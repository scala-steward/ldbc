/**
 * Copyright (c) 2023-2025 by Takahiko Tominaga
 * This software is licensed under the MIT License (MIT).
 * For more information see LICENSE or https://opensource.org/licenses/MIT
 */

package ldbc.connector

import ldbc.sql.Connection

import ldbc.connector.net.*
import ldbc.connector.net.packet.response.*
import ldbc.connector.net.protocol.*

/**
 * A connection (session) with a specific database. SQL statements are executed and results are returned within the context of a connection.
 *
 * @tparam F
 *   the effect type
 */
trait LdbcConnection[F[_]] extends Connection[F]:

  /**
   * Creates a client-side prepared statement with the given SQL.
   *
   * @param sql
   *   SQL queries based on text protocols
   */
  def clientPreparedStatement(sql: String): F[ClientPreparedStatement[F]]

  /**
   * Prepares a statement on the client, using client-side emulation
   * (irregardless of the configuration property 'useServerPrepStmts')
   * with the same semantics as the java.sql.Connection.prepareStatement()
   * method with the same argument types.
   *
   * @param sql
   *   statement
   * @param resultSetType
   *   resultSetType
   * @param resultSetConcurrency
   *   resultSetConcurrency
   * @return prepared statement
   */
  def clientPreparedStatement(
    sql:                  String,
    resultSetType:        Int,
    resultSetConcurrency: Int
  ): F[ClientPreparedStatement[F]]

  /**
   * Creates a default <code>PreparedStatement</code> object that has
   * the capability to retrieve auto-generated keys. The given constant
   * tells the driver whether it should make auto-generated keys
   * available for retrieval.  This parameter is ignored if the SQL statement
   * is not an <code>INSERT</code> statement, or an SQL statement able to return
   * auto-generated keys (the list of such statements is vendor-specific).
   * <P>
   * <B>Note:</B> This method is optimized for handling
   * parametric SQL statements that benefit from precompilation. If
   * the driver supports precompilation,
   * the method <code>prepareStatement</code> will send
   * the statement to the database for precompilation. Some drivers
   * may not support precompilation. In this case, the statement may
   * not be sent to the database until the <code>PreparedStatement</code>
   * object is executed.  This has no direct effect on users; however, it does
   * affect which methods throw certain SQLExceptions.
   * <P>
   * Result sets created using the returned <code>PreparedStatement</code>
   * object will by default be type <code>TYPE_FORWARD_ONLY</code>
   * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
   * The holdability of the created result sets can be determined by
   * calling {@link #getHoldability}.
   *
   * @param sql an SQL statement that may contain one or more '?' IN
   *        parameter placeholders
   * @param autoGeneratedKeys a flag indicating whether auto-generated keys
   *        should be returned; one of
   *        <code>Statement.RETURN_GENERATED_KEYS</code> or
   *        <code>Statement.NO_GENERATED_KEYS</code>
   * @return a new <code>PreparedStatement</code> object, containing the
   *         pre-compiled SQL statement, that will have the capability of
   *         returning auto-generated keys
   */
  def clientPreparedStatement(
    sql:               String,
    autoGeneratedKeys: Int
  ): F[ClientPreparedStatement[F]]

  /**
   * Creates a server prepared statement with the given SQL.
   *
   * @param sql
   *   SQL queries based on text protocols
   */
  def serverPreparedStatement(sql: String): F[ServerPreparedStatement[F]]

  /**
   * Prepares a statement on the server (irregardless of the
   * configuration property 'useServerPrepStmts') with the same semantics
   * as the java.sql.Connection.prepareStatement() method with the
   * same argument types.
   *
   * @param sql
   *   statement
   * @param resultSetType
   *   resultSetType
   * @param resultSetConcurrency
   *   resultSetConcurrency
   * @return prepared statement
   */
  def serverPreparedStatement(
    sql:                  String,
    resultSetType:        Int,
    resultSetConcurrency: Int
  ): F[ServerPreparedStatement[F]]

  /**
   * Creates a default <code>PreparedStatement</code> object that has
   * the capability to retrieve auto-generated keys. The given constant
   * tells the driver whether it should make auto-generated keys
   * available for retrieval.  This parameter is ignored if the SQL statement
   * is not an <code>INSERT</code> statement, or an SQL statement able to return
   * auto-generated keys (the list of such statements is vendor-specific).
   * <P>
   * <B>Note:</B> This method is optimized for handling
   * parametric SQL statements that benefit from precompilation. If
   * the driver supports precompilation,
   * the method <code>prepareStatement</code> will send
   * the statement to the database for precompilation. Some drivers
   * may not support precompilation. In this case, the statement may
   * not be sent to the database until the <code>PreparedStatement</code>
   * object is executed.  This has no direct effect on users; however, it does
   * affect which methods throw certain SQLExceptions.
   * <P>
   * Result sets created using the returned <code>PreparedStatement</code>
   * object will by default be type <code>TYPE_FORWARD_ONLY</code>
   * and have a concurrency level of <code>CONCUR_READ_ONLY</code>.
   * The holdability of the created result sets can be determined by
   * calling {@link # getHoldability}.
   *
   * @param sql               an SQL statement that may contain one or more '?' IN
   *                          parameter placeholders
   * @param autoGeneratedKeys a flag indicating whether auto-generated keys
   *                          should be returned; one of
   *                          <code>Statement.RETURN_GENERATED_KEYS</code> or
   *                          <code>Statement.NO_GENERATED_KEYS</code>
   * @return a new <code>PreparedStatement</code> object, containing the
   *         pre-compiled SQL statement, that will have the capability of
   *         returning auto-generated keys
   */
  def serverPreparedStatement(
    sql:               String,
    autoGeneratedKeys: Int
  ): F[ServerPreparedStatement[F]]

  /**
   * Retrieves the statistics of this Connection object.
   *
   * @return
   *   the statistics of this Connection object
   */
  def getStatistics: F[StatisticsPacket]

  /**
   * Resets the server-side state of this connection. 
   */
  def resetServerState: F[Unit]

  /**
   * Changes the user and password for this connection.
   *
   * @param user
   *   the new user name
   * @param password
   *   the new password
   */
  def changeUser(user: String, password: String): F[Unit]
