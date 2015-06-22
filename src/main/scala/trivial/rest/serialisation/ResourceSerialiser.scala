package trivial.rest.serialisation

import org.json4s.JsonAST.JString
import org.json4s._
import org.json4s.reflect.TypeInfo

import scala.reflect.ClassTag

// TODO - CAS - 06/05/15 - Just import Mange? Or copy verbatim, so there are fewer dependencies?
case class ResourceSerialiser[T: ClassTag](serialise: T ⇒ String, deserialise: String ⇒ Option[T]) extends Serializer[T] {
  private val TheClass       = implicitly[ClassTag[T]].runtimeClass
  private val serialiserName = TheClass.getSimpleName

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
    case (TypeInfo(TheClass, _), JString(value)) ⇒ deserialise(value).getOrElse(failHard(value))
  }

  private def failHard(value: Any) =
    throw new MappingException(s"Can't convert [$value] to an instance of $serialiserName.")

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: T ⇒ JString(serialise(x))
  }
}