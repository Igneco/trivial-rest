package trivial.rest

import com.twitter.finagle.httpx.{Response, MediaType, Request}
import com.twitter.util.Future
import org.json4s._
import trivial.rest.controller.finatra.UsableController
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser
import trivial.rest.validation.Validator

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
           val controller: UsableController,
           val serialiser: Serialiser,
           persister: Persister,
           validator: Validator) {

  private val resources = mutable.ListBuffer[String]()
  private val forwardMigrations = mutable.Map.empty[Class[_], _ => _]
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"

  import controller._

//  notFound { request: Request =>
//    // TODO - CAS - 24/06/15 - Match this to a resource path (res, res/:id or res?a=b)
//    response.status(404).plain(s"Unrecognised route ====> ${request.path}\nHave you forgotten to register a resource or specify the correct HTTP method?").toFuture
//  }

  def resource[T <: Resource[T] with AnyRef : ClassTag : Manifest](supportedMethods: HttpMethod*): Rest = {
    lazy val resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources += resourceName
    serialiser.registerResource[T](loadAllItems)

    def loadAllItems: Formats => Either[Failure, Seq[T]] = (formats) => persister.read[T](resourceName)

    supportedMethods.foreach {
      case GetAll => addGetAll(resourceName)
      case Post => addPost(resourceName)
      case Put => addPut(resourceName)
      case Get => addGet(resourceName)
      case Delete => addDelete(resourceName)
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }

    val unsupportedMethods: Set[HttpMethod] = HttpMethod.all.diff(supportedMethods.toSet)

    def unsupportedError(httpMethod: HttpMethod) = {
      println(s"Generating unsupportedError for HTTP method: ${httpMethod}")
      s"Method not allowed: $httpMethod. Methods supported " +
        s"by /$resourceName are: ${supportedMethods.mkString(", ")}"
    }

    // TODO - CAS - 03/05/15 - add a mapping from HttpMethod to controller function, as the first stage of abstracting the Controller

    def methodNotSupported(httpMethod: HttpMethod): (Request) => Future[Response] = { request: Request =>
      response.status(405).plain(unsupportedError(httpMethod)).toFuture
    }

    unsupportedMethods foreach {
      case GetAll => get(pathTo(resourceName), resourceName) { methodNotSupported(GetAll) }
      case Post => post(pathTo(resourceName), resourceName) { methodNotSupported(Post) }
      case Get => // get(pathTo(resourceName), resourceName) { methodNotSupported(GetAll) } // us2(get, Get, ":idParam")
      case Put => put(pathTo(resourceName), resourceName) { methodNotSupported(Put) }
      case Delete => delete(pathTo(resourceName), resourceName) { methodNotSupported(Delete) }
      case x => throw new UnsupportedOperationException(s"I haven't built unsupport for $x yet")
    }

    this
  }

  def prepopulate[T <: Resource[T] : ClassTag : Manifest](initialPopulation: Seq[T]): Either[Failure, Int] = {
    def removeIds[T <: Resource[T]](seqTs: Seq[T]) = seqTs.map(_.withId(None))
    def eliminateDupes(inputs: Seq[T], existing: Either[Failure, Seq[T]]) = (existing.right map removeIds).right.map(exs => removeIds(inputs) diff exs)

    createResources(eliminateDupes(initialPopulation, persister.read[T](Resource.name[T])))
  }

  def migrate[T <: Resource[T] : ClassTag : Manifest](forwardMigration: (T) => T = identity[T] _,
                                                      backwardsView: (T) => AnyRef = identity[T] _,
                                                      oldResourceName: Option[String] = None): Either[Failure, Int] = {
    oldResourceName.foreach{ name =>
      backwardsCompatibleAlias(name, backwardsView)
      addPost[T](name)
    }

    try {
      persister.migrate(forwardMigration, oldResourceName)
    } catch {
      case e: Exception => Left(Failure(500, s"Migration failed, due to: ${e.toString}\n${e.getStackTraceString}"))
    }
  }

  private def backwardsCompatibleAlias[T <: Resource[T] : ClassTag : Manifest](alias: String, backwardsView: (T) => AnyRef): Unit = {
    get(pathTo(alias)) { request: Request =>
      respond(persister.read[T](Resource.name[T]).right.map(seqTs => seqTs map backwardsView))
    }
  }

  def addPost[T <: Resource[T] with AnyRef : Manifest](resourceName: String): Unit = {
    // TODO - CAS - 22/06/15 - Don't allow POST for Hardcoded Resouces
    post(pathTo(resourceName)) { request: Request =>
      respondChanged(createResources(serialiser.deserialise(request.getContentString())), "added")
      // TODO - CAS - 22/04/15 - Rebuild cache of T
    }
  }

  // TODO - CAS - 03/07/15 - Also combinify
  private def createResources[T <: Resource[T] : Manifest](deserialisedT: Either[Failure, Seq[T]]): Either[Failure, Int] =
    try {
      val validatedT: Either[Failure, Seq[T]] = deserialisedT.right.flatMap(validator.validate(_, Post))
      val copiedWithSeqId: Either[Failure, Seq[T]] = validatedT.right.map(_.map(_.withId(Some(persister.nextSequenceId))))
      val saved: Either[Failure, Int] = copiedWithSeqId.right.flatMap(resources => persister.create(Resource.name[T], resources))
      saved
    } catch {
      case e: Exception => Left(FailFactory.persistCreate(pathTo(Resource.name[T]), ExceptionDecoder.readable(e)))
    }

  private def updateResources[T <: Resource[T] : Manifest](deserialisedT: Either[Failure, Seq[T]]): Either[Failure, Int] =
    try {
      val validatedT: Either[Failure, Seq[T]] = deserialisedT.right.flatMap(validator.validate(_, Put))
      val saved: Either[Failure, Int] = validatedT.right.flatMap(resources => persister.update(Resource.name[T], resources))
      saved
    } catch {
      case e: Exception => Left(FailFactory.persistUpdate(pathTo(Resource.name[T]), ExceptionDecoder.readable(e)))
    }

  def pathTo(resourceName: String) = s"${uriRoot.stripSuffix("/")}/$resourceName"

  def addPut[T <: Resource[T] : Manifest](resourceName: String): Unit = {
    put(s"${pathTo(resourceName)}/:id") { request: Request =>
      respondChanged(updateResources(serialiser.deserialise(request.getContentString())), "updated")
    }
  }

  def addDelete[T <: Resource[T] : Manifest](resourceName: String): Unit = {
    delete(s"${pathTo(resourceName)}/:id") { request: Request =>
      respondChanged(persister.delete[T](resourceName, request.params("id")), "deleted")
    }
  }

  def addGet[T <: Resource[T] : ClassTag : Manifest](resourceName: String): Unit = {
    get(s"${pathTo(resourceName)}/:id") { request: Request =>
      def toEither[T : ClassTag](option: Option[T]): Either[Failure, T] =
        option.fold[Either[Failure, T]](Left(Failure(404, s"Not found: $resourceName with ID ${request.params("id")}")))(t => Right(t))

      respondSingle(persister.read[T](resourceName, request.params("id")).right.flatMap(seqTs => toEither(seqTs.headOption)))
    }
  }

  def addGetAll[T <: Resource[T] : Manifest](resourceName: String): Unit = {
    get(pathTo(resourceName)) { request: Request =>
      respond(persister.read[T](resourceName, request.params))
    }
  }

  // TODO - CAS - 24/06/15 - Combinify all three ...
  def respondChanged[T <: Resource[T] with AnyRef : Manifest](result: Either[Failure, Int], direction: String) =
    result match {
      case Right(count) => response.ok.body( s"""{"${direction}Count":"$count"}""").contentType(utf8Json).toFuture
      case Left(failure) => response.status(failure.statusCode).plain(failure.describe).toFuture
    }

  private def respond[T <: AnyRef : ClassTag](result: Either[Failure, Seq[T]]) =
    result match {
      case Right(seqTs) => response.ok.body(serialiser.serialise(seqTs)).contentType(utf8Json).toFuture
      case Left(failure) => response.status(failure.statusCode).plain(failure.describe).toFuture
    }

  private def respondSingle[T <: AnyRef : ClassTag](result: Either[Failure, T]) =
    result match {
      case Right(t) => response.ok.body(serialiser.serialise(t)).contentType(utf8Json).toFuture
      case Left(failure) => response.status(failure.statusCode).plain(failure.describe).toFuture
    }

  // TODO - CAS - 25/06/15 - Be more specific, e.g. /:unsuppported?abc --> GET is not supported for :unsupported, which only supports: POST, PUT
  get(s"${uriRoot.stripSuffix("/")}/:unsupportedResourceName") { request: Request =>
    val unsupportedResource = request.params("unsupportedResourceName")
    response.status(404).plain(s"Resource type not supported: $unsupportedResource").toFuture
  }

  get(uriRoot) { request: Request =>
    // TODO - CAS - 07/05/15 - the /[API ROOT]/ URI should be a resource: an array of available resource links and methods; ResourceDescriptor(relativeUri: String, httpMethods: HttpMethod*)
    response.ok.body(serialiser.serialise(resources.sorted[String])).contentType(utf8Json).toFuture
  }

//  controller.errorHandler = controller.errorHandler match {
//    case Some(handler) => Some(handler)
//    case None => Some(knownErrorsHandler)
//  }

//  def knownErrorsHandler: (Request) => Future[Response] = (request:Request) => request.error match {
//    case Some(e:com.twitter.finatra.UnsupportedMediaType) => response.status(415).plain("No handler for this media type found").toFuture
//    case Some(x) => response.status(500).plain(s"Finatra has trapped an exception:\n${x}").toFuture
//    case other => response.status(500).plain(s"Finatra has handled an exception, but the error is: $other").toFuture
//  }
}