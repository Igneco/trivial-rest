package trivial.rest.serialisation

import org.json4s.JsonAST.JString
import org.json4s._
import org.json4s.reflect.TypeInfo
import trivial.rest.Classy

import scala.reflect.ClassTag

case class TypeSerialiser[T: ClassTag](serialise: T ⇒ String, deserialise: Any ⇒ Option[T]) extends Serializer[T] {
  private val TheClass       = implicitly[ClassTag[T]].runtimeClass
  private val serialiserName = TheClass.getSimpleName

  def deserialize(implicit format: Formats): PartialFunction[(TypeInfo, JValue), T] = {
    case (TypeInfo(TheClass, _), JString(value)) ⇒ deserialise(value).getOrElse(failHard(value))
  }

  private def failHard(value: Any) =
    throw new MappingException(s"Can't convert [$value] to an instance of $serialiserName.")

  def serialize(implicit format: Formats): PartialFunction[Any, JValue] = {
    case x: T ⇒ JString(serialise(x))
    // Pattern matching on type T will not recognise Boolean values, as they are not backed
    // by a class at Runtime. They are just Java booleans.
    case b: Boolean if Classy.runtimeClass[T] == classOf[Boolean] ⇒ JString(serialise(b.asInstanceOf[T]))
  }
}