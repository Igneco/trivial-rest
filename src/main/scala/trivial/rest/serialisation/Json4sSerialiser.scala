package trivial.rest.serialisation

import org.json4s.JsonAST.JValue
import org.json4s._
import org.json4s.native.{JsonParser, Serialization}
import trivial.rest._

import scala.collection.mutable
import scala.reflect.ClassTag

class Json4sSerialiser extends Serialiser {

  type JsonRepresentation = JValue

  private val resourceSerialisers = mutable.Map.empty[Class[_], TypeSerialiser[_]]
  private val fieldDefaults = mutable.Map.empty[Class[_], JValue]
  private val typeSerialisers = mutable.ListBuffer[TypeSerialiser[_]]()

  override def registerResource[T <: Resource[T] : ClassTag](allTheTs: Formats => Either[Failure, Seq[T]]) = {
    // TODO - CAS - 15/06/15 - Push the ResourceSerialiser back into the client, so that we don't need to know anything about Resources here
    val serialiser = TypeSerialiser[T](_.id.getOrElse(""), { case id: String => hunt(allTheTs(formatsExcept[T]), id) })
    resourceSerialisers += Classy.runtimeClass[T] -> serialiser
    this
  }

  override def withTypeSerialiser[T](typeSerialiser: TypeSerialiser[T]): Serialiser = {
    typeSerialisers += typeSerialiser
    this
  }

  // TODO - CAS - 11/05/15 - memoize
  override implicit def formatsExcept[T : ClassTag]: Formats =
    Serialization.formats(NoTypeHints) ++ typeSerialisers ++ (resourceSerialisers - Classy.runtimeClass[T]).values

  override def withDefaultFields[T : ClassTag](defaultObject: T): Json4sSerialiser = {
    val jValue: JValue = Extraction.decompose(defaultObject)
    fieldDefaults += Classy.runtimeClass[T] -> jValue
    this
  }

  // TODO - CAS - 07/05/15 - Switch this to persister.getById, once we have /get/:id enabled
  def hunt[T <: Resource[T]](allTheTs: => Either[Failure, Seq[T]], id: String): Option[T] = allTheTs match {
    case Right(seqTs) => seqTs.find(_.id == Some(id))
    case Left(failure) => None
  }

  /*

  TODO - CAS - 01/05/15 - Map these to better error messages

  (1)
  // The extract[] method doesn't know the type of T, probably because it can't infer it.
  // Therefore, the constructor is a big fat Nothing. Pass in the type explicitly. This is
  // trying to create a Seq[Nothing], so it is trying to build Nothings and failing. Fixed
  // by calling loadAll[T](param) instead of loadAll(param)

  two: Left(Failure(500,Failed to deserialise into foo, due to: org.json4s.package$MappingException: Parsed JSON values do not match with class constructor
  args=
  arg types=
  constructor=public scala.runtime.Nothing$()))

  (2)
  // The "currency" field on the case class cannot be completed, because there is no "currency" value in the JSON string.

  Left(Failure(500,Failed to deserialise into exchangerate, due to: org.json4s.package$MappingException: No usable value for currency
  No usable value for isoName
  Did not find value which can be converted into java.lang.String

  (3)
  // Extract is looking for a field name in the target case class, to match the value
  // in the JSON. The field isn't there, or has a different name.

  Caused by: org.json4s.package$MappingException: Did not find value which can be converted into java.lang.String

  TODO - CAS - 01/05/15 - Try parsing the JSON AST, and showing that, for MappingException, which is about converting AST -> T
  */
  override def deserialise[T : Manifest](body: String): Either[Failure, Seq[T]] = deserialiseToType(deserialiseToJson(body))

  def deserialiseToType[T : Manifest](json: JValue): Either[Failure, Seq[T]] =
    try {
      val defaultValues: JValue = fieldDefaults.getOrElse(Classy.runtimeClass[T], JObject())

      json match {
        case JArray(resources) => Right(JArray(resources.map(defaultValues merge _)).extract[Seq[T]])
        case jObject: JObject => Right(Seq((defaultValues merge jObject).extract[T]))
        case JNothing => Right(Seq.empty)
        case other => Left(FailFactory.unexpectedJson(json))
      }
    } catch {
      case m: MappingException => Left(Failure(500, ExceptionDecoder.huntCause(m, Seq.empty[String])))
      case e: Exception => Left(FailFactory.deserialisation(e))
    }

  def deserialiseToJson[T: Manifest](body: String): JValue = JsonParser.parse(body)

  override def emptyJson = JNothing
}