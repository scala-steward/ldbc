/** This file is part of the ldbc. For the full copyright and license information, please view the LICENSE file that was
  * distributed with this source code.
  */

package ldbc.core

/** A model representing SQL database information.
  */
trait Database:

  /** Database Type */
  val databaseType: Database.Type

  /** Database Name */
  val name: String

  /** Database Schema */
  val schema: String

  /** Database Schema Meta Information */
  val schemaMeta: Option[String]

  /** Database Catalog */
  val catalog: Option[String]

  /** A value to represent the character set. */
  val character: Option[Character]

  /** A value to represent the collation. */
  val collate: Option[Collate]

  /** Connection host to database */
  val host: String

  /** Connection port to database */
  val port: Int

  /** List of Tables in Database */
  val tables: Set[Table[?]]

object Database:

  /** Enum representing the database type, only databases that are already supported by the library will be managed.
    */
  enum Type(val driver: String):
    case MySQL    extends Type("com.mysql.cj.jdbc.Driver")
    case AWSMySQL extends Type("software.aws.rds.jdbc.mysql.Driver")
