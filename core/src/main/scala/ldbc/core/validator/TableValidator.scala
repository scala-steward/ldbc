/** This file is part of the ldbc. For the full copyright and license information, please view the LICENSE file that was
  * distributed with this source code.
  */

package ldbc.core.validator

import ldbc.core.*
import ldbc.core.attribute.*

/** Trait for validation of table definitions.
  */
private[ldbc] trait TableValidator:

  /** Trait for generating SQL table information. */
  def table: Table[?]

  protected val autoInc = table.all.filter(_.attributes.contains(AutoInc()))

  protected val primaryKey = table.all.filter(_.attributes.exists(_.isInstanceOf[PrimaryKey]))

  protected val keyPart = table.keyDefinitions.flatMap {
    case key: PrimaryKey with Index => key.keyPart.toList
    case key: UniqueKey with Index  => key.keyPart.toList
    case _                          => List.empty
  }

  protected val constraints = table.keyDefinitions.flatMap {
    case key: Constraint => Some(key)
    case _               => None
  }

  require(
    table.all.distinctBy(_.label).length == table.all.length,
    "Columns with the same name cannot be defined in a single table."
  )

  require(
    autoInc.length <= 1,
    "AUTO_INCREMENT can only be set on one of the table columns."
  )

  require(
    primaryKey.length <= 1
      && table.keyDefinitions.count(_.label == "PRIMARY KEY") <= 1
      && primaryKey.length + table.keyDefinitions.count(_.label == "PRIMARY KEY") <= 1,
    "PRIMARY KEY can only be set on one of the table columns."
  )

  require(
    !(autoInc.nonEmpty &&
      (autoInc.count(column =>
        column.attributes.exists(_.isInstanceOf[PrimaryKey]) || column.attributes.exists(_.isInstanceOf[UniqueKey])
      ) >= 1 || keyPart.count(key =>
        autoInc
          .map(_.label)
          .contains(key.label)
      ) == 0)),
    "The columns with AUTO_INCREMENT must have a Primary Key or Unique Key."
  )

  if constraints.nonEmpty then
    require(
      constraints.exists(_.key match
        case key: ForeignKey => key.colName.map(_.dataType) == key.reference.keyPart.map(_.dataType)
        case _               => false
      ),
      s"""
         |The type of the column set in FOREIGN KEY does not match.
         |
         |`${ table._name }` Table
         |
         |============================================================
         |${ initForeignKeyErrorMsg(constraints) }
         |============================================================
         |""".stripMargin
    )

    require(
      constraints.exists(_.key match
        case key: ForeignKey =>
          key.reference.keyPart.toList.flatMap(_.attributes).exists(_.isInstanceOf[PrimaryKey]) ||
          key.reference.table.keyDefinitions.exists(_ match
            case v: PrimaryKey with Index => v.keyPart.exists(c => key.reference.keyPart.exists(_ == c))
            case _                        => false
          )
        case _ => false
      ),
      "The column referenced by FOREIGN KEY must be a PRIMARY KEY."
    )

  private def initForeignKeyErrorMsg(constraints: Seq[Constraint]): String =
    constraints
      .flatMap(_.key match
        case key: ForeignKey =>
          for
            (column, index)             <- key.colName.zipWithIndex.toList
            (refColumn, refColumnIndex) <- key.reference.keyPart.zipWithIndex.toList
          yield
            if index == refColumnIndex then s"""
             |(${ column.dataType == refColumn.dataType }) `${ column.label }` ${ column.dataType.typeName } =:= `${ refColumn.label }` ${ refColumn.dataType.typeName }
             |""".stripMargin
            else ""
        case _ => ""
      )
      .mkString("\n")
