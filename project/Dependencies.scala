/**
 *  This file is part of the ldbc.
 *  For the full copyright and license information,
 *  please view the LICENSE file that was distributed with this source code.
 */

import sbt._

object Dependencies {

  val cats = "org.typelevel" %% "cats-core" % "2.9.0"
  val catsEffect = "org.typelevel" %% "cats-effect" % "3.5.0"

  val schemaspy = "org.schemaspy" % "schemaspy" % "6.2.2"

  val parserCombinators = "org.scala-lang.modules" %% "scala-parser-combinators" % "2.3.0"

  // TODO: Scala3 compatible version needs to be officially released
  val slick = "com.typesafe.slick" %% "slick" % "3.5.0-pre.71.7c7e79ee.dirty"

  val specs2Version = "5.2.0"
  val specs2: Seq[ModuleID] = Seq(
    "specs2-core",
    "specs2-junit",
  ).map("org.specs2" %% _ % specs2Version % Test)

  val scalaTest = "org.scalatest" %% "scalatest" % "3.2.15" % Test

  val mockito = "org.mockito" % "mockito-inline" % "5.2.0" % Test
}
