/** This file is part of the ldbc. For the full copyright and license information, please view the LICENSE file that was
  * distributed with this source code.
  */

package ldbc.core.attribute

/** Model for specifying an additional attribute AUTO_INCREMENT for DataType.
  */
private[ldbc] case class AutoInc[T <: Byte | Short | Int | Long | BigInt | Option[Byte | Short | Int | Long | BigInt]]()
  extends Attribute[T]:

  override def queryString: String = "AUTO_INCREMENT"

  override def toString: String = queryString
