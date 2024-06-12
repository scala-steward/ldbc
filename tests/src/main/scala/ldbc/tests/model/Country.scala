/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.tests.model

import cats.effect.IO

import ldbc.core.*
import ldbc.core.model.*
import ldbc.sql.PreparedStatement
import ldbc.dsl.*

case class Country(
  code:           String,
  name:           String,
  continent:      Country.Continent,
  region:         String,
  surfaceArea:    BigDecimal,
  indepYear:      Option[Short],
  population:     Int,
  lifeExpectancy: Option[BigDecimal],
  gnp:            Option[BigDecimal],
  gnpOld:         Option[BigDecimal],
  localName:      String,
  governmentForm: String,
  headOfState:    Option[String],
  capital:        Option[Int],
  code2:          String
)

object Country:

  enum Continent(val value: String) extends Enum:
    case Asia          extends Continent("Asia")
    case Europe        extends Continent("Europe")
    case North_America extends Continent("North America")
    case Africa        extends Continent("Africa")
    case Oceania       extends Continent("Oceania")
    case Antarctica    extends Continent("Antarctica")
    case South_America extends Continent("South America")

    override def toString: String = value

  object Continent extends EnumDataType[Continent]

  given Parameter[Continent] with
    override def bind[F[_]](statement: PreparedStatement[F], index: Int, value: Continent): F[Unit] =
      statement.setString(index, value.toString)

  given ResultSetReader[IO, Continent] =
    ResultSetReader.mapping[IO, String, Continent](str => Continent.valueOf(str.replace(" ", "_")))

  val table: Table[Country] = Table[Country]("country")(
    column("Code", CHAR(3).DEFAULT(""), PRIMARY_KEY),
    column("Name", CHAR(52).DEFAULT("")),
    column("Continent", ENUM(using Continent).DEFAULT(Continent.Asia)),
    column("Region", CHAR(26).DEFAULT("")),
    column("SurfaceArea", DECIMAL(10, 2).DEFAULT(0.00)),
    column("IndepYear", SMALLINT.DEFAULT(None)),
    column("Population", INT.DEFAULT(0)),
    column("LifeExpectancy", DECIMAL(3, 1).DEFAULT(None)),
    column("GNP", DECIMAL(10, 2).DEFAULT(None)),
    column("GNPOld", DECIMAL(10, 2).DEFAULT(None)),
    column("LocalName", CHAR(45).DEFAULT("")),
    column("GovernmentForm", CHAR(45).DEFAULT("")),
    column("HeadOfState", CHAR(60).DEFAULT(None)),
    column("Capital", INT.DEFAULT(None)),
    column("Code2", CHAR(2).DEFAULT(""))
  )
