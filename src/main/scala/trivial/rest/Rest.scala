package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import com.twitter.finatra.{Controller, Request}
import org.json4s._
import org.json4s.native.Serialization
import trivial.rest.persistence.Persister
import trivial.rest.validation.{RestRulesValidator, Validator}

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Trivial REST:
 * (1) Declare a case class
 * (2) Register it as a Resource, specifying the allowed HTTP methods
 * (3) You get a truly RESTful API, your allowed HTTP methods, and persistence, and caching, for free.
 * 
 * Concepts to explore:
 *   Case classes as a schema for JSON
 *   Multiple versions of a case class supported at the same time (Record, Record2, etc), based on cascading support
 */
class Rest(controller: Controller, uriRoot: String, persister: Persister, validator: Validator = new RestRulesValidator) {
  private val resources = ListBuffer[String]()
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"
  
  import controller._
  
  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  val serialiser = DefaultJacksonJsonSerializer

  def resource[T <: Restable with AnyRef : ClassTag](supportedMethods: HttpMethod*)(implicit mf: scala.reflect.Manifest[T]) = {
    def resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources.append(resourceName)

    supportedMethods.foreach {
      case GetAll => addGetAll(resourceName)
      case Post => addPost(resourceName)(mf)
      // case Get => /:resourceName/:id
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }
    
    val unsupportedMethods: Set[HttpMethod] = HttpMethod.all.diff(supportedMethods.toSet)
    def unsupport(method: HttpMethod) = (request: Request) => render.status(405)
      .plain(s"Method not allowed: $method. Methods supported by /$resourceName are: ${supportedMethods.mkString(", ")}").toFuture
    
    unsupportedMethods foreach {
      case GetAll => get(pathTo(resourceName)) {unsupport(GetAll)}
      case Post => post(pathTo(resourceName)) {unsupport(Post)}
      case Get => // get(pathTo(resourceName)) { unsupport }
      case Put => put(pathTo(resourceName)) {unsupport(Put)}
      case Delete => delete(pathTo(resourceName)) {unsupport(Delete)}
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }
    
    this
  }
  
  def addPost[T <: Restable with AnyRef](resourceName: String)(implicit mf: scala.reflect.Manifest[T]): Unit = {
    def deserialise(body: String): Either[Failure, Seq[T]] =
      try {
        Right(Serialization.read[Seq[T]](body))
      } catch {
        case e: Exception => Left(Failure(500, s"Failed to deserialise into $resourceName, due to: $e"))
      }
    
    post(pathTo(resourceName)) { request =>
      // TODO - CAS - 27/04/15 - Yes, this will become a for-comp, but only when I have worked out all the bits
      
      val persisted = try {
        val deserialisedT: Either[Failure, Seq[T]] = deserialise(request.getContentString())
        val validatedT: Either[Failure, Seq[T]] = deserialisedT.right.flatMap(validator.validate)
        val copied: Either[Failure, Seq[Restable]] = validatedT.right.map(_.map(_.withId(s"${persister.nextSequenceNumber}")))

        val stringOffDisk: Either[Failure, Array[Byte]] = persister.loadAll(resourceName)
        val allPrevious: Either[Failure, Seq[T]] = stringOffDisk.right.flatMap(bytes => deserialise(new String(bytes.map(_.toChar))))
        val appended: Either[Failure, Seq[Restable]] = allPrevious.right.flatMap(st => copied.right.map(moreSt => st ++ moreSt))

        val serialised: Either[Failure, String] = appended.right.map(t => Serialization.write(t))
        serialised.right.flatMap(pj => persister.save(resourceName, pj))
      } catch {
        case e: Exception => Left(Failure(500, s"It went horribly wrong: $e"))
      }

      persisted match {
        case Right(bytes)  => render.body(bytes).contentType(utf8Json).toFuture
        case Left(failure) => render.status(failure.statusCode).plain(failure.reason).toFuture
      }
      
      // TODO - CAS - 22/04/15 - Rebuild cache of T
    }
  }

  def pathTo(resourceName: String) = s"${uriRoot.stripSuffix("/")}/$resourceName"

  def addGetAll(resourceName: String): Unit = {
    def loadAll(request: Request) =
      persister.loadAll(resourceName) match {
        case Right(bytes) => render.body(bytes).contentType(utf8Json).toFuture
        case Left(failure) => render.status(failure.statusCode).plain(failure.reason).toFuture
      }

    // TODO - CAS - 20/04/15 - Remove support for the suffixed URI
    get(s"${uriRoot.stripSuffix("/")}/$resourceName.json") { loadAll }
    get(pathTo(resourceName)) { loadAll }
  }

  get(s"${uriRoot.stripSuffix("/")}/:unsupportedResourceName") { request =>
    val unsupportedResource = request.routeParams("unsupportedResourceName")
    render.status(404).plain(s"Resource type not supported: $unsupportedResource").toFuture
  }

  get(uriRoot) { request =>
    render.body(serialiser.serialize(resources.sorted[String])).contentType(utf8Json).toFuture
  }
}