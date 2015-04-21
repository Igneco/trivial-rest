package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.{Request, FileResolver, Controller}
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import Json._
import trivial.rest.persistence.{Persister, JsonOnFileSystem}

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Trivial REST:
 * (1) Declare a case class
 * (2) Register it as a Resource, specifying the allowed HTTP methods
 * (3) You get a truly RESTful API, your allowed HTTP methods, and persistence, and caching, for free.
 */
class Rest(controller: Controller, uriRoot: String, persister: Persister) {
  private val resources = ListBuffer[String]()
  
  import controller._
  
  val serialiser = DefaultJacksonJsonSerializer

  def resource[T: ClassTag](supportedMethods: HttpMethod*) = {
    def resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources.append(resourceName)

    supportedMethods.foreach {
      case GetAll => addGetAll(resourceName)
      case Put => addPut(resourceName)
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }
    
    this
  }
  
  def addPut(resourceName: String): Unit = {
    put(pathTo(resourceName)) { request =>
      val body: String = request.getContentString()
      println(s"body: ${body}")
      persister.save(resourceName, body) match {
        case Right(bytes) => render.body(bytes).contentType(utf8Json).toFuture
        case Left(whyNot) => render.status(500).plain(whyNot).toFuture
      }
    }
  }

  def pathTo(resourceName: String): String = {
    s"${uriRoot.stripSuffix("/")}/$resourceName"
  }

  def addGetAll(resourceName: String): Unit = {
    def loadAll(request: Request) =
      persister.loadAll(resourceName) match {
        case Right(bytes) => render.body(bytes).contentType(utf8Json).toFuture
        case Left(whyNot) => render.status(500).plain(whyNot).toFuture
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

trait HttpMethod
case object GetAll extends HttpMethod
case object Get extends HttpMethod
case object Put extends HttpMethod
case object Post extends HttpMethod
case object Delete extends HttpMethod

object Json {
  val utf8Json = s"${MediaType.Json}; charset=UTF-8"
}