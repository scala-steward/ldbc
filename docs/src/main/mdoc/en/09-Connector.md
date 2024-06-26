# Connector

This chapter describes database connections using LDBC's own MySQL connector.

To make a connection to a MySQL database in Scala, you need to use JDBC, which is a standard Java API that can also be used in Scala.
JDBC is implemented in Java and can only work in a JVM environment, even when used in Scala.

The recent environment surrounding Scala has seen a lot of development of plug-ins to work with JS, Native, and other environments.
Scala continues to evolve from a language that runs only in the JVM, where Java assets can be used, to one that can run in a multi-platform environment.

However, JDBC is a standard Java API and does not support operation in Scala's multiplatform environment.

Therefore, even if you create an application in Scala that can run on JS, Native, etc., you will not be able to connect to databases such as MySQL because you cannot use JDBC.

Typelevel Project has a Scala library for [PostgreSQL](https://www.postgresql.org/) called [Skunk](https://github.com/typelevel/skunk).
This project does not use JDBC and uses only pure Scala to connect to PostgreSQL. Therefore, Skunk can be used to connect to PostgreSQL in any JVM, JS, or Native environment.

The LDBC connector is a Skunk-inspired project that is being developed to enable connections to MySQL in any JVM, JS, or Native environment.

※ This connector is currently an experimental feature. Therefore, please do not use it in a production environment.

The LDBC connector is the lowest layer API.
We plan to use this connector to provide higher-layer APIs in the future. We also plan to make it compatible with existing higher-layer APIs.

The following dependencies must be set up in your project in order to use it.

**JVM**

@@@ vars
```scala
libraryDependencies += "$org$" %% "ldbc-connector" % "$version$"
```
@@@

**JS/Native**

@@@ vars
```scala
libraryDependencies += "$org$" %%% "ldbc-connector" % "$version$"
```
@@@

**Supported Versions**

The current version supports the following versions of MySQL

- MySQL 5.7.x
- MySQL 8.x

The main support is for MySQL 8.x. MySQL 5.7.x is a sub-support. Therefore, be careful when working with MySQL 5.7.x.
We plan to discontinue support for MySQL 5.7.x in the future.

## Connection

Use `Connection` to make a connection to MySQL using the LDBC connector.

In addition, `Connection` allows the use of `Otel4s` to collect telemetry data in order to allow observer-aware development.
Therefore, when using `Connection`, the `Tracer` of `Otel4s` must be set.

It is recommended to use `Tracer.noop` during development or when telemetry data using traces is not needed.

```scala
import cats.effect.IO
import org.typelevel.otel4s.trace.Tracer
import ldbc.connector.Connection

given Tracer[IO] = Tracer.noop[IO]

val connection = Connection[IO](
  host = "127.0.0.1",
  port = 3306,
  user = "root",
)
```

The following is a list of properties that can be set when constructing a `Connection`.

| Property                | Type               | Use                                                                                                        |
|-------------------------|--------------------|------------------------------------------------------------------------------------------------------------|
| host                    | String             | Specify the host for the MySQL server                                                                      |
| port                    | Int                | Specify the port number of the MySQL server                                                                |
| user                    | String             | Specify the user name to log in to the MySQL server                                                        |
| password                | Option[String]     | Specify the password of the user who will log in to the MySQL server                                       |
| database                | Option[String]     | Specify the database name to be used after connecting to the MySQL server                                  |
| debug                   | Boolean            | Outputs a log of the process. Default is false.                                                            |
| ssl                     | SSL                | Specifies whether SSL/TLS is used for notifications to and from the MySQL server. The default is SSL.None. |
| socketOptions           | List[SocketOption] | Specifies socket options for TCP/UDP sockets.                                                              |
| readTimeout             | Duration           | Specifies the timeout before an attempt is made to connect to the MySQL server. Default is Duration.Inf.   |
| allowPublicKeyRetrieval | Boolean            | Specifies whether to use the RSA public key when authenticating with the MySQL server. Default is false.   |

Connection` uses `Resource` to manage resources. Therefore, when connection information is used, the `use` method is used to manage the resource.

```scala
connection.use { conn =>
  // Write code
}
```

### Authentication

Authentication in MySQL involves the client sending user information in a phase called LoginRequest when connecting to the MySQL server. The server then looks up the user in the `mysql.user` table to determine which authentication plugin to use. After the authentication plugin is determined, the server calls the plugin to initiate user authentication and sends the results to the client. In this way, authentication is pluggable (various types of plug-ins can be added and removed) in MySQL.

Authentication plug-ins supported by MySQL are listed on the [official page](https://dev.mysql.com/doc/refman/8.0/ja/authentication-plugins.html).

LDBC currently supports the following authentication plug-ins

- Native pluggable authentication
- SHA-256 pluggable authentication
- Cache of SHA-2 pluggable certificates

※ Native pluggable authentication and SHA-256 pluggable authentication are plugins that have been deprecated since MySQL 8.x. It is recommended that you use the SHA-2 pluggable authentication cache unless you have a good reason to do otherwise.

There is no need to be aware of authentication plug-ins in the LDBC application code. Users simply create a user created with the authentication plugin they wish to use on the MySQL database and then attempt to connect to MySQL using that user in the LDBC application code.
LDBC will internally determine the authentication plugin and use the appropriate authentication plugin to connect to MySQL.

## Execution

The following tables are assumed to be used in the subsequent process.

```sql
CREATE TABLE users (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  name VARCHAR(255) NOT NULL,
  age INT NULL
);
```

### Statement

`Statement` is an API for executing SQL without dynamic parameters.

※ Since `Statement` does not use dynamic parameters, there is a risk of SQL injection depending on its usage. Therefore, it is recommended to use `PreparedStatement` when dynamic parameters are used.

Construct a `Statement` using the `createStatement` method of `Connection`.

#### Read query

Use the `executeQuery` method to execute read-only SQL.

The values returned by the MySQL server as a result of executing the query are stored in a `ResultSet` and returned as the return value.

```scala
connection.use { conn =>
  for
    statement <- conn.createStatement()
    result <- statement.executeQuery("SELECT * FROM users")
  yield
    // Processing with ResultSet
}
```

#### Write Query

Use the `executeUpdate` method to execute SQL to write.

The value returned by the MySQL server as a result of executing the query is the number of rows affected.

```scala
connection.use { conn =>
  for
    statement <- conn.createStatement()
    result <- statement.executeUpdate("INSERT INTO users (name, age) VALUES ('Alice', 20)")
  yield
}
```

#### Get the value of AUTO_INCREMENT

Use the `getGeneratedKeys` method to retrieve the AUTO_INCREMENT value after the query is executed using `Statement`.

The value returned by the MySQL server as a result of executing the query will be the value generated for AUTO_INCREMENT as the return value.

```scala
connection.use { conn =>
  for
    statement <- conn.createStatement()
    _ <- statement.executeUpdate("INSERT INTO users (name, age) VALUES ('Alice', 20)", Statement.RETURN_GENERATED_KEYS)
    gereatedKeys <- statement.getGeneratedKeys()
  yield
}
```

### Client/Server PreparedStatement

LDBC provides `PreparedStatement` divided into `Client PreparedStatement` and `Server PreparedStatement`.

`Client PreparedStatement` is an API for constructing SQL on the application using dynamic parameters and sending it to the MySQL server.
Therefore, the method of sending queries to the MySQL server is the same as for `Statement`.

This API is equivalent to JDBC's `PreparedStatement`.

A `PreparedStatement` for building queries in a more secure MySQL server is provided in the `Server PreparedStatement`, so please use that.

`Server PreparedStatement` is an API that prepares the query to be executed in advance in the MySQL server and executes it by setting parameters in the application.

The `Server PreparedStatement` allows reuse of queries, since the query to be executed and the parameters are sent separately.

When using `Server PreparedStatement`, the query is prepared in advance by the MySQL server. Although the MySQL server uses memory to store them, the queries can be reused, which improves performance.

However, there is a risk of memory leaks because the pre-prepared query will continue to use memory until it is freed.

If you use `Server PreparedStatement`, you must use the `close` method to properly release the query.

#### Client PreparedStatement

Construct a `Client PreparedStatement` using the `ClientPreparedStatement` method of `Connection`.

```scala
connection.use { conn =>
  for 
    statement <- conn.clientPreparedStatement("SELECT * FROM users WHERE id = ?")
    ...
  yield ...
}
```

#### Server PreparedStatement

Construct a `Server PreparedStatement` using the `Connection` `serverPreparedStatement` method.

```scala
connection.use { conn =>
  for 
    statement <- conn.serverPreparedStatement("SELECT * FROM users WHERE id = ?")
    ...
  yield ...
}
```

#### Read query

Use the `executeQuery` method to execute read-only SQL.

The values returned by the MySQL server as a result of executing the query are stored in a `ResultSet` and returned as the return value.

```scala
connection.use { conn =>
  for 
    statement <- conn.clientPreparedStatement("SELECT * FROM users WHERE id = ?") // or conn.serverPreparedStatement("SELECT * FROM users WHERE id = ?")
    _ <- statement.setLong(1, 1)
    result <- statement.executeQuery()
  yield
    // Processing with ResultSet
}
```

If you want to use dynamic parameters, use the `setXXX` method to set the parameters.
The `setXXX` method can also use the `Option` type. If `None` is passed, the parameter will be set to NULL.

The `setXXX` method specifies the index of the parameter and the value of the parameter.

```scala
statement.setLong(1, 1)
```

The following methods are supported in the current version

| Method        | Type                                | Note                                               |
|---------------|:------------------------------------|----------------------------------------------------|
| setNull       |                                     | Set the parameter to NULL                          |
| setBoolean    | Boolean/Option[Boolean]             |                                                    |
| setByte       | Byte/Option[Byte]                   |                                                    |
| setShort      | Short/Option[Short]                 |                                                    |
| setInt        | Int/Option[Int]                     |                                                    |
| setLong       | Long/Option[Long]                   |                                                    |
| setBigInt     | BigInt/Option[BigInt]               |                                                    |
| setFloat      | Float/Option[Float]                 |                                                    |
| setDouble     | Double/Option[Double]               |                                                    |
| setBigDecimal | BigDecimal/Option[BigDecimal]       |                                                    |
| setString     | String/Option[String]               |                                                    |
| setBytes      | Array[Byte]/Option[Array[Byte]]     |                                                    |
| setDate       | LocalDate/Option[LocalDate]         | Directly handle `java.time` instead of `java.sql`. |
| setTime       | LocalTime/Option[LocalTime]         | Directly handle `java.time` instead of `java.sql`. |
| setTimestamp  | LocalDateTime/Option[LocalDateTime] | Directly handle `java.time` instead of `java.sql`. |
| setYear       | Year/Option[Year]                   | Directly handle `java.time` instead of `java.sql`. |

#### Write Query

Use the `executeUpdate` method to execute the SQL to be written.

The value returned by the MySQL server as a result of executing the query is the number of rows affected.

```scala
connection.use { conn =>
  for 
    statement <- conn.clientPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)") // or conn.serverPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)")
    _ <- statement.setString(1, "Alice")
    _ <- statement.setInt(2, 20)
    result <- statement.executeUpdate()
  yield result
}

```

#### Get the value of AUTO_INCREMENT

Use the `getGeneratedKeys` method to retrieve the value of AUTO_INCREMENT after executing the query.

The value returned by the MySQL server as a result of executing the query will be the value generated for AUTO_INCREMENT as the return value.

```scala
connection.use { conn =>
  for
    statement <- conn.clientPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS) // or conn.serverPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)", Statement.RETURN_GENERATED_KEYS)
    _ <- statement.setString(1, "Alice")
    _ <- statement.setInt(2, 20)
    _ <- statement.executeUpdate()
    getGeneratedKeys <- statement.getGeneratedKeys()
  yield getGeneratedKeys
}
```

### ResultSet

The `ResultSet` is an API for storing values returned by the MySQL server after query execution.

There are two ways to retrieve records retrieved by executing SQL from a `ResultSet`: using the `next` and `getXXX` methods as in JDBC, or using LDBC's own `decode` method.

#### next/getXXX

The `next` method returns `true` if the next record exists, or `false` if the next record does not exist.

The `getXXX` method is an API for retrieving values from a record.

The `getXXX` method can be used either by specifying the index of the column to be retrieved or by specifying the column name.

```scala
connection.use { conn =>
  for
    statement <- conn.clientPreparedStatement("SELECT `id`, `name`, `age` FROM users WHERE id = ?")
    _ <- statement.setLong(1, 1)
    result <- statement.executeQuery()
    records <- Monad[IO].whileM(result.next()) {
      for
        id <- result.getLong(1)
        name <- result.getString("name")
        age <- result.getInt(3)
      yield (id, name, age)
    }
  yield records
}
```

#### decode

The `decode` method is used to retrieve records from the `ResultSet` after they have been retrieved by executing SQL.

The `decode` method is an API for converting values retrieved from `ResultSet` to Scala types.

The type to be converted is specified using the `*:` operator depending on the number of columns to be retrieved.

The example shows how to retrieve the id, name, and age columns of the users table, specifying the type of each column.

```scala
result.decode(bigint *: varchar *: int.opt)
```

If you want to get a NULL-allowed column, use the `opt` method to convert it to the `Option` type.
If the record is NULL, it can be retrieved as None.

The sequence of events from query execution to record retrieval is as follows

```scala
connection.use { conn =>
  for
    statement <- conn.clientPreparedStatement("SELECT * FROM users WHERE id = ?") // or conn.serverPreparedStatement("SELECT * FROM users WHERE id = ?")
    _ <- statement.setLong(1, 1)
    result <- statement.executeQuery()
    decodes <- result.decode(bigint *: varchar *: int.opt)
  yield decodes
}
```

The records retrieved from a `ResultSet` will always be an array.
This is because a query in MySQL may always return multiple records.

If you want to retrieve a single record, use the `head` or `headOption` method after the `decode` process.

The following data types are supported in the current version

| Codec       | Data Type         | Scala Type     |
|-------------|-------------------|----------------|
| boolean     | BOOLEAN           | Boolean        |
| tinyint     | TINYINT           | Byte           |
| utinyint    | unsigned TINYINT  | Short          |
| smallint    | SMALLINT          | Short          |
| usmallint   | unsigned SMALLINT | Int            |
| int         | INT               | Int            |
| uint        | unsigned INT      | Long           |
| bigint      | BIGINT            | Long           |
| ubigint     | unsigned BIGINT   | BigInt         |
| float       | FLOAT             | Float          |
| double      | DOUBLE            | Double         |
| decimal     | DECIMAL           | BigDecimal     |
| char        | CHAR              | String         |
| varchar     | VARCHAR           | String         |
| binary      | BINARY            | Array[Byte]    |
| varbinary   | VARBINARY         | String         |
| tinyblob    | TINYBLOB          | String         |
| blob        | BLOB              | String         |
| mediumblob  | MEDIUMBLOB        | String         |
| longblob    | LONGBLOB          | String         |
| tinytext    | TINYTEXT          | String         |
| text        | TEXT              | String         |
| mediumtext  | MEDIUMTEXT        | String         |
| longtext    | LONGTEXT          | String         |
| enum        | ENUM              | String         |
| set         | SET               | List[String]   |
| json        | JSON              | String         |
| date        | DATE              | LocalDate      |
| time        | TIME              | LocalTime      |
| timetz      | TIME              | OffsetTime     |
| datetime    | DATETIME          | LocalDateTime  |
| timestamp   | TIMESTAMP         | LocalDateTime  |
| timestamptz | TIMESTAMP         | OffsetDateTime |
| year        | YEAR              | Year           |

※ Currently, it is designed to retrieve values by specifying the MySQL data type, but in the future it may be changed to a more concise Scala type to retrieve values.

The following data types are not supported

- GEOMETRY
- POINT
- LINESTRING
- POLYGON
- MULTIPOINT
- MULTILINESTRING
- MULTIPOLYGON
- GEOMETRYCOLLECTION

## Transaction

To execute a transaction using `Connection`, use the `setAutoCommit` method in combination with the `commit` and `rollback` methods.

First, use the `setAutoCommit` method to disable transaction autocommit.

```scala
conn.setAutoCommit(false)
```

Use the `commit` method to commit the transaction after some processing.

```scala
for
  statement <- conn.clientPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)")
  _ <- statement.setString(1, "Alice")
  _ <- statement.setInt(2, 20)
  result <- statement.executeUpdate()
  _ <- conn.commit()
yield
```

Or use the `rollback` method to roll back the transaction.

```scala
for
  statement <- conn.clientPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)")
  _ <- statement.setString(1, "Alice")
  _ <- statement.setInt(2, 20)
  result <- statement.executeUpdate()
  _ <- conn.rollback()
yield
```

If transaction autocommit is disabled using the `setAutoCommit` method, rollback will occur automatically when the connection's Resource is released.

### Transaction isolation level

LDBC allows for the setting of transaction isolation levels.

The transaction isolation level is set using the `setTransactionIsolation` method.

The following transaction isolation levels are supported in MySQL.

- READ UNCOMMITTED
- READ COMMITTED
- REPEATABLE READ
- SERIALIZABLE

See [official documentation](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html) for more information on transaction isolation levels in MySQL.

```scala
import ldbc.connector.Connection.TransactionIsolationLevel

conn.setTransactionIsolation(TransactionIsolationLevel.REPEATABLE_READ)
```

Use the `getTransactionIsolation` method to get the currently set transaction isolation level.

```scala
for
  isolationLevel <- conn.getTransactionIsolation()
yield
```

### Savepoint

For more advanced transaction management, the "Savepoint feature" can be used. This allows you to mark a specific point during a database operation so that if something goes wrong, you can rewind the database state back to that point. This is especially useful for complex database operations or when you need to set a safe point in a long transaction.

**Features：**

- Flexible Transaction Management: Use Savepoint to create a "checkpoint" anywhere within a transaction. State can be returned to that point as needed.
- Error Recovery: Save time and increase efficiency by going back to the last safe Savepoint when an error occurs, rather than starting all over.
- Advanced Control: Multiple Savepoints can be configured for more precise transaction control. Developers can easily implement more complex logic and error handling.

By taking advantage of this feature, your application will be able to achieve more robust and reliable database operations.

**Savepoint Settings**

To set a Savepoint, use the `setSavepoint` method. This method allows you to specify a name for the Savepoint.
If you do not specify a name for the Savepoint, the value generated by the UUID will be set as the default name.

The `getSavepointName` method can be used to retrieve the name of the configured Savepoint.

※ Since autocommit is enabled by default in MySQL, it is necessary to disable autocommit when using Savepoint. Otherwise, all operations will be committed each time, and it will not be possible to roll back transactions using Savepoint.

```scala
for
  _ <- conn.setAutoCommit(false)
  savepoint <- conn.setSavepoint("savepoint1")
yield savepoint.getSavepointName
```

**Rollback of Savepoint**

To rollback a part of a transaction using Savepoint, rollback is performed by passing Savepoint to the `rollback` method.
If you commit the entire transaction after a partial rollback using Savepoint, the transaction after that Savepoint will not be committed.

```scala
for
  _ <- conn.setAutoCommit(false)
  savepoint <- conn.setSavepoint("savepoint1")
  _ <- conn.rollback(savepoint)
  _ <- conn.commit()
yield
```

**Savepoint Release**

To release a Savepoint, pass the Savepoint to the `releaseSavepoint` method.
After releasing a Savepoint, commit the entire transaction and the transactions after that Savepoint will be committed.

```scala
for
  _ <- conn.setAutoCommit(false)
  savepoint <- conn.setSavepoint("savepoint1")
  _ <- conn.releaseSavepoint(savepoint)
  _ <- conn.commit()
yield
```
## Utility Commands

MySQL has several utility commands. ([reference](https://dev.mysql.com/doc/dev/mysql-server/latest/page_protocol_command_phase_utility.html))

LDBC provides an API for using these commands.

| Command              | Use                                                                | Support |
|----------------------|--------------------------------------------------------------------|---------|
| COM_QUIT             | Tells the server that the client wants it to close the connection. | ✅       |
| COM_INIT_DB          | Change the default schema of the connection                        | ✅       |
| COM_STATISTICS       | Get a human readable string of some internal status vars.          | ✅       |
| COM_DEBUG            | Dump debug info to server's stdout                                 | ❌       |
| COM_PING             | Check if the server is alive                                       | ✅       |
| COM_CHANGE_USER      | Changes the user of the current connection.                        | ✅       |
| COM_RESET_CONNECTION | Resets the session state                                           | ✅       |
| COM_SET_OPTION       | Sets options for the current connection                            | ✅       |

### COM_QUIT

The `COM_QUIT` command is used to tell the server that the client is requesting that the connection be closed.

In LDBC, the `close` method of `Connection` can be used to close a connection.
Using the `close` method closes the connection, so the connection cannot be used in any subsequent process.

※ Connection` uses `Resource` to manage resources. Therefore, there is no need to use the `close` method to release resources.

```scala
connection.use { conn =>
  conn.close()
}
```

### COM_INIT_DB

`COM_INIT_DB` is a command to change the default schema for a connection.

In LDBC, the default schema can be changed using the `setSchema` method of `Connection`.

```scala
connection.use { conn =>
  conn.setSchema("test")
}
```

### COM_STATISTICS

The `COM_STATISTICS` command is used to retrieve internal status strings in readable format.

In LDBC, you can use the `getStatistics` method of `Connection` to get the internal status string.

```scala
connection.use { conn =>
  conn.getStatistics
}
```

The statuses that can be obtained are as follows

- `uptime` : the time since the server was started
- `threads` : number of clients currently connected.
- `questions` : number of queries since the server started
- `slowQueries` : number of slow queries.
- `opens` : number of table opens since the server started.
- `flushTables` : number of tables flushed since the server started.
- `openTables` : number of tables currently open.
- `queriesPerSecondAvg` : average number of queries per second.

### COM_PING

The `COM_PING` command is used to check if the server is alive.

In LDBC, you can check if the server is alive using the `isValid` method of `Connection`.
It returns `true` if the server is alive, or `false` if not.

```scala
connection.use { conn =>
  conn.isValid
}
```

### COM_CHANGE_USER

The `COM_CHANGE_USER` command is used to change the user of the current connection.
It also resets the following connection states

- User Variables
- Temporary tables
- Prepared statements
- etc...

LDBC allows changing the user using the `changeUser` method of `Connection`.

```scala
connection.use { conn =>
  conn.changeUser("root", "password")
}
```

### COM_RESET_CONNECTION

`COM_RESET_CONNECTION` is a command to reset the session state.

`COM_RESET_CONNECTION` is a more lightweight version of `COM_CHANGE_USER`, with almost the same functionality to clean up the session state, but with the following features

- No re-authentication (no extra client/server exchange to do so).
- Does not close connections.

LDBC allows you to reset the session state using the `resetServerState` method of `Connection`.

```scala
connection.use { conn =>
  conn.resetServerState
}
```

### COM_SET_OPTION

`COM_SET_OPTION` is a command to set options for the current connection.

LDBC allows you to set options using the `enableMultiQueries` and `disableMultiQueries` methods of `Connection`.

The `enableMultiQueries` method allows multiple queries to be executed at once.
If you use the `disableMultiQueries` method, you will not be able to run multiple queries at once.

It can only be used for batch processing with Insert, Update, and Delete statements; if used with a Select statement, only the results of the first query will be returned.

```scala
connection.use { conn =>
  conn.enableMultiQueries *> conn.disableMultiQueries
}
```

## Batch commands

LDBC allows multiple queries to be executed at once using batch commands.
Using batch commands allows multiple queries to be executed at once, reducing the number of network round trips.

To use batch commands, add a query using the `addBatch` method of the `Statement` or `PreparedStatement` and execute the query using the `executeBatch` method.

```scala 3
connection.use { conn =>
  for
    statement <- conn.createStatement()
    _ <- statement.addBatch("INSERT INTO users (name, age) VALUES ('Alice', 20)")
    _ <- statement.addBatch("INSERT INTO users (name, age) VALUES ('Bob', 30)")
    result <- statement.executeBatch()
  yield result
}
```

In the above example, data for `Alice` and `Bob` can be added at once.
The query to be executed would be as follows

```sql
INSERT INTO users (name, age) VALUES ('Alice', 20);INSERT INTO users (name, age) VALUES ('Bob', 30);
```

The return value after executing a batch command is an array of the number of rows affected by each query executed.

In the above example, one row of data for `Alice` is added and one row of data for `Bob` is added, so the return value is `List(1, 1)`.

After executing the batch command, the queries that have been added so far by the `addBatch` method will be cleared.

If you want to clear them manually, use the `clearBatch` method to do so.

Translated with www.DeepL.com/Translator (free version)

```scala
connection.use { conn =>
  for
    statement <- conn.createStatement()
    _ <- statement.addBatch("INSERT INTO users (name, age) VALUES ('Alice', 20)")
    _ <- statement.clearBatch()
    _ <- statement.addBatch("INSERT INTO users (name, age) VALUES ('Bob', 30)")
    _ <- statement.executeBatch()
  yield
}
```

In the above example, the data for `Alice` is not added, but the data for `Bob` is.

### Difference between Statement and PreparedStatement

The queries executed by the batch command may differ between a `Statement` and a `PreparedStatement`.

When an INSERT statement is executed in a batch command using a `Statement`, multiple queries are executed at once.
However, if you run an INSERT statement in a batch command using a `PreparedStatement`, a single query will be executed.

For example, if you run the following query in a batch command, multiple queries will be executed at once because you are using a `Statement`.

Translated with www.DeepL.com/Translator (free version)

```scala
connection.use { conn =>
  for
    statement <- conn.createStatement()
    _ <- statement.addBatch("INSERT INTO users (name, age) VALUES ('Alice', 20)")
    _ <- statement.addBatch("INSERT INTO users (name, age) VALUES ('Bob', 30)")
    result <- statement.executeBatch()
  yield result
}

// Query to be executed
// INSERT INTO users (name, age) VALUES ('Alice', 20);INSERT INTO users (name, age) VALUES ('Bob', 30);
```

However, if the following query is executed in a batch command, one query will be executed because of the use of `PreparedStatement`.

```scala
connection.use { conn =>
  for
    statement <- conn.clientPreparedStatement("INSERT INTO users (name, age) VALUES (?, ?)")
    _ <- statement.setString(1, "Alice")
    _ <- statement.setInt(2, 20)
    _ <- statement.addBatch()
    _ <- statement.setString(1, "Bob")
    _ <- statement.setInt(2, 30)
    _ <- statement.addBatch()
    result <- statement.executeBatch()
  yield result
}

// Query to be executed
// INSERT INTO users (name, age) VALUES ('Alice', 20), ('Bob', 30);
```

This is because if you are using `PreparedStatement`, you can set multiple parameters for a single query by using the `addBatch` method after setting the query parameters.

## Stored Procedure Execution

LDBC provides an API for executing stored procedures.

To execute a stored procedure, use the `prepareCall` method of `Connection` to construct a `CallableStatement`.

※ The stored procedures used are those described in the [official](https://dev.mysql.com/doc/connector-j/en/connector-j-usagenotes-statements-callable.html) document.

```sql
CREATE PROCEDURE demoSp(IN inputParam VARCHAR(255), INOUT inOutParam INT)
BEGIN
    DECLARE z INT;
    SET z = inOutParam + 1;
    SET inOutParam = z;

    SELECT inputParam;

    SELECT CONCAT('zyxw', inputParam);
END
```

To execute the above stored procedure, the following would be used

```scala
connection.use { conn =>
  for
    callableStatement <- conn.prepareCall("CALL demoSp(?, ?)")
    _ <- callableStatement.setString(1, "abcdefg")
    _ <- callableStatement.setInt(2, 1)
    hasResult <- callableStatement.execute()
    values <- Monad[IO].whileM[List, Option[String]](callableStatement.getMoreResults()) {
      for
        resultSet <- callableStatement.getResultSet().flatMap {
          case Some(rs) => IO.pure(rs)
          case None     => IO.raiseError(new Exception("No result set"))
        }
        value <- resultSet.getString(1)
      yield value
    }
  yield values // List(Some("abcdefg"), Some("zyxwabcdefg"))
}
```

To get the value of an output parameter (a parameter you specified as OUT or INOUT when you created the stored procedure), in JDBC you must use the various `registerOutputParameter()` methods of the CallableStatement interface to specify parameters before statement execution, while LDBC will also set parameters during query execution by simply setting them using the `setXXX` method.

However, LDBC also allows you to specify parameters using the `registerOutputParameter()` method.

```scala
connection.use { conn =>
  for
    callableStatement <- conn.prepareCall("CALL demoSp(?, ?)")
    _ <- callableStatement.setString(1, "abcdefg")
    _ <- callableStatement.setInt(2, 1)
    _ <- callableStatement.registerOutParameter(2, ldbc.connector.data.Types.INTEGER)
    hasResult <- callableStatement.execute()
    value <- callableStatement.getInt(2)
  yield value // 2
}
```

※ Note that if you specify an Out parameter with `registerOutParameter`, the value will be set at `Null` for the server if the parameter is not set with the `setXXX` method using the same index value.

## Unsupported Feature

The LDBC connector is currently an experimental feature. Therefore, the following features are not supported.
We plan to provide the features as they become available.

- Connection Pooling
- Failover measures
- etc...
