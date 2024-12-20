/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.query.builder

import scala.language.dynamics
import scala.deriving.Mirror
import scala.quoted.*

import ldbc.dsl.codec.Decoder
import ldbc.statement.{ AbstractTable, Column }
import ldbc.query.builder.formatter.Naming
import ldbc.query.builder.interpreter.*
import ldbc.query.builder.Column as ColumnAnnotation

trait SharedTable extends Dynamic:

  type Self

  def columns: List[Column[?]]

trait Table[P] extends SharedTable, AbstractTable[P]:

  override type Self = Table[P]

  /**
   * A method to get a specific column defined in the table.
   *
   * @param tag
   *   A type with a single instance. Here, Column is passed.
   * @param mirror
   *   product isomorphism map
   * @param index
   *   Position of the specified type in tuple X
   * @tparam Tag
   *   Type with a single instance
   */
  transparent inline def selectDynamic[Tag <: Singleton](
    tag: Tag
  )(using
    mirror: Mirror.Of[P],
    index:  ValueOf[Tuples.IndexOf[mirror.MirroredElemLabels, Tag]]
  ): Column[Tuple.Elem[mirror.MirroredElemTypes, Tuples.IndexOf[mirror.MirroredElemLabels, Tag]]] =
    columns
      .apply(index.value)
      .asInstanceOf[Column[Tuple.Elem[mirror.MirroredElemTypes, Tuples.IndexOf[mirror.MirroredElemLabels, Tag]]]]

object Table:

  private[ldbc] case class Impl[P <: Product](
    $name:   String,
    columns: List[Column[?]]
  )(using mirror: Mirror.ProductOf[P])
    extends Table[P]:

    override def statement: String = $name

    override def * : Column[P] =
      val decoder: Decoder[P] = new Decoder[P]((resultSet, prefix) =>
        mirror.fromTuple(
          Tuple
            .fromArray(columns.map(_.decoder.decode(resultSet, prefix)).toArray)
            .asInstanceOf[mirror.MirroredElemTypes]
        )
      )
      val alias = columns.flatMap(_.alias).mkString(", ")
      Column.Impl[P](
        columns.map(_.name).mkString(", "),
        if alias.isEmpty then None else Some(alias),
        decoder,
        Some(columns.length),
        Some(columns.map(column => s"${ column.name } = ?").mkString(", "))
      )

  inline def derived[P <: Product]:               Table[P] = ${ derivedImpl[P] }
  inline def derived[P <: Product](name: String): Table[P] = ${ derivedWithNameImpl[P]('name) }

  private def derivedImpl[P <: Product](using
    quotes: Quotes,
    tpe:    Type[P]
  ): Expr[Table[P]] =

    import quotes.reflect.*

    val symbol = TypeRepr.of[P].typeSymbol

    val annot = TypeRepr.of[ColumnAnnotation].typeSymbol

    val naming = Expr.summon[Naming] match
      case Some(naming) => naming
      case None         => '{ Naming.SNAKE }

    val mirror = Expr.summon[Mirror.ProductOf[P]].getOrElse {
      report.errorAndAbort(s"Mirror for type $tpe not found")
    }

    val labels = symbol.primaryConstructor.paramSymss.flatten
      .collect {
        case sym if sym.hasAnnotation(annot) =>
          val annotExpr = sym.getAnnotation(annot).get.asExprOf[ColumnAnnotation]
          '{ $annotExpr.name }
        case sym =>
          val name = Expr(sym.name)
          '{ $naming.format($name) }
      }

    val decodes = Expr.ofSeq(
      symbol.caseFields
        .map { field =>
          field.tree match
            case ValDef(name, tpt, _) =>
              tpt.tpe.asType match
                case '[tpe] =>
                  val decoder = Expr.summon[Decoder.Elem[tpe]].getOrElse {
                    report.errorAndAbort(s"Decoder for type $tpe not found")
                  }
                  decoder.asExprOf[Decoder.Elem[?]]
                case _ =>
                  report.errorAndAbort(s"Type $tpt is not a type")
        }
    )

    val name = Expr(symbol.name)

    val columns = '{
      ${ Expr.ofSeq(labels) }
        .zip($decodes)
        .map {
          case (label: String, decoder: Decoder.Elem[t]) => Column[t](label, $naming.format($name))(using decoder)
        }
        .toList
    }

    '{
      Impl[P](
        $naming.format($name),
        $columns
      )(using $mirror)
    }

  private def derivedWithNameImpl[P <: Product](name: Expr[String])(using
    quotes: Quotes,
    tpe:    Type[P]
  ): Expr[Table[P]] =

    import quotes.reflect.*

    val symbol = TypeRepr.of[P].typeSymbol

    val annot = TypeRepr.of[ColumnAnnotation].typeSymbol

    val naming = Expr.summon[Naming] match
      case Some(naming) => naming
      case None         => '{ Naming.SNAKE }

    val mirror = Expr.summon[Mirror.ProductOf[P]].getOrElse {
      report.errorAndAbort(s"Mirror for type $tpe not found")
    }

    val labels = symbol.primaryConstructor.paramSymss.flatten
      .collect {
        case sym if sym.hasAnnotation(annot) =>
          val annotExpr = sym.getAnnotation(annot).get.asExprOf[ColumnAnnotation]
          '{ $annotExpr.name }
        case sym =>
          val name = Expr(sym.name)
          '{ $naming.format($name) }
      }

    val decodes = Expr.ofSeq(
      symbol.caseFields
        .map { field =>
          field.tree match
            case ValDef(name, tpt, _) =>
              tpt.tpe.asType match
                case '[tpe] =>
                  val decoder = Expr.summon[Decoder.Elem[tpe]].getOrElse {
                    report.errorAndAbort(s"Decoder for type $tpe not found")
                  }
                  decoder.asExprOf[Decoder.Elem[?]]
                case _ =>
                  report.errorAndAbort(s"Type $tpt is not a type")
        }
    )

    val columns = '{
      ${ Expr.ofSeq(labels) }
        .zip($decodes)
        .map {
          case (label: String, decoder: Decoder.Elem[t]) => Column[t](label, $name)(using decoder)
        }
        .toList
    }

    '{
      Impl[P](
        $name,
        $columns
      )(using $mirror)
    }

  trait Opt[P] extends SharedTable, AbstractTable.Opt[P]:

    override type Self = Opt[P]

    transparent inline def selectDynamic[Tag <: Singleton](
      tag: Tag
    )(using
      mirror: Mirror.Of[P],
      index:  ValueOf[Tuples.IndexOf[mirror.MirroredElemLabels, Tag]]
    ): Column[
      Option[ExtractOption[Tuple.Elem[mirror.MirroredElemTypes, Tuples.IndexOf[mirror.MirroredElemLabels, Tag]]]]
    ] =
      columns
        .apply(index.value)
        .asInstanceOf[Column[
          ExtractOption[Tuple.Elem[mirror.MirroredElemTypes, Tuples.IndexOf[mirror.MirroredElemLabels, Tag]]]
        ]]
        .opt

  object Opt:

    private[ldbc] case class Impl[P](
      $name:   String,
      columns: List[Column[?]],
      *      : Column[Option[P]]
    ) extends Opt[P]:

      override def statement: String = $name
