package trivial.rest

import com.twitter.finagle.httpx.{MediaType, Request, Response}
import com.twitter.util.Future
import org.json4s._
import trivial.rest.controller.Controller
import trivial.rest.controller.finatra.FinatraController
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser
import trivial.rest.validation.RestValidator

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
           val controller2: Controller,
           val controller: FinatraController,
           val serialiser: Serialiser,
           persister: Persister,
           validator: RestValidator) {

  private val resources = mutable.ListBuffer[String]()
  private val forwardMigrations = mutable.Map.empty[Class[_], _ => _]
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"

//  import controller._

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

    def unsupportedError(httpMethod: HttpMethod) =
      s"Method not allowed: $httpMethod. Methods supported " +
        s"by /$resourceName are: ${supportedMethods.mkString(", ")}"

    unsupportedMethods.foreach { method =>
      controller2.unsupport(pathTo(resourceName), method, unsupportedError(method))
    }

    this
  }

  def prepopulate[T <: Resource[T] : ClassTag : Manifest](initialPopulation: Seq[T]): Either[Failure, Int] = {
    def removeId[T <: Resource[T]](t: T) = t.withId(None)
    def eliminateDupes(inputs: Seq[T], existing: Seq[T]) = Right((inputs map removeId) diff (existing map removeId))

    for {
      existingResources <- persister.read[T](Resource.name[T]).right
      withoutDupes <- eliminateDupes(initialPopulation, existingResources).right
      numberCreated <- createResources(withoutDupes).right
    } yield numberCreated
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
    controller.get(pathTo(alias)) { request: Request =>
      respondSingle(persister.read[T](Resource.name[T]).right.map(seqTs => seqTs map backwardsView))
    }
  }

  def addPost[T <: Resource[T] with AnyRef : Manifest](resourceName: String): Unit = {
    // TODO - CAS - 22/06/15 - Don't allow POST for Hardcoded Resouces
    controller.post(pathTo(resourceName)) { request: Request =>
      val result = for {
        resources <- serialiser.deserialise(request.getContentString()).right
        numberAdded <- createResources(resources).right
      } yield s"""{"addedCount":"$numberAdded"}"""

      respond(result)
      // TODO - CAS - 22/04/15 - Rebuild cache of T
    }
  }

  def addPut[T <: Resource[T] : Manifest](resourceName: String): Unit = {
    controller.put(s"${pathTo(resourceName)}/:id") { request: Request =>
      val result = for {
        resources <- serialiser.deserialise(request.getContentString()).right
        numberAdded <- updateResources(resources).right
      } yield s"""{"updatedCount":"$numberAdded"}"""

      respond(result)
      // TODO - CAS - 22/09/15 - Rebuild cache of T
    }
  }

  def addId[T <: Resource[T]](t: T): T = t.withId(Some(persister.nextSequenceId))

  // TODO - CAS - 03/07/15 - Also combinify
  private def createResources[T <: Resource[T] : Manifest](deserialisedT: Seq[T]): Either[Failure, Int] =
    try {
      val validatedT: Either[Failure, Seq[T]] = validator.validate(deserialisedT, Post)
      val copiedWithSeqId: Either[Failure, Seq[T]] = validatedT.right.map(_.map(addId))
      val saved: Either[Failure, Int] = copiedWithSeqId.right.flatMap(resources => persister.create(Resource.name[T], resources))
      saved
    } catch {
      case e: Exception => Left(FailFactory.persistCreate(pathTo(Resource.name[T]), ExceptionDecoder.readable(e)))
    }

  private def updateResources[T <: Resource[T] : Manifest](deserialisedT: Seq[T]): Either[Failure, Int] =
    try {
      val validatedT: Either[Failure, Seq[T]] = validator.validate(deserialisedT, Put)
      val saved: Either[Failure, Int] = validatedT.right.flatMap(resources => persister.update(Resource.name[T], resources))
      saved
    } catch {
      case e: Exception => Left(FailFactory.persistUpdate(pathTo(Resource.name[T]), ExceptionDecoder.readable(e)))
    }

  def pathTo(resourceName: String) = s"${uriRoot.stripSuffix("/")}/$resourceName"

  def addDelete[T <: Resource[T] : Manifest](resourceName: String): Unit = {
    controller.delete(s"${pathTo(resourceName)}/:id") { request: Request =>
      respondChanged(persister.delete[T](resourceName, request.params("id")), "deleted")
    }
  }

  def addGet[T <: Resource[T] : ClassTag : Manifest](resourceName: String): Unit = {
    controller.get(s"${pathTo(resourceName)}/:id") { request: Request =>
      def toEither[X : ClassTag](option: Option[X]): Either[Failure, X] =
        option.fold[Either[Failure, X]](Left(Failure(404, s"Not found: $resourceName with ID ${request.params("id")}")))(t => Right(t))

      val result = for {
        itemsRead <- persister.read[T](resourceName, request.params("id")).right
        firstItemRead <- toEither(itemsRead.headOption).right
      } yield serialiser.serialise(firstItemRead)

      respond(result)
    }
  }

  def addGetAll[T <: Resource[T] : Manifest](resourceName: String): Unit = {
    controller.get(pathTo(resourceName)) { request: Request =>
      respondMultiple(persister.read[T](resourceName, request.params))
    }
  }

  // Kill these, then extract Controller methods

  private def respondChanged[T <: Resource[T] with AnyRef : Manifest](result: Either[Failure, Int], direction: String): Future[Response] =
    respond(result.right.map(count => s"""{"${direction}Count":"$count"}"""), direction)

  private def respondMultiple[T <: AnyRef : ClassTag](result: Either[Failure, Seq[T]]): Future[Response] =
    respond(result.right.map(seqTs => serialiser.serialise(seqTs)))

  private def respondSingle[T <: AnyRef : ClassTag](result: Either[Failure, T]): Future[Response] =
    respond(result.right.map(t => serialiser.serialise(t)))

  private def respond[T <: Resource[T] with AnyRef : Manifest](result: Either[Failure, String], direction: String = ""): Future[Response] =
    result match {
      case Right(content) => controller.response.ok.body(content).contentType(utf8Json).toFuture
      case Left(failure) => controller.response.status(failure.statusCode).plain(failure.describe).toFuture
    }

  // TODO - CAS - 25/06/15 - Be more specific, e.g. /:unsuppported?abc --> GET is not supported for :unsupported, which only supports: POST, PUT
  controller.get(s"${uriRoot.stripSuffix("/")}/:unsupportedResourceName") { request: Request =>
    val unsupportedResource = request.params("unsupportedResourceName")
    controller.response.status(404).plain(s"Resource type not supported: $unsupportedResource").toFuture
  }

  controller.get(uriRoot) { request: Request =>
    // TODO - CAS - 07/05/15 - the /[API ROOT]/ URI should be a resource: an array of available resource links and methods; ResourceDescriptor(relativeUri: String, httpMethods: HttpMethod*)
    controller.response.ok.body(serialiser.serialise(resources.sorted[String])).contentType(utf8Json).toFuture
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