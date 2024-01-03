/** This file is part of the ldbc. For the full copyright and license information, please view the LICENSE file that was
  * distributed with this source code.
  */

package ldbc.dsl.internal

import java.sql.{ Blob, Clob, NClob, SQLWarning, SQLXML, Struct }
import java.util.Properties
import java.util.concurrent.Executor

import scala.jdk.CollectionConverters.*

import cats.Applicative
import cats.implicits.*
import cats.data.Kleisli

import cats.effect.Sync

import ldbc.sql.{ Statement, PreparedStatement, ResultSet, Connection }
import ldbc.dsl.PreparedStatement

trait ConnectionSyntax extends StatementSyntax:

  implicit class ConnectionF(connectionObject: Connection.type):

    def apply[F[_]: Sync](connection: java.sql.Connection): Connection[F] = new Connection[F]:

      override def createStatement(): F[Statement[F]] =
        Sync[F].blocking(connection.createStatement()).map(Statement[F])

      override def prepareStatement(sql: String): F[PreparedStatement[F]] =
        Sync[F].blocking(connection.prepareStatement(sql)).map(PreparedStatement[F])

      override def nativeSQL(sql: String): F[String] = Sync[F].blocking(connection.nativeSQL(sql))

      override def setAutoCommit(autoCommit: Boolean): F[Unit] = Sync[F].blocking(connection.setAutoCommit(autoCommit))

      override def getAutoCommit(): F[Boolean] = Sync[F].blocking(connection.getAutoCommit)

      override def commit(): F[Unit] = Sync[F].blocking(connection.commit())

      override def rollback(): F[Unit] = Sync[F].blocking(connection.rollback())

      override def close(): F[Unit] = Sync[F].blocking(connection.close())

      override def isClosed(): F[Boolean] = Sync[F].blocking(connection.isClosed)

      override def setReadOnly(readOnly: Boolean): F[Unit] = Sync[F].blocking(connection.setReadOnly(readOnly))

      override def isReadOnly(): F[Boolean] = Sync[F].blocking(connection.isReadOnly)

      override def setCatalog(catalog: String): F[Unit] = Sync[F].blocking(connection.setCatalog(catalog))

      override def getCatalog(): F[String] = Sync[F].blocking(connection.getCatalog)

      override def setTransactionIsolation(level: Connection.TransactionIsolation): F[Unit] =
        Sync[F].blocking(connection.setTransactionIsolation(level.code))

      override def getTransactionIsolation(): F[Int] = Sync[F].blocking(connection.getTransactionIsolation)

      override def getWarnings(): F[SQLWarning] = Sync[F].blocking(connection.getWarnings)

      override def clearWarnings(): F[Unit] = Sync[F].blocking(connection.clearWarnings())

      override def createStatement(
        resultSetType:        ResultSet.Type,
        resultSetConcurrency: ResultSet.Concur
      ): F[Statement[F]] =
        Sync[F].blocking(connection.createStatement(resultSetType.code, resultSetConcurrency.code)).map(Statement[F])

      override def prepareStatement(
        sql:                  String,
        resultSetType:        ResultSet.Type,
        resultSetConcurrency: ResultSet.Concur
      ): F[PreparedStatement[F]] =
        Sync[F]
          .blocking(connection.prepareStatement(sql, resultSetType.code, resultSetConcurrency.code))
          .map(PreparedStatement[F])

      override def getTypeMap(): F[Map[String, Class[_]]] = Sync[F].blocking(connection.getTypeMap.asScala.toMap)

      override def setTypeMap(map: Map[String, Class[_]]): F[Unit] = Sync[F].blocking(connection.setTypeMap(map.asJava))

      override def setHoldability(holdability: Int): F[Unit] = Sync[F].blocking(connection.setHoldability(holdability))

      override def getHoldability(): F[Int] = Sync[F].blocking(connection.getHoldability)

      override def createStatement(
        resultSetType:        ResultSet.Type,
        resultSetConcurrency: ResultSet.Concur,
        resultSetHoldability: ResultSet.Holdability
      ): F[Statement[F]] = Sync[F]
        .blocking(connection.createStatement(resultSetType.code, resultSetConcurrency.code, resultSetHoldability.code))
        .map(Statement[F])

      override def prepareStatement(
        sql:                  String,
        resultSetType:        ResultSet.Type,
        resultSetConcurrency: ResultSet.Concur,
        resultSetHoldability: ResultSet.Holdability
      ): F[PreparedStatement[F]] = Sync[F]
        .blocking(
          connection.prepareStatement(sql, resultSetType.code, resultSetConcurrency.code, resultSetHoldability.code)
        )
        .map(PreparedStatement[F])

      override def prepareStatement(sql: String, autoGeneratedKeys: Statement.Generated): F[PreparedStatement[F]] =
        Sync[F].blocking(connection.prepareStatement(sql, autoGeneratedKeys.code)).map(PreparedStatement[F])

      override def prepareStatement(sql: String, columnIndexes: Array[Int]): F[PreparedStatement[F]] =
        Sync[F].blocking(connection.prepareStatement(sql, columnIndexes)).map(PreparedStatement[F])

      override def prepareStatement(sql: String, columnNames: Array[String]): F[PreparedStatement[F]] =
        Sync[F].blocking(connection.prepareStatement(sql, columnNames)).map(PreparedStatement[F])

      override def createClob(): F[Clob] = Sync[F].blocking(connection.createClob())

      override def createBlob(): F[Blob] = Sync[F].blocking(connection.createBlob())

      override def createNClob(): F[NClob] = Sync[F].blocking(connection.createNClob())

      override def createSQLXML(): F[SQLXML] = Sync[F].blocking(connection.createSQLXML())

      override def isValid(timeout: Int): F[Boolean] = Sync[F].blocking(connection.isValid(timeout))

      override def setClientInfo(name: String, value: String): F[Unit] =
        Sync[F].blocking(connection.setClientInfo(name, value))

      override def setClientInfo(properties: Properties): F[Unit] =
        Sync[F].blocking(connection.setClientInfo(properties))

      override def getClientInfo(name: String): F[String] = Sync[F].blocking(connection.getClientInfo(name))

      override def getClientInfo(): F[Properties] = Sync[F].blocking(connection.getClientInfo())

      override def createArrayOf(typeName: String, elements: Array[Object]): F[java.sql.Array] =
        Sync[F].blocking(connection.createArrayOf(typeName, elements))

      override def createStruct(typeName: String, attributes: Array[Object]): F[Struct] =
        Sync[F].blocking(connection.createStruct(typeName, attributes))

      override def setSchema(schema: String): F[Unit] = Sync[F].blocking(connection.setSchema(schema))

      override def getSchema(): F[String] = Sync[F].blocking(connection.getSchema)

      override def abort(executor: Executor): F[Unit] = Sync[F].blocking(connection.abort(executor))

      override def setNetworkTimeout(executor: Executor, milliseconds: Int): F[Unit] =
        Sync[F].blocking(connection.setNetworkTimeout(executor, milliseconds))

      override def getNetworkTimeout(): F[Int] = Sync[F].blocking(connection.getNetworkTimeout)

    def pure[F[_]: Applicative, T](value: T): Kleisli[F, Connection[F], T] =
      Kleisli.pure[F, Connection[F], T](value)