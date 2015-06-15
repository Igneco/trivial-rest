package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import com.twitter.finatra.{Controller, Request, ResponseBuilder}
import com.twitter.util.Future
import org.json4s._
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser
import trivial.rest.validation.{RestRulesValidator, Validator}

import scala.collection.mutable
import scala.reflect.ClassTag

/**
 * Trivial REST:
 * (1) Declare a case class
 * (2) Register it as a Resource, specifying the allowed HTTP methods
 * (3) You get a truly RESTful API, your allowed HTTP methods, and persistence, and caching, for free.
 * (4) -- and a few days later -- when you need to migrate data, simply declare the migration rules
 *
 * Concepts to explore:
 *   Case classes as a schema for JSON
 *   Postel's Law / The Robustness Principle - http://en.wikipedia.org/wiki/Robustness_principle
 *   Multiple versions of a case class supported at the same time
 */
class Rest(uriRoot: String,
           controller: Controller,
           serialiser: Serialiser,
           persister: Persister,
           validator: Validator = new RestRulesValidator) {

  private val resources = mutable.ListBuffer[String]()
  private val forwardMigrations = mutable.Map.empty[Class[_], _ => _]
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"

  import controller._

  def resource[T <: Resource[T] with AnyRef : ClassTag : Manifest](supportedMethods: HttpMethod*): Rest = {
    lazy val resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources += resourceName
    serialiser.registerResource[T](allOf[T])

    def allOf[R : Manifest]: Formats => Either[Failure, Seq[R]] = (formats) => persister.loadAll[R](resourceName)

    supportedMethods.foreach {
      case GetAll => addGetAll(resourceName)
      case Post => addPost(resourceName)
      // case Get => /:resourceName/:id  and  /:resourceName?name1=value1&name2=value2  (using the JSON AST DSL query support)
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

  //noinspection ConvertibleToMethodValue
  def migrate[T <: Resource[T] : ClassTag : Manifest](idempotentForwardMigration: (T) => T, backwardsView: (T) => AnyRef = identity[T] _, oldResourceName: Option[String] = None): Either[Failure, Int] = {
    // TODO - CAS - 09/06/15 - register the migration, so we can use it here for post() and get()

    oldResourceName.foreach{ name =>
      backwardsCompatibleAlias(name, backwardsView)
      addPost[T](name)
    }
    persister.migrate(idempotentForwardMigration, oldResourceName)
  }

  private def backwardsCompatibleAlias[T : ClassTag : Manifest](alias: String, backwardsView: (T) => AnyRef): Unit = {
    get(s"/$alias") { request =>
      respond(persister.loadAll[T](Resource.name[T]).right.map(seqTs => seqTs map backwardsView))
    }
  }

  def addPost[T <: Resource[T] with AnyRef : Manifest](resourceName: String): Unit = {

    post(pathTo(resourceName)) { request =>
      // TODO - CAS - 27/04/15 - Yes, this will become a for-comp, but only when I have worked out all the bits

      val persisted: Either[Failure, String] = try {
        // TODO - CAS - 02/06/15 - Check we don't have an ID before we serialise? Write a test ... try to Post something with an ID
        val deserialisedT: Either[Failure, Seq[T]] = serialiser.deserialise(request.getContentString())
        val validatedT: Either[Failure, Seq[T]] = deserialisedT.right.flatMap(validator.validate)
        val copiedWithSeqId: Either[Failure, Seq[T]] = validatedT.right.map(_.map(_.withId(persister.nextSequenceId)))
        val saved: Either[Failure, Int] = copiedWithSeqId.right.flatMap(pj => persister.save(Resource.name[T], pj))
        val serialised: Either[Failure, String] = saved.right.map(t => s"""{"addedCount":"$t"}""")
        serialised
      } catch {
        case e: Exception => Left(Failure.persistence(pathTo(Resource.name[T]), e.getStackTraceString))
      }

      persisted match {
        case Right(contents) => render.body(contents).contentType(utf8Json).toFuture
        case Left(failure) => render.status(failure.statusCode).plain(failure.reason).toFuture
      }

      // TODO - CAS - 22/04/15 - Rebuild cache of T
    }
  }

  def pathTo(resourceName: String) = s"${uriRoot.stripSuffix("/")}/$resourceName"

  def addGetAll[T <: AnyRef : Manifest](resourceName: String): Unit = {
    get(s"${pathTo(resourceName)}.json") { request => route.get(pathTo(resourceName)) }
    get(pathTo(resourceName)) { request =>
      respond(persister.loadAll[T](resourceName)(implicitly[Manifest[T]]))
    }
  }

  private def respond[T <: AnyRef : ClassTag](result: Either[Failure, Seq[T]]): Future[ResponseBuilder] =
    result match {
      case Right(seqTs) => render.body(serialiser.serialise(seqTs)).contentType(utf8Json).toFuture
      case Left(failure) => render.status(failure.statusCode).plain(failure.reason).toFuture
    }

  get(s"${uriRoot.stripSuffix("/")}/:unsupportedResourceName") { request =>
    val unsupportedResource = request.routeParams("unsupportedResourceName")
    render.status(404).plain(s"Resource type not supported: $unsupportedResource").toFuture
  }

  get(uriRoot) { request =>
    // TODO - CAS - 07/05/15 - the /[API ROOT]/ URI should be a resource: an array of available resource links and methods; ResourceDescriptor(relativeUri: String, httpMethods: HttpMethod*)
    render.body(DefaultJacksonJsonSerializer.serialize(resources.sorted[String])).contentType(utf8Json).toFuture
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