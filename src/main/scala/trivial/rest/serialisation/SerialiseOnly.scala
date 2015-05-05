package trivial.rest.serialisation

import org.json4s.JsonAST.JString
import org.json4s._
import org.json4s.reflect.TypeInfo

import scala.reflect.ClassTag

case class SerialiseOnly[T: ClassTag](serialise: T ⇒ String) extends Serializer[T] {

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] =
    PartialFunction.empty[(TypeInfo, JValue), T]

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: T ⇒ JString(serialise(x))
  }
}