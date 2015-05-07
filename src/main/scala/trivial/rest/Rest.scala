package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import com.twitter.finatra.{Controller, Request, ResponseBuilder}
import com.twitter.util.Future
import org.json4s._
import org.json4s.native.Serialization
import trivial.rest.configuration.Config
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.{SerialiserExceptionHelper, ResourceSerialiser}
import trivial.rest.validation.{RestRulesValidator, Validator}

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * Trivial REST:
 * (1) Declare a case class
 * (2) Register it as a Resource, specifying the allowed HTTP methods
 * (3) You get a truly RESTful API, your allowed HTTP methods, and persistence, and caching, for free.
 * 
 * Concepts to explore:
 *   Case classes as a schema for JSON
 *   Postel's Law / The Robustness Principle - http://en.wikipedia.org/wiki/Robustness_principle
 *   Multiple versions of a case class supported at the same time (Record, Record2, etc), based on cascading support
 */
class Rest(uriRoot: String, controller: Controller, persister: Persister, validator: Validator = new RestRulesValidator, config: Config = new Config) {
  private val resourceToSerialiser = mutable.Map.empty[String, ResourceSerialiser[_]]
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"
  
  import controller._
  
  def formatsFor(resourceName: String) =
    if (config.flattenNestedResources)
      Serialization.formats(NoTypeHints) ++ (resourceToSerialiser - resourceName).values
    else
      Serialization.formats(NoTypeHints)

  def resource[T <: Resource[T] with AnyRef : ClassTag : Manifest](supportedMethods: HttpMethod*) = {
    lazy val resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase
    
    // TODO - CAS - 07/05/15 - Needs to be pushed down to a callback, otherwise not all formats will be loaded
    implicit val formats: Formats = formatsFor(resourceName)

    resourceToSerialiser += (resourceName -> ResourceSerialiser[T](_.id.getOrElse(""), id => hunt(resourceName, id)))

    // TODO - CAS - 07/05/15 - Switch this to persister.getById, once we have /get/:id enabled
    def hunt[T <: Resource[T] : Manifest](resourceName: String, id: String): Option[T] = persister.loadAll[T](resourceName) match {
      case Right(seqTs) => seqTs.find(_.id == Some(id))
      case Left(failure) => None
    }

    supportedMethods.foreach {
      case GetAll => addGetAll(resourceName)
      case Post => addPost(resourceName)
      // case Get => /:resourceName/:id
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }

    val unsupportedMethods: Set[HttpMethod] = HttpMethod.all.diff(supportedMethods.toSet)

    def unsupportedError(httpMethod: HttpMethod) =
      s"Method not allowed: $httpMethod. Methods supported " +
        s"by /$resourceName are: ${supportedMethods.mkString(", ")}"

    type ControllerFunction = (String) => ((Request) => Future[ResponseBuilder]) => Unit

    // TODO - CAS - 03/05/15 - add a mapping from HttpMethod to controller function, as the first stage of abstracting the Controller
    def unsupport(f: ControllerFunction, httpMethod: HttpMethod) = {
      f(pathTo(resourceName)) { request =>
        render.status(405).plain(unsupportedError(httpMethod)).toFuture
      }
    }

    unsupportedMethods foreach {
      case GetAll => unsupport(get, GetAll)
      case Post => unsupport(post, Post)
      case Get => // get(pathTo(resourceName)) { unsupport } // us2(get, Get, ":idParam")
      case Put => unsupport(put, Put)
      case Delete => unsupport(delete, Delete)
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }
    
    this
  }
  
  def addPost[T <: Resource[T] with AnyRef : Manifest](resourceName: String): Unit = {
    // TODO - CAS - 01/05/15 - This and the copy in Persister -> put into a Serialiser dependency
    def deserialise(body: String): Either[Failure, Seq[T]] =
      try {
        implicit val formats: Formats = formatsFor(resourceName)
        Right(Serialization.read[Seq[T]](body))
      } catch {
        case m: MappingException => Left(Failure(500, SerialiserExceptionHelper.huntCause(m, Seq.empty[String])))
        case e: Exception => Left(Failure(500, s"THE ONE IN REST ===> Failed to deserialise into $resourceName, due to: $e"))
      }
    
    post(pathTo(resourceName)) { request =>
      // TODO - CAS - 27/04/15 - Yes, this will become a for-comp, but only when I have worked out all the bits

      val persisted = try {
        implicit val formats: Formats = formatsFor(resourceName)
        
        val deserialisedT: Either[Failure, Seq[T]] = deserialise(request.getContentString())
        val validatedT: Either[Failure, Seq[T]] = deserialisedT.right.flatMap(validator.validate)
        val copiedWithSeqId: Either[Failure, Seq[T]] = validatedT.right.map(_.map(_.withId(s"${persister.nextSequenceNumber}")))
        val saved: Either[Failure, Seq[T]] = copiedWithSeqId.right.flatMap(pj => persister.save(resourceName, pj))
        val serialised: Either[Failure, String] = saved.right.map(t => Serialization.write(t))
        serialised
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

  def addGetAll[T <: Resource[T] with AnyRef : Manifest](resourceName: String): Unit = {
    // TODO - CAS - 20/04/15 - Remove support for the suffixed URI
    get(s"${uriRoot.stripSuffix("/")}/$resourceName.json") { request => loadAll(resourceName) }
    get(pathTo(resourceName)) { request => loadAll(resourceName) }
  }

  def loadAll[T <: Resource[T] with AnyRef : Manifest](resourceName: String) = {
    implicit val formats: Formats = formatsFor(resourceName)

    persister.loadAll[T](resourceName) match {
      case Right(seqTs) => render.body(Serialization.write(seqTs)).contentType(utf8Json).toFuture
      case Left(failure) => render.status(failure.statusCode).plain(failure.reason).toFuture
    }
  }

  get(s"${uriRoot.stripSuffix("/")}/:unsupportedResourceName") { request =>
    val unsupportedResource = request.routeParams("unsupportedResourceName")
    render.status(404).plain(s"Resource type not supported: $unsupportedResource").toFuture
  }

  get(uriRoot) { request =>
    // TODO - CAS - 07/05/15 - the /[API ROOT]/ URI should be a resource: an array of available resource links and methods; ResourceDescriptor(relativeUri: String, httpMethods: HttpMethod*)
    render.body(DefaultJacksonJsonSerializer.serialize(resourceToSerialiser.keySet.toSeq.sorted[String])).contentType(utf8Json).toFuture
  }

  controller.errorHandler = controller.errorHandler match {
    case Some(handler) => Some(handler)
    case None => Some(knownErrorsHandler)
  }

  def knownErrorsHandler: (Request) => Future[ResponseBuilder] = (request:Request) => request.error match {
    case Some(e:com.twitter.finatra.UnsupportedMediaType) => render.status(415).plain("No handler for this media type found").toFuture
    case Some(x) => render.status(500).plain(s"Finatra has trapped an exception:\n${x}").toFuture
    case other => render.status(500).plain(s"Finatra has handled an exception, but the error is: $other").toFuture
  }
}