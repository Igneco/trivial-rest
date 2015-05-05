package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import com.twitter.finatra.{ResponseBuilder, Controller, Request}
import com.twitter.util.Future
import org.json4s._
import org.json4s.native.Serialization
import trivial.rest.configuration.Config
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
class Rest(uriRoot: String, controller: Controller, persister: Persister, validator: Validator = new RestRulesValidator, config: Config = new Config) {
  private val resources = ListBuffer[String]()
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"
  
  import controller._
  
  val serialiser = DefaultJacksonJsonSerializer

  def resource[T <: Restable[T] with AnyRef : ClassTag](supportedMethods: HttpMethod*)(implicit mf: scala.reflect.Manifest[T]) = {
    lazy val resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources.append(resourceName)

    implicit val formats: Formats = if (config.flattenNestedResources) {
      Serialization.formats(NoTypeHints)
    } else {
      Serialization.formats(NoTypeHints)
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
  
  def addPost[T <: Restable[T] with AnyRef : Manifest](resourceName: String)(implicit formats: Formats): Unit = {
    // TODO - CAS - 01/05/15 - This and the copy in Persister -> put into a Serialiser dependency
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

  def addGetAll[T <: Restable[T] with AnyRef : Manifest](resourceName: String)(implicit formats: Formats): Unit = {
    def loadAll(request: Request) =
      persister.loadAll[T](resourceName) match {
        case Right(seqTs) => render.body(Serialization.write(seqTs)).contentType(utf8Json).toFuture
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