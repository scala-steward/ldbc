/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

import cats.effect.*
import cats.effect.unsafe.implicits.global

import org.typelevel.otel4s.trace.Tracer

import ldbc.dsl.io.*

import ldbc.connector.*

@main def program1(): Unit =
  // #given
  given Tracer[IO] = Tracer.noop[IO]
  // #given

  // #program
  val program: DBIO[Int] = DBIO.pure[IO, Int](1)
  // #program

  // #connection
  def connection = Connection[IO](
    host     = "127.0.0.1",
    port     = 13306,
    user     = "ldbc",
    password = Some("password"),
    ssl      = SSL.Trusted
  )
  // #connection

  // #run
  connection
    .use { conn =>
      program.readOnly(conn).map(println(_))
    }
    .unsafeRunSync()
  // 1
  // #run
