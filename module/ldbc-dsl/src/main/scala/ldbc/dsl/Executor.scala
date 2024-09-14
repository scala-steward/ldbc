/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.dsl

import cats.{ Functor, Monad, MonadError }
import cats.syntax.all.*

import cats.effect.*
import cats.effect.kernel.Resource.ExitCase

import ldbc.sql.*

import ldbc.dsl.logging.*

/**
 * A trait that represents the execution of a query.
 *
 * @tparam F
 *   The effect type
 * @tparam T
 *   The result type of the query
 */
trait Executor[F[_]: Temporal, T]:

  private[ldbc] def execute(connection: Connection[F])(using logHandler: LogHandler[F]): F[T]

  /**
   * Functions for managing the processing of connections in a read-only manner.
   */
  def readOnly(connection: Connection[F])(using LogHandler[F]): F[T] =
    connection.setReadOnly(true) *> execute(connection) <* connection.setReadOnly(false)

  /**
   * Functions to manage the processing of connections for writing.
   */
  def commit(connection: Connection[F])(using LogHandler[F]): F[T] =
    connection.setReadOnly(false) *> connection.setAutoCommit(true) *> execute(connection)

  /**
   * Functions to manage the processing of connections, always rolling back.
   */
  def rollback(connection: Connection[F])(using LogHandler[F]): F[T] =
    connection.setReadOnly(false) *> connection.setAutoCommit(false) *> execute(connection) <* connection
      .rollback() <* connection.setAutoCommit(true)

  /**
   * Functions to manage the processing of connections in a transaction.
   */
  def transaction(connection: Connection[F])(using LogHandler[F]): F[T] =
    val acquire = connection.setReadOnly(false) *> connection.setAutoCommit(false) *> Temporal[F].pure(connection)

    val release = (connection: Connection[F], exitCase: ExitCase) =>
      (exitCase match
        case ExitCase.Errored(_) | ExitCase.Canceled => connection.rollback()
        case _                                       => connection.commit()
      )
        *> connection.setAutoCommit(true)

    Resource
      .makeCase(acquire)(release)
      .use(execute)

object Executor:

  private[ldbc] case class Impl[F[_]: Temporal, T](
    statement: String,
    params:    List[Parameter],
    run:       Connection[F] => F[T]
  ) extends Executor[F, T]:

    private[ldbc] def execute(connection: Connection[F])(using logHandler: LogHandler[F]): F[T] =
      run(connection)
        .onError(ex => logHandler.run(LogEvent.ProcessingFailure(statement, params.map(_.value), ex)))
        <* logHandler.run(LogEvent.Success(statement, params.map(_.value)))

  def pure[F[_]: Temporal, T](value: T): Executor[F, T] =
    new Executor[F, T]:
      override private[ldbc] def execute(connection: Connection[F])(using LogHandler[F]): F[T] = Monad[F].pure(value)
      override def readOnly(connection:              Connection[F])(using LogHandler[F]): F[T] = Monad[F].pure(value)
      override def commit(connection:                Connection[F])(using LogHandler[F]): F[T] = Monad[F].pure(value)
      override def rollback(connection:              Connection[F])(using LogHandler[F]): F[T] = Monad[F].pure(value)
      override def transaction(connection:           Connection[F])(using LogHandler[F]): F[T] = Monad[F].pure(value)

  def raiseError[F[_]: Temporal, A](e: Throwable): Executor[F, A] =
    new Executor[F, A]:
      override private[ldbc] def execute(connection: Connection[F])(using LogHandler[F]): F[A] =
        MonadError[F, Throwable].raiseError(e)

  given [F[_]: Temporal]: Functor[[T] =>> Executor[F, T]] with
    override def map[A, B](fa: Executor[F, A])(f: A => B): Executor[F, B] =
      new Executor[F, B]:
        override private[ldbc] def execute(connection: Connection[F])(using LogHandler[F]): F[B] =
          fa.execute(connection).map(f)

  given [F[_]: Temporal]: MonadError[[T] =>> Executor[F, T], Throwable] with
    override def pure[A](x: A): Executor[F, A] = Executor.pure(x)

    override def flatMap[A, B](fa: Executor[F, A])(f: A => Executor[F, B]): Executor[F, B] =
      new Executor[F, B]:
        override private[ldbc] def execute(connection: Connection[F])(using LogHandler[F]): F[B] =
          fa.execute(connection).flatMap(a => f(a).execute(connection))

    override def tailRecM[A, B](a: A)(f: A => Executor[F, Either[A, B]]): Executor[F, B] =
      new Executor[F, B]:
        override private[ldbc] def execute(connection: Connection[F])(using logHandler: LogHandler[F]): F[B] =
          MonadError[F, Throwable].tailRecM(a)(a => f(a).execute(connection))

    override def ap[A, B](ff: Executor[F, A => B])(fa: Executor[F, A]): Executor[F, B] =
      new Executor[F, B]:
        override private[ldbc] def execute(connection: Connection[F])(using logHandler: LogHandler[F]): F[B] =
          (ff.execute(connection), fa.execute(connection)).mapN(_(_))

    override def raiseError[A](e: Throwable): Executor[F, A] =
      Executor.raiseError(e)

    override def handleErrorWith[A](fa: Executor[F, A])(f: Throwable => Executor[F, A]): Executor[F, A] =
      new Executor[F, A]:
        override private[ldbc] def execute(connection: Connection[F])(using LogHandler[F]): F[A] =
          fa.execute(connection).handleErrorWith(e => f(e).execute(connection))
