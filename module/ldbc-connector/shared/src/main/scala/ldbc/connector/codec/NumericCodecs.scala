/**
 * Copyright (c) 2023-2024 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.connector.codec

import scala.compiletime.error

import scodec.bits.BitVector

import ldbc.connector.data.Type

trait NumericCodecs:

  private def safe[A](`type`: Type)(f: String => A): String => Either[String, A] = s =>
    try Right(f(s))
    catch case ex: NumberFormatException => Left(s"Invalid ${ `type`.name } $s ${ ex.getMessage }")

  private def tinyintUnsignedRange(str: String): Short =
    val short = str.toShort
    if 0 <= short && short <= 255 then short
    else throw new NumberFormatException("can only handle the range 0 ~ 255")

  private def smallintUnsignedRange(str: String): Int =
    val int = str.toInt
    if 0 <= int && int <= 65535 then int
    else throw new NumberFormatException("can only handle the range 0 ~ 65535")

  private def mediumintSignedRange(str: String): Int =
    val int = str.toInt
    if -8388608 <= int && int <= 8388607 then int
    else throw new NumberFormatException("can only handle the range -8388608 ~ 8388607")

  private def mediumintUnsignedRange(str: String): Int =
    val int = str.toInt
    if 0 <= int && int <= 16777215 then int
    else throw new NumberFormatException("can only handle the range 0 ~ 16777215")

  private def intUnsignedRange(str: String): Long =
    val long = str.toLong
    if 0 <= long && long <= 4294967295L then long
    else throw new NumberFormatException("can only handle the range 0 ~ 4294967295")

  private def bigintUnsignedRange(str: String): BigInt =
    val bigInt = BigInt(str)
    if 0 <= bigInt && bigInt <= BigInt("18446744073709551615") then bigInt
    else throw new NumberFormatException("can only handle the range 0 ~ 18446744073709551615")

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def bit(size: Int): Codec[BitVector] = Codec.simple(
    _.toBin,
    safe(Type.bit)(str => BitVector.fromByte(
      if str.length == 1 && !str.forall(_.isDigit) then str.getBytes().head
      else str.toByte
    )),
    Type.bit(size)
  )
  val bit: Codec[BitVector] = Codec.simple(
    _.toBin,
    safe(Type.bit)(str => BitVector.fromByte(
      if str.length == 1 && !str.forall(_.isDigit) then str.getBytes().head
      else str.toByte
    )),
    Type.bit
  )

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def tinyint(size: Int): Codec[Byte] = Codec.simple(_.toString, safe(Type.tinyint)(_.toByte), Type.tinyint(size))
  val tinyint:            Codec[Byte] = Codec.simple(_.toString, safe(Type.tinyint)(_.toByte), Type.tinyint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def utinyint(size: Int): Codec[Short] =
    Codec.simple(_.toString, safe(Type.utinyint)(tinyintUnsignedRange), Type.utinyint(size))
  val utinyint: Codec[Short] = Codec.simple(_.toString, safe(Type.utinyint)(tinyintUnsignedRange), Type.utinyint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def smallint(size: Int): Codec[Short] = Codec.simple(_.toString, safe(Type.smallint)(_.toShort), Type.smallint(size))
  val smallint:            Codec[Short] = Codec.simple(_.toString, safe(Type.smallint)(_.toShort), Type.smallint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def usmallint(size: Int): Codec[Int] =
    Codec.simple(_.toString, safe(Type.usmallint)(smallintUnsignedRange), Type.usmallint(size))
  val usmallint: Codec[Int] = Codec.simple(_.toString, safe(Type.usmallint)(smallintUnsignedRange), Type.usmallint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def mediumint(size: Int): Codec[Int] =
    Codec.simple(_.toString, safe(Type.mediumint)(mediumintSignedRange), Type.mediumint(size))
  val mediumint: Codec[Int] = Codec.simple(_.toString, safe(Type.mediumint)(mediumintSignedRange), Type.mediumint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def umediumint(size: Int): Codec[Int] =
    Codec.simple(_.toString, safe(Type.umediumint)(mediumintUnsignedRange), Type.mediumint(size))
  val umediumint: Codec[Int] = Codec.simple(_.toString, safe(Type.umediumint)(mediumintUnsignedRange), Type.umediumint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def int(size: Int): Codec[Int] = Codec.simple(_.toString, safe(Type.int)(_.toInt), Type.int(size))
  val int:            Codec[Int] = Codec.simple(_.toString, safe(Type.int)(_.toInt), Type.int)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def uint(size: Int): Codec[Long] = Codec.simple(_.toString, safe(Type.uint)(intUnsignedRange), Type.uint(size))
  val uint:            Codec[Long] = Codec.simple(_.toString, safe(Type.uint)(intUnsignedRange), Type.uint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def bigint(size: Int): Codec[Long] = Codec.simple(_.toString, safe(Type.bigint)(_.toLong), Type.bigint(size))
  val bigint:            Codec[Long] = Codec.simple(_.toString, safe(Type.bigint)(_.toLong), Type.bigint)

  @deprecated(
    "As of MySQL 8.0.17, the display width attribute for integer data types is deprecated. It will no longer be supported in future versions of MySQL.",
    "0.3.0"
  )
  def ubigint(size: Int): Codec[BigInt] =
    Codec.simple(_.toString, safe(Type.ubigint)(bigintUnsignedRange), Type.ubigint(size))
  val ubigint: Codec[BigInt] = Codec.simple(_.toString, safe(Type.ubigint)(bigintUnsignedRange), Type.ubigint)

  inline def decimal(
    inline accuracy: Int = 10,
    inline scale:    Int = 0
  ): Codec[BigDecimal] =
    inline if accuracy < 0 then error("The value of accuracy for DECIMAL must be an integer.")
    inline if scale < 0 then error("The DECIMAL scale value must be an integer.")
    inline if accuracy > 65 then error("The maximum number of digits for DECIMAL is 65.")
    val `type` = Type.decimal(accuracy, scale)
    Codec.simple(_.toString, safe(`type`)(BigDecimal(_)), `type`)

  val float:  Codec[Float]  = Codec.simple(_.toString, safe(Type.float)(_.toFloat), Type.float)
  val double: Codec[Double] = Codec.simple(_.toString, safe(Type.double)(_.toDouble), Type.double)

object numeric extends NumericCodecs
