/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.query.builder.statement

import ldbc.dsl.Parameter

/**
 * Trait for building Statements to be added, updated, and deleted.
 */
private[ldbc] trait Command:

  /**
   * SQL statement string
   */
  def statement: String

  /**
   * A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
   * only.
   */
  def params: Seq[Parameter.DynamicBinder]

object Command:

  /**
   * A model for constructing WHERE statements in MySQL.
   *
   * @param _statement
   *   SQL statement string
   * @param expressionSyntax
   *   Trait for the syntax of expressions available in MySQL.
   * @param params
   *   A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
   *   only.
   */
  case class Where(
    _statement:       String,
    expressionSyntax: ExpressionSyntax,
    params:           Seq[Parameter.DynamicBinder]
  ) extends Command,
            LimitProvider:

    override def statement: String = _statement ++ s" WHERE ${ expressionSyntax.statement }"

  /**
   * @param _statement
   *   SQL statement string
   * @param params
   *   A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
   *   only.
   */
  case class Limit(
    _statement: String,
    params:     Seq[Parameter.DynamicBinder]
  ) extends Command:

    override def statement: String = _statement ++ " LIMIT ?"

  /**
   * Transparent Trait to provide limit method.
   */
  private[ldbc] transparent trait LimitProvider:
    self: Command =>

    /**
     * A method for setting the LIMIT condition in a statement.
     *
     * @param length
     *   Upper limit to be updated
     */
    def limit(length: Long): Parameter[Long] ?=> Limit =
      Limit(
        _statement = statement,
        params     = params :+ Parameter.DynamicBinder(length)
      )
