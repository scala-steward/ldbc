/**
 * Copyright (c) 2023-2025 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.connector.net.protocol

import java.time.*

import scala.collection.immutable.{ ListMap, SortedMap }

import cats.*
import cats.syntax.all.*

import cats.effect.*

import org.typelevel.otel4s.trace.{ Span, Tracer }
import org.typelevel.otel4s.Attribute

import ldbc.sql.{ CallableStatement, DatabaseMetaData, ParameterMetaData, ResultSet, Statement }

import ldbc.connector.*
import ldbc.connector.data.*
import ldbc.connector.exception.SQLException
import ldbc.connector.net.packet.request.*
import ldbc.connector.net.packet.response.*
import ldbc.connector.net.Protocol

case class CallableStatementImpl[F[_]: Exchange: Tracer](
  protocol:                Protocol[F],
  serverVariables:         Map[String, String],
  sql:                     String,
  paramInfo:               CallableStatementImpl.ParamInfo,
  params:                  Ref[F, SortedMap[Int, Parameter]],
  batchedArgs:             Ref[F, Vector[String]],
  connectionClosed:        Ref[F, Boolean],
  statementClosed:         Ref[F, Boolean],
  resultSetClosed:         Ref[F, Boolean],
  currentResultSet:        Ref[F, Option[ResultSet]],
  outputParameterResult:   Ref[F, Option[ResultSetImpl]],
  resultSets:              Ref[F, List[ResultSetImpl]],
  parameterIndexToRsIndex: Ref[F, Map[Int, Int]],
  updateCount:             Ref[F, Long],
  moreResults:             Ref[F, Boolean],
  autoGeneratedKeys:       Ref[F, Int],
  lastInsertId:            Ref[F, Long],
  resultSetType:           Int = ResultSet.TYPE_FORWARD_ONLY,
  resultSetConcurrency:    Int = ResultSet.CONCUR_READ_ONLY
)(using F: MonadThrow[F])
  extends CallableStatement[F],
          SharedPreparedStatement[F]:

  private val attributes = protocol.initialPacket.attributes ++ List(
    Attribute("type", "CallableStatement"),
    Attribute("sql", sql)
  )

  override def executeQuery(): F[ResultSet] =
    checkClosed() *>
      checkNullOrEmptyQuery(sql) *>
      exchange[F, ResultSet]("statement") { (span: Span[F]) =>
        if sql.toUpperCase.startsWith("CALL") then
          executeCallStatement(span).flatMap { resultSets =>
            resultSets.headOption match
              case None =>
                for
                  resultSet <- F.pure(
                                 ResultSetImpl.empty(
                                   serverVariables,
                                   protocol.initialPacket.serverVersion
                                 )
                               )
                  _ <- currentResultSet.set(Some(resultSet))
                yield resultSet
              case Some(resultSet) =>
                currentResultSet.update(_ => Some(resultSet)) *> resultSet.pure[F]
          } <* retrieveOutParams()
        else
          params.get.flatMap { params =>
            span.addAttributes(
              (attributes ++ List(
                Attribute("params", params.map((_, param) => param.toString).mkString(", ")),
                Attribute("execute", "query")
              ))*
            ) *>
              protocol.resetSequenceId *>
              protocol.send(
                ComQueryPacket(buildQuery(sql, params), protocol.initialPacket.capabilityFlags, ListMap.empty)
              ) *>
              receiveQueryResult()
          }
      } <* params.set(SortedMap.empty)

  override def executeLargeUpdate(): F[Long] =
    checkClosed() *>
      checkNullOrEmptyQuery(sql) *>
      exchange[F, Long]("statement") { (span: Span[F]) =>
        if sql.toUpperCase.startsWith("CALL") then
          executeCallStatement(span).flatMap { resultSets =>
            resultSets.headOption match
              case None =>
                for
                  resultSet <- F.pure(
                                 ResultSetImpl.empty(
                                   serverVariables,
                                   protocol.initialPacket.serverVersion
                                 )
                               )
                  _ <- currentResultSet.set(Some(resultSet))
                yield resultSet
              case Some(resultSet) =>
                currentResultSet.update(_ => Some(resultSet)) *> resultSet.pure[F]
          } *> retrieveOutParams() *> F.pure(-1)
        else
          params.get.flatMap { params =>
            span.addAttributes(
              (attributes ++ List(
                Attribute("params", params.map((_, param) => param.toString).mkString(", ")),
                Attribute("execute", "update")
              ))*
            ) *>
              sendQuery(buildQuery(sql, params)).flatMap {
                case result: OKPacket => lastInsertId.set(result.lastInsertId) *> F.pure(result.affectedRows)
                case error: ERRPacket => F.raiseError(error.toException(Some(sql), None))
                case _: EOFPacket     => F.raiseError(new SQLException("Unexpected EOF packet"))
              }
          }
      }

  override def execute(): F[Boolean] =
    checkClosed() *>
      checkNullOrEmptyQuery(sql) *>
      exchange[F, Boolean]("statement") { (span: Span[F]) =>
        if sql.toUpperCase.startsWith("CALL") then
          executeCallStatement(span).flatMap { results =>
            moreResults.update(_ => results.nonEmpty) *>
              currentResultSet.update(_ => results.headOption) *>
              resultSets.set(results.toList) *>
              F.pure(results.nonEmpty)
          } <* retrieveOutParams()
        else
          params.get
            .flatMap { params =>
              span.addAttributes(
                (attributes ++ List(
                  Attribute("params", params.map((_, param) => param.toString).mkString(", ")),
                  Attribute("execute", "update")
                ))*
              ) *>
                sendQuery(buildQuery(sql, params)).flatMap {
                  case result: OKPacket => lastInsertId.set(result.lastInsertId) *> F.pure(result.affectedRows)
                  case error: ERRPacket => F.raiseError(error.toException(Some(sql), None))
                  case _: EOFPacket     => F.raiseError(new SQLException("Unexpected EOF packet"))
                }
            }
            .map(_ => false)
      }

  override def getMoreResults(): F[Boolean] =
    checkClosed() *> moreResults.get.flatMap { isMoreResults =>
      if isMoreResults then
        resultSets.get.flatMap {
          case Nil               => moreResults.set(false) *> F.pure(false)
          case resultSet :: tail =>
            currentResultSet.set(Some(resultSet)) *> resultSets.set(tail) *> F.pure(true)
        }
      else F.pure(false)
    }

  override def addBatch(): F[Unit] =
    checkClosed() *>
      checkNullOrEmptyQuery(sql) *> (
        sql.toUpperCase match
          case q if q.startsWith("CALL") =>
            setInOutParamsOnServer(paramInfo) *> setOutParams()
          case _ => F.unit
      ) *>
      params.get.flatMap { params =>
        batchedArgs.update(_ :+ buildBatchQuery(sql, params))
      } *>
      params.set(SortedMap.empty)

  override def clearBatch(): F[Unit] = batchedArgs.set(Vector.empty)

  override def executeLargeBatch(): F[Array[Long]] =
    checkClosed() *>
      checkNullOrEmptyQuery(sql) *>
      exchange[F, Array[Long]]("statement") { (span: Span[F]) =>
        batchedArgs.get.flatMap { args =>
          span.addAttributes(
            (attributes ++ List(
              Attribute("execute", "batch"),
              Attribute("size", args.length.toLong),
              Attribute("sql", args.toArray.toSeq)
            ))*
          ) *> (
            if args.isEmpty then F.pure(Array.empty)
            else
              sql.toUpperCase match
                case q if q.startsWith("INSERT") =>
                  sendQuery(sql.split("VALUES").head + " VALUES" + args.mkString(","))
                    .flatMap {
                      case _: OKPacket      => F.pure(Array.fill(args.length)(Statement.SUCCESS_NO_INFO.toLong))
                      case error: ERRPacket => F.raiseError(error.toException(Some(sql), None))
                      case _: EOFPacket     => F.raiseError(new SQLException("Unexpected EOF packet"))
                    }
                case q if q.startsWith("update") || q.startsWith("delete") || q.startsWith("CALL") =>
                  protocol.resetSequenceId *>
                    protocol.comSetOption(EnumMySQLSetOption.MYSQL_OPTION_MULTI_STATEMENTS_ON) *>
                    protocol.resetSequenceId *>
                    protocol.send(
                      ComQueryPacket(
                        args.mkString(";"),
                        protocol.initialPacket.capabilityFlags,
                        ListMap.empty
                      )
                    ) *>
                    args
                      .foldLeft(F.pure(Vector.empty[Long])) { ($acc, _) =>
                        for
                          acc    <- $acc
                          result <-
                            protocol
                              .receive(GenericResponsePackets.decoder(protocol.initialPacket.capabilityFlags))
                              .flatMap {
                                case result: OKPacket =>
                                  lastInsertId.set(result.lastInsertId) *> F.pure(acc :+ result.affectedRows)
                                case error: ERRPacket =>
                                  F.raiseError(error.toException("Failed to execute batch", acc))
                                case _: EOFPacket => F.raiseError(new SQLException("Unexpected EOF packet"))
                              }
                        yield result
                      }
                      .map(_.toArray) <*
                    protocol.resetSequenceId <*
                    protocol.comSetOption(EnumMySQLSetOption.MYSQL_OPTION_MULTI_STATEMENTS_OFF)
                case _ =>
                  F.raiseError(
                    new SQLException("The batch query must be an INSERT, UPDATE, or DELETE, CALL statement.")
                  )
          )
        }
      } <* params.set(SortedMap.empty) <* batchedArgs.set(Vector.empty)

  override def getGeneratedKeys(): F[ResultSet] =
    autoGeneratedKeys.get.flatMap {
      case Statement.RETURN_GENERATED_KEYS =>
        for
          lastInsertId <- lastInsertId.get
          resultSet    <- F.pure(
                         ResultSetImpl(
                           Vector(new ColumnDefinitionPacket:
                             override def table:      String                     = ""
                             override def name:       String                     = "GENERATED_KEYS"
                             override def columnType: ColumnDataType             = ColumnDataType.MYSQL_TYPE_LONGLONG
                             override def flags:      Seq[ColumnDefinitionFlags] = Seq.empty),
                           Vector(ResultSetRowPacket(Array(Some(lastInsertId.toString)))),
                           serverVariables,
                           protocol.initialPacket.serverVersion
                         )
                       )
          _ <- currentResultSet.set(Some(resultSet))
        yield resultSet
      case Statement.NO_GENERATED_KEYS =>
        F.raiseError(
          new SQLException(
            "Generated keys not requested. You need to specify Statement.RETURN_GENERATED_KEYS to Statement.executeUpdate(), Statement.executeLargeUpdate() or Connection.prepareStatement()."
          )
        )
    }

  override def close(): F[Unit] = statementClosed.set(true) *> resultSetClosed.set(true)

  override def registerOutParameter(parameterIndex: Int, sqlType: Int): F[Unit] =
    if paramInfo.numParameters > 0 then
      paramInfo.parameterList.find(_.index == parameterIndex) match
        case Some(param) =>
          (if param.jdbcType == sqlType then F.unit
           else
             F.raiseError(
               new SQLException(
                 "The type specified for the parameter does not match the type registered as a procedure."
               )
             )
          ) *> (
            if param.isOut && param.isIn then
              val paramName          = param.paramName.getOrElse("nullnp" + param.index)
              val inOutParameterName = mangleParameterName(paramName)

              val queryBuf = new StringBuilder(4 + inOutParameterName.length + 1)
              queryBuf.append("SET ")
              queryBuf.append(inOutParameterName)
              queryBuf.append("=")

              params.get.flatMap { params =>
                val sql = queryBuf.toString ++ params.get(param.index).fold("NULL")(_.sql)
                sendQuery(sql).flatMap {
                  case _: OKPacket      => F.unit
                  case error: ERRPacket => F.raiseError(error.toException(Some(sql), None))
                  case _: EOFPacket     => F.raiseError(new SQLException("Unexpected EOF packet"))
                }
              }
            else F.raiseError(new SQLException("No output parameters returned by procedure."))
          )
        case None =>
          F.raiseError(
            new SQLException(s"Parameter index of $parameterIndex is out of range (1, ${ paramInfo.numParameters })")
          )
    else F.unit

  override def getString(parameterIndex: Int): F[Option[String]] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getString(index)))
    yield Option(value)

  override def getBoolean(parameterIndex: Int): F[Boolean] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getBoolean(index)))
    yield value

  override def getByte(parameterIndex: Int): F[Byte] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getByte(index)))
    yield value

  override def getShort(parameterIndex: Int): F[Short] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getShort(index)))
    yield value

  override def getInt(parameterIndex: Int): F[Int] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getInt(index)))
    yield value

  override def getLong(parameterIndex: Int): F[Long] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getLong(index)))
    yield value

  override def getFloat(parameterIndex: Int): F[Float] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getFloat(index)))
    yield value

  override def getDouble(parameterIndex: Int): F[Double] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getDouble(index)))
    yield value

  override def getBytes(parameterIndex: Int): F[Option[Array[Byte]]] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getBytes(index)))
    yield Option(value)

  override def getDate(parameterIndex: Int): F[Option[LocalDate]] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getDate(index)))
    yield Option(value)

  override def getTime(parameterIndex: Int): F[Option[LocalTime]] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getTime(index)))
    yield Option(value)

  override def getTimestamp(parameterIndex: Int): F[Option[LocalDateTime]] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getTimestamp(index)))
    yield Option(value)

  override def getBigDecimal(parameterIndex: Int): F[Option[BigDecimal]] =
    for
      resultSet <- checkBounds(parameterIndex) *> getOutputParameters()
      paramMap  <- parameterIndexToRsIndex.get
      index = paramMap.getOrElse(parameterIndex, parameterIndex)
      value <-
        (if index == CallableStatementImpl.NOT_OUTPUT_PARAMETER_INDICATOR then
           F.raiseError(new SQLException(s"Parameter $parameterIndex is not registered as an output parameter"))
         else shiftF(resultSet.getBigDecimal(index)))
    yield Option(value)

  override def getString(parameterName: String): F[Option[String]] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getString(mangleParameterName(parameterName)))
    yield Option(value)

  override def getBoolean(parameterName: String): F[Boolean] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getBoolean(mangleParameterName(parameterName)))
    yield value

  override def getByte(parameterName: String): F[Byte] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getByte(mangleParameterName(parameterName)))
    yield value

  override def getShort(parameterName: String): F[Short] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getShort(mangleParameterName(parameterName)))
    yield value

  override def getInt(parameterName: String): F[Int] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getInt(mangleParameterName(parameterName)))
    yield value

  override def getLong(parameterName: String): F[Long] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getLong(mangleParameterName(parameterName)))
    yield value

  override def getFloat(parameterName: String): F[Float] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getFloat(mangleParameterName(parameterName)))
    yield value

  override def getDouble(parameterName: String): F[Double] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getDouble(mangleParameterName(parameterName)))
    yield value

  override def getBytes(parameterName: String): F[Option[Array[Byte]]] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getBytes(mangleParameterName(parameterName)))
    yield Option(value)

  override def getDate(parameterName: String): F[Option[LocalDate]] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getDate(mangleParameterName(parameterName)))
    yield Option(value)

  override def getTime(parameterName: String): F[Option[LocalTime]] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getTime(mangleParameterName(parameterName)))
    yield Option(value)

  override def getTimestamp(parameterName: String): F[Option[LocalDateTime]] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getTimestamp(mangleParameterName(parameterName)))
    yield Option(value)

  override def getBigDecimal(parameterName: String): F[Option[BigDecimal]] =
    for
      resultSet <- getOutputParameters()
      value     <- shiftF(resultSet.getBigDecimal(mangleParameterName(parameterName)))
    yield Option(value)

  private def setParameter(index: Int, value: String): F[Unit] =
    params.update(_ + (index -> Parameter.parameter(value)))

  private def sendQuery(sql: String): F[GenericResponsePackets] =
    checkNullOrEmptyQuery(sql) *> protocol.resetSequenceId *> protocol.send(
      ComQueryPacket(sql, protocol.initialPacket.capabilityFlags, ListMap.empty)
    ) *> protocol.receive(GenericResponsePackets.decoder(protocol.initialPacket.capabilityFlags))

  private def receiveUntilOkPacket(resultSets: Vector[ResultSetImpl]): F[Vector[ResultSetImpl]] =
    protocol.receive(ColumnsNumberPacket.decoder(protocol.initialPacket.capabilityFlags)).flatMap {
      case _: OKPacket                 => resultSets.pure[F]
      case error: ERRPacket            => F.raiseError(error.toException(Some(sql), None))
      case result: ColumnsNumberPacket =>
        for
          columnDefinitions <-
            protocol.repeatProcess(
              result.size,
              ColumnDefinitionPacket.decoder(protocol.initialPacket.capabilityFlags)
            )
          resultSetRow <-
            protocol.readUntilEOF[ResultSetRowPacket](
              ResultSetRowPacket.decoder(protocol.initialPacket.capabilityFlags, columnDefinitions.length)
            )
          resultSet = ResultSetImpl(
                        columnDefinitions,
                        resultSetRow,
                        serverVariables,
                        protocol.initialPacket.serverVersion,
                        resultSetType,
                        resultSetConcurrency
                      )
          resultSets <- receiveUntilOkPacket(resultSets :+ resultSet)
        yield resultSets
    }

  private def receiveQueryResult(): F[ResultSet] =
    protocol.receive(ColumnsNumberPacket.decoder(protocol.initialPacket.capabilityFlags)).flatMap {
      case _: OKPacket =>
        F.pure(
          ResultSetImpl
            .empty(
              serverVariables,
              protocol.initialPacket.serverVersion
            )
        )
      case error: ERRPacket            => F.raiseError(error.toException(Some(sql), None))
      case result: ColumnsNumberPacket =>
        for
          columnDefinitions <-
            protocol.repeatProcess(
              result.size,
              ColumnDefinitionPacket.decoder(protocol.initialPacket.capabilityFlags)
            )
          resultSetRow <- protocol.readUntilEOF[ResultSetRowPacket](
                            ResultSetRowPacket.decoder(protocol.initialPacket.capabilityFlags, columnDefinitions.length)
                          )
          resultSet = ResultSetImpl(
                        columnDefinitions,
                        resultSetRow,
                        serverVariables,
                        protocol.initialPacket.serverVersion,
                        resultSetType,
                        resultSetConcurrency
                      )
          _ <- currentResultSet.set(Some(resultSet))
        yield resultSet
    }

  /**
   * Change the parameter name to an arbitrary prefixed naming.
   *
   * @param origParameterName
   * the original parameter name
   * @return
   * the parameter name
   */
  private def mangleParameterName(origParameterName: String): String =
    val offset = if origParameterName.nonEmpty && origParameterName.charAt(0) == '@' then 1 else 0

    val paramNameBuf = new StringBuilder(
      CallableStatementImpl.PARAMETER_NAMESPACE_PREFIX.length + origParameterName.length
    )
    paramNameBuf.append(CallableStatementImpl.PARAMETER_NAMESPACE_PREFIX)
    paramNameBuf.append(origParameterName.substring(offset))

    paramNameBuf.toString

  /**
   * Set output parameters to be used by the server.
   *
   * @param paramInfo
   * the parameter information
   */
  private def setInOutParamsOnServer(paramInfo: CallableStatementImpl.ParamInfo): F[Unit] =
    if paramInfo.numParameters > 0 then
      paramInfo.parameterList.foldLeft(F.unit) { (acc, param) =>
        if param.isOut && param.isIn then
          val paramName          = param.paramName.getOrElse("nullnp" + param.index)
          val inOutParameterName = mangleParameterName(paramName)

          val queryBuf = new StringBuilder(4 + inOutParameterName.length + 1)
          queryBuf.append("SET ")
          queryBuf.append(inOutParameterName)
          queryBuf.append("=")

          acc *> params.get.flatMap { params =>
            val sql = queryBuf.toString ++ params.get(param.index).fold("NULL")(_.sql)
            sendQuery(sql).flatMap {
              case _: OKPacket      => F.unit
              case error: ERRPacket => F.raiseError(error.toException(Some(sql), None))
              case _: EOFPacket     => F.raiseError(new SQLException("Unexpected EOF packet"))
            }
          }
        else acc
      }
    else F.unit

  /**
   * Set output parameters to be handled by the client.
   */
  private def setOutParams(): F[Unit] =
    if paramInfo.numParameters > 0 then
      paramInfo.parameterList.foldLeft(F.unit) { (acc, param) =>
        if !paramInfo.isFunctionCall && param.isOut then
          val paramName        = param.paramName.getOrElse("nullnp" + param.index)
          val outParameterName = mangleParameterName(paramName)

          acc *> params.get.flatMap { params =>
            for
              outParamIndex <- (
                                 if params.isEmpty then F.pure(param.index)
                                 else
                                   params.keys
                                     .find(_ == param.index)
                                     .fold(
                                       F.raiseError(
                                         new SQLException(
                                           s"Parameter ${ param.index } is not registered as an output parameter"
                                         )
                                       )
                                     )(_.pure[F])
                               )
              _ <- setParameter(outParamIndex, outParameterName)
            yield ()
          }
        else acc
      }
    else F.unit

  /**
   * Issues a second query to retrieve all output parameters.
   */
  private def retrieveOutParams(): F[Unit] =
    val parameters = paramInfo.parameterList.foldLeft(Vector.empty[(Int, String)]) { (acc, param) =>
      if param.isOut then
        val paramName        = param.paramName.getOrElse("nullnp" + param.index)
        val outParameterName = mangleParameterName(paramName)
        acc :+ (param.index, outParameterName)
      else acc
    }

    if paramInfo.numParameters > 0 && parameters.nonEmpty then

      val sql = parameters.zipWithIndex
        .map {
          case ((_, paramName), index) =>
            val prefix = if index != 0 then ", " else ""
            val atSign = if !paramName.startsWith("@") then "@" else ""
            s"$prefix$atSign$paramName"
        }
        .mkString("SELECT ", "", "")

      checkClosed() *>
        checkNullOrEmptyQuery(sql) *>
        protocol.resetSequenceId *>
        protocol.send(ComQueryPacket(sql, protocol.initialPacket.capabilityFlags, ListMap.empty)) *>
        receiveQueryResult().flatMap {
          case resultSet: ResultSetImpl => outputParameterResult.update(_ => Some(resultSet))
        } *>
        parameters.zipWithIndex.foldLeft(F.unit) {
          case (acc, ((paramIndex, _), index)) =>
            acc *> parameterIndexToRsIndex.update(_ + (paramIndex -> (index + 1)))
        }
    else F.unit

  /**
   * Returns the ResultSet that holds the output parameters, or throws an
   * appropriate exception if none exist, or they weren't returned.
   *
   * @return
   * the ResultSet that holds the output parameters
   */
  private def getOutputParameters(): F[ResultSetImpl] =
    outputParameterResult.get.flatMap {
      case None =>
        if paramInfo.numParameters == 0 then F.raiseError(new SQLException("No output parameters registered."))
        else F.raiseError(new SQLException("No output parameters returned by procedure."))
      case Some(resultSet) => resultSet.pure[F]
    }

  /**
   * Checks if the parameter index is within the bounds of the number of parameters.
   *
   * @param paramIndex
   * the parameter index to check
   */
  private def checkBounds(paramIndex: Int): F[Unit] =
    if paramIndex < 1 || paramIndex > paramInfo.numParameters then
      F.raiseError(
        new SQLException(s"Parameter index of ${ paramIndex } is out of range (1, ${ paramInfo.numParameters })")
      )
    else F.unit

  /**
   * Executes a CALL/Stored function statement.
   *
   * @param span
   * the span
   * @return
   * a list of ResultSet
   */
  private def executeCallStatement(span: Span[F]): F[Vector[ResultSetImpl]] =
    setInOutParamsOnServer(paramInfo) *>
      setOutParams() *>
      params.get.flatMap { params =>
        span.addAttributes(
          (attributes ++ List(
            Attribute("params", params.map((_, param) => param.toString).mkString(", ")),
            Attribute("execute", "query")
          ))*
        ) *>
          protocol.resetSequenceId *>
          protocol.send(
            ComQueryPacket(buildQuery(sql, params), protocol.initialPacket.capabilityFlags, ListMap.empty)
          ) *>
          receiveUntilOkPacket(Vector.empty)
      }

object CallableStatementImpl:

  val NOT_OUTPUT_PARAMETER_INDICATOR: Int = Int.MinValue

  private val PARAMETER_NAMESPACE_PREFIX = "@ldbc_mysql_outparam_"

  /**
   * CallableStatementParameter represents a parameter in a stored procedure.
   *
   * @param paramName
   *   the name of the parameter
   * @param isIn
   *   whether the parameter is an input parameter
   * @param isOut
   *   whether the parameter is an output parameter
   * @param index
   *   the index of the parameter
   * @param jdbcType
   *   the JDBC type of the parameter
   * @param typeName
   *   the name of the type of the parameter
   * @param precision
   *   the precision of the parameter
   * @param scale
   *   the scale of the parameter
   * @param nullability
   *   the nullability of the parameter
   * @param inOutModifier
   *   the in/out modifier of the parameter
   */
  case class CallableStatementParameter(
    paramName:     Option[String],
    isIn:          Boolean,
    isOut:         Boolean,
    index:         Int,
    jdbcType:      Int,
    typeName:      Option[String],
    precision:     Int,
    scale:         Int,
    nullability:   Short,
    inOutModifier: Int
  )

  /**
   * ParamInfo represents the information about the parameters in a stored procedure.
   *
   * @param nativeSql
   *   the original SQL statement
   * @param dbInUse
   *   the database in use
   * @param isFunctionCall
   *   whether the SQL statement is a function call
   * @param numParameters
   *   the number of parameters in the SQL statement
   * @param parameterList
   *   a list of CallableStatementParameter representing each parameter
   * @param parameterMap
   *   a map from parameter name to CallableStatementParameter
   */
  case class ParamInfo(
    nativeSql:      String,
    dbInUse:        Option[String],
    isFunctionCall: Boolean,
    numParameters:  Int,
    parameterList:  List[CallableStatementParameter],
    parameterMap:   ListMap[String, CallableStatementParameter]
  )

  object ParamInfo:

    def apply(
      nativeSql:      String,
      database:       Option[String],
      resultSet:      ResultSetImpl,
      isFunctionCall: Boolean
    ): ParamInfo =
      val builder = List.newBuilder[CallableStatementParameter]
      while resultSet.next() do
        val index           = resultSet.getRow()
        val paramName       = resultSet.getString(4)
        val procedureColumn = resultSet.getInt(5)
        val jdbcType        = resultSet.getInt(6)
        val typeName        = resultSet.getString(7)
        val precision       = resultSet.getInt(8)
        val scale           = resultSet.getInt(19)
        val nullability     = resultSet.getShort(12)

        val inOutModifier = procedureColumn match
          case DatabaseMetaData.procedureColumnIn    => ParameterMetaData.parameterModeIn
          case DatabaseMetaData.procedureColumnInOut => ParameterMetaData.parameterModeInOut
          case DatabaseMetaData.procedureColumnOut | DatabaseMetaData.procedureColumnReturn =>
            ParameterMetaData.parameterModeOut
          case _ => ParameterMetaData.parameterModeUnknown

        val (isOutParameter, isInParameter) =
          if index - 1 == 0 && isFunctionCall then (true, false)
          else if inOutModifier == DatabaseMetaData.procedureColumnInOut then (true, true)
          else if inOutModifier == DatabaseMetaData.procedureColumnIn then (false, true)
          else if inOutModifier == DatabaseMetaData.procedureColumnOut then (true, false)
          else (false, false)

        builder += CallableStatementParameter(
          Option(paramName),
          isInParameter,
          isOutParameter,
          index,
          jdbcType,
          Option(typeName),
          precision,
          scale,
          nullability,
          inOutModifier
        )

      val parameterList = builder.result()

      ParamInfo(
        nativeSql      = nativeSql,
        dbInUse        = database,
        isFunctionCall = isFunctionCall,
        numParameters  = resultSet.rowLength(),
        parameterList  = parameterList,
        parameterMap   = ListMap(parameterList.map(p => p.paramName.getOrElse("") -> p)*)
      )
