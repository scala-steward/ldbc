/** This file is part of the ldbc. For the full copyright and license information, please view the LICENSE file that was
  * distributed with this source code.
  */

package ldbc.query.builder.statement

import ldbc.sql.ParameterBinder
import ldbc.query.builder.TableQuery

/** A model for constructing GROUP BY statements in MySQL.
  *
  * @param tableQuery
  *   Trait for generating SQL table information.
  * @param statement
  *   SQL statement string
  * @param columns
  *   Union-type column list
  * @param params
  *   A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
  *   only.
  * @tparam F
  *   The effect type
  * @tparam P
  *   Base trait for all products
  * @tparam T
  *   Union type of column
  */
private[ldbc] case class GroupBy[F[_], P <: Product, T](
  tableQuery: TableQuery[F, P],
  statement:  String,
  columns:    T,
  params:     Seq[ParameterBinder[F]]
) extends Query[F, T],
          OrderByProvider[F, P, T],
          LimitProvider[F, T]:

  def having[A](func: T => ExpressionSyntax[F]): Having[F, P, T] =
    val expressionSyntax = func(columns)
    Having(
      tableQuery = tableQuery,
      statement  = statement ++ s" HAVING ${ expressionSyntax.statement }",
      columns    = columns,
      params     = params ++ expressionSyntax.parameter
    )
