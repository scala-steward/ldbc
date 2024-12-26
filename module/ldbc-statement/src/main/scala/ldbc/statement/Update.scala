/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.statement

import scala.annotation.targetName

import ldbc.dsl.{ Parameter, SQL }
import ldbc.dsl.codec.Encoder

/** 
 * Trait for building Statements to be updated.
 *
 * @tparam A
 *  The type of Table. in the case of Join, it is a Tuple of type Table.
 */
sealed trait Update[A] extends Command:

  /** A model for generating queries from Table information. */
  def table: A

  /** 
   * Methods for setting the value of a column in a table.
   * 
   * {{{
   *   TableQuery[City]
   *     .update(...)
   *     .set(_.population, 13929286)
   * }}}
   * 
   * @param column
   *   A column in the table
   * @param value
   *   The value to be set
   */
  def set[B](column: A => Column[B], value: B)(using Encoder[B]): Update[A]

  /** 
   * Methods for setting the value of a column in a table.
   * If the value passed to this set is false, the update process is skipped.
   * 
   * {{{
   *   TableQuery[City]
   *     .update(...)
   *     .set(_.population, 13929286, true)
   * }}}
   * 
   * @param column
   *   A column in the table
   * @param value
   *   The value to be set
   * @param bool
   *   A boolean value that determines whether to update
   */
  def set[B](column: A => Column[B], value: B, bool: Boolean)(using Encoder[B]): Update[A]

  /** 
   * A method for setting the WHERE condition in a Update statement.
   * 
   * {{{
   *   TableQuery[City]
   *     .update(...)
   *     .set(_.population, 13929286)
   *     .where(_.name === "Tokyo")
   * }}}
   * 
   * @param func
   *   A function that takes a column and returns an expression.
   */
  def where(func: A => Expression): Where.C[A]

object Update:

  private[ldbc] case class Impl[A](
    table:     A,
    statement: String,
    params:    List[Parameter.Dynamic]
  ) extends Update[A]:

    @targetName("combine")
    override def ++(sql: SQL): SQL = this.copy(statement = statement ++ sql.statement, params = params ++ sql.params)

    override def set[B](column: A => Column[B], value: B)(using Encoder[B]): Update[A] =
      this.copy(
        statement = statement ++ s", ${ column(table).updateStatement }",
        params    = params :+ Parameter.Dynamic(value)
      )

    override def set[B](column: A => Column[B], value: B, bool: Boolean)(using Encoder[B]): Update[A] =
      if bool then set(column, value) else this

    override def where(func: A => Expression): Where.C[A] =
      val expression = func(table)
      Where.C[A](
        table     = table,
        statement = statement ++ s" WHERE ${ expression.statement }",
        params    = params ++ expression.parameter
      )