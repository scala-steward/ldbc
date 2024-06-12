/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.query.builder.statement

import ldbc.core.Column
import ldbc.dsl.Parameter
import ldbc.query.builder.TableQuery
import ldbc.query.builder.interpreter.Tuples

/**
 * Trait for building Statements to be added.
 *
 * @tparam P
 *   Base trait for all products
 */
private[ldbc] trait Insert[P <: Product] extends Command:
  self =>

  /** A model for generating queries from Table information. */
  def tableQuery: TableQuery[P]

  /** Methods for constructing INSERT ... ON DUPLICATE KEY UPDATE statements. */
  def onDuplicateKeyUpdate[T](func: TableQuery[P] => T)(using
    Tuples.IsColumnQuery[T] =:= true
  ): DuplicateKeyUpdateInsert =
    val duplicateKeys = func(self.tableQuery) match
      case tuple: Tuple => tuple.toList.map(column => s"$column = new_${ tableQuery.table._name }.$column")
      case column       => List(s"$column = new_${ tableQuery.table._name }.$column")
    new DuplicateKeyUpdateInsert:
      override def params: Seq[Parameter.DynamicBinder] = self.params

      override def statement: String =
        s"${ self.statement } AS new_${ tableQuery.table._name } ON DUPLICATE KEY UPDATE ${ duplicateKeys.mkString(", ") }"

/**
 * Insert trait that provides a method to update in case of duplicate keys.
 */
trait DuplicateKeyUpdateInsert extends Command

/**
 * A model for constructing INSERT statements that insert single values in MySQL.
 *
 * @param tableQuery
 *   Trait for generating SQL table information.
 * @param tuple
 *   Tuple type value of the property with type parameter P.
 * @param params
 *   A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
 *   only.
 * @tparam P
 *   Base trait for all products
 * @tparam T
 *   Tuple type of the property with type parameter P
 */
case class SingleInsert[P <: Product, T <: Tuple](
  tableQuery: TableQuery[P],
  tuple:      T,
  params:     Seq[Parameter.DynamicBinder]
) extends Insert[P]:

  override val statement: String =
    s"INSERT INTO ${ tableQuery.table._name } (${ tableQuery.table.all
        .mkString(", ") }) VALUES(${ tuple.toArray.map(_ => "?").mkString(", ") })"

/**
 * A model for constructing INSERT statements that insert multiple values in MySQL.
 *
 * @param tableQuery
 *   Trait for generating SQL table information.
 * @param tuples
 *   Tuple type value of the property with type parameter P.
 * @param params
 *   A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
 *   only.
 * @tparam P
 *   Base trait for all products
 * @tparam T
 *   Tuple type of the property with type parameter P
 */
case class MultiInsert[P <: Product, T <: Tuple](
  tableQuery: TableQuery[P],
  tuples:     List[T],
  params:     Seq[Parameter.DynamicBinder]
) extends Insert[P]:

  private val values = tuples.map(tuple => s"(${ tuple.toArray.map(_ => "?").mkString(", ") })")

  override val statement: String =
    s"INSERT INTO ${ tableQuery.table._name } (${ tableQuery.table.all.mkString(", ") }) VALUES${ values.mkString(", ") }"

/**
 * A model for constructing INSERT statements that insert values into specified columns in MySQL.
 *
 * @param query
 *   Trait for generating SQL table information.
 * @param columns
 *   List of columns into which values are to be inserted.
 * @param parameter
 *   Parameters of the value to be inserted
 * @tparam P
 *   Base trait for all products
 * @tparam T
 *   Tuple type of the property with type parameter P
 */
case class SelectInsert[P <: Product, T](
  query:     TableQuery[P],
  columns:   T,
  parameter: Parameter.MapToTuple[Column.Extract[T]]
):

  private val columnStatement = columns match
    case v: Tuple => v.toArray.distinct.mkString(", ")
    case v        => v

  private val insertStatement: String =
    s"INSERT INTO ${ query.table._name } ($columnStatement)"

  def values(tuple: Column.Extract[T]): Insert[P] =
    new Insert[P]:
      override def tableQuery: TableQuery[P] = query
      override def statement: String = s"$insertStatement VALUES(${ tuple.toArray.map(_ => "?").mkString(", ") })"
      override def params: Seq[Parameter.DynamicBinder] =
        tuple.zip(parameter).toArray.toSeq.map {
          case (value: Any, parameter: Any) =>
            Parameter.DynamicBinder[Any](value)(using parameter.asInstanceOf[Parameter[Any]])
        }

  def values(tuples: List[Column.Extract[T]]): Insert[P] =
    val values = tuples.map(tuple => s"(${ tuple.toArray.map(_ => "?").mkString(", ") })")
    new Insert[P]:
      override def tableQuery: TableQuery[P] = query
      override def statement:  String        = s"$insertStatement VALUES${ values.mkString(", ") }"
      override def params: Seq[Parameter.DynamicBinder] =
        tuples.flatMap(_.zip(parameter).toArray.map {
          case (value: Any, parameter: Any) =>
            Parameter.DynamicBinder[Any](value)(using parameter.asInstanceOf[Parameter[Any]])
        })

/**
 * A model for constructing ON DUPLICATE KEY UPDATE statements that insert multiple values in MySQL.
 *
 * @param tableQuery
 *   Trait for generating SQL table information.
 * @param tuples
 *   Tuple type value of the property with type parameter P.
 * @param params
 *   A list of Traits that generate values from Parameter, allowing PreparedStatement to be set to a value by index
 *   only.
 * @tparam P
 *   Base trait for all products
 * @tparam T
 *   Tuple type of the property with type parameter P
 */
case class DuplicateKeyUpdate[P <: Product, T <: Tuple](
  tableQuery: TableQuery[P],
  tuples:     List[T],
  params:     Seq[Parameter.DynamicBinder]
) extends DuplicateKeyUpdateInsert:

  private val values = tuples.map(tuple => s"(${ tuple.toArray.map(_ => "?").mkString(", ") })")

  private val duplicateKeys = tableQuery.table.all.map(column => s"$column = new_${ tableQuery.table._name }.$column")

  override val statement: String =
    s"INSERT INTO ${ tableQuery.table._name } (${ tableQuery.table.all.mkString(", ") }) VALUES${ values.mkString(
        ", "
      ) } AS new_${ tableQuery.table._name } ON DUPLICATE KEY UPDATE ${ duplicateKeys.mkString(", ") }"
