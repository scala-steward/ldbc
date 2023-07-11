/** This file is part of the ldbc. For the full copyright and license information, please view the LICENSE file that was
  * distributed with this source code.
  */

package ldbc.generator

import java.io.File
import java.nio.file.Files
import java.nio.charset.Charset

import scala.io.Codec

import ldbc.generator.formatter.Naming
import ldbc.generator.parser.Parser
import ldbc.generator.model.*

private[ldbc] object LdbcGenerator:

  def generate(
    sqlFilePaths:       Array[File],
    classNameFormat:    String,
    propertyNameFormat: String,
    sourceManaged:      File,
    baseDirectory:      File
  ): Array[File] =
    sqlFilePaths.flatMap(file =>

      val content = new String(
        Files.readAllBytes(file.toPath),
        Charset.defaultCharset()
      )

      val classNameFormatter    = Naming.fromString(classNameFormat)
      val propertyNameFormatter = Naming.fromString(propertyNameFormat)

      Parser.parse(content) match
        case Parser.Success(parsed, _) =>
          parsed.flatMap { (name, statements) =>
            statements.map(statement =>
              val className = classNameFormatter.format(statement.tableName)
              val properties = statement.columnDefinitions.map(column =>
                propertyGenerator(className, column, propertyNameFormatter, classNameFormatter)
              )

              val objects =
                statement.columnDefinitions.map(column => enumGenerator(column, classNameFormatter)).filter(_.nonEmpty)

              val outputFile = new File(sourceManaged, s"$className.scala")

              if !outputFile.exists() then
                outputFile.getParentFile.mkdirs()
                outputFile.createNewFile()

              val keyDefinitions = statement.keyDefinitions.map(key =>
                s".keySet(table => ${ key.toCode("table", classNameFormatter, propertyNameFormatter) })"
              )

              val packageName = if name.nonEmpty then s"ldbc.generated.$name" else "ldbc.generated"

              val columns =
                statement.columnDefinitions.map((column: ColumnDefinition) =>
                  column.dataType.scalaType match
                    case ScalaType.Enum(types) => column.copy(name = classNameFormatter.format(column.name))
                    case _                     => column
                )

              val scalaSource =
                s"""
                 |package $packageName
                 |
                 |import ldbc.core.*
                 |
                 |case class $className(
                 |  ${ properties.mkString(",\n  ") }
                 |)
                 |
                 |object $className:
                 |
                 |  ${ objects.mkString("\n  ") }
                 |  val table: TABLE[$className] = Table[$className]("${ statement.tableName }")(
                 |    ${ columns.map(_.toCode).mkString(",\n    ") }
                 |  )
                 |  ${ keyDefinitions.mkString("\n  ") }
                 |""".stripMargin

              Files.write(outputFile.toPath, scalaSource.getBytes(summon[Codec].name))
              outputFile
            )
          }
        case Parser.NoSuccess(errorMessage, _) =>
          println(s"NoSuccess: $errorMessage")
          List.empty
        case Parser.Failure(errorMessage, _) =>
          println(s"Failure: $errorMessage")
          List.empty
        case Parser.Error(errorMessage, _) =>
          println(s"Error: $errorMessage")
          List.empty
    )

  private def propertyGenerator(
    className:             String,
    column:                ColumnDefinition,
    propertyNameFormatter: Naming,
    classNameFormatter:    Naming
  ): String =

    val name = propertyNameFormatter.format(column.name)

    (column.attributes.forall(_.constraint), column.dataType.scalaType) match
      case (true, _: ScalaType.Enum)  => s"$name: Option[$className.${ classNameFormatter.format(column.name) }]"
      case (false, _: ScalaType.Enum) => s"$name: $className.${ classNameFormatter.format(column.name) }"
      case (true, _)                  => s"$name: Option[${ column.dataType.scalaType.code }]"
      case (false, _)                 => s"$name: ${ column.dataType.scalaType.code }"

  private def enumGenerator(column: ColumnDefinition, formatter: Naming): String =
    column.dataType.scalaType match
      case ScalaType.Enum(types) =>
        val enumName = formatter.format(column.name)
        s"""enum $enumName extends ldbc.core.model.Enum:
           |    case ${ types.mkString(", ") }
           |  object $enumName extends ldbc.core.model.EnumDataType[$enumName]
           |""".stripMargin
      case _ => ""
