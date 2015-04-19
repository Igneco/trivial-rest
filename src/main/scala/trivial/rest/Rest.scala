package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.Controller
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import Json._

import scala.collection.mutable.ListBuffer
import scala.reflect.ClassTag

/**
 * Trivial REST:
 * (1) Declare a case class
 * (2) Register it as a Resource, specifying the allowed HTTP methods
 * (3) You get a truly RESTful API, your allowed HTTP methods, and persistence, and caching, for free.
 */
trait Rest { controller: Controller =>
  private val resources = ListBuffer[String]()
  private val rootPath : String = "/"

  def resource[T: ClassTag](supportedMethods: HttpMethod*) = {
    def resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources.append(resourceName)
    supportedMethods.foreach{
      case GetAll => addGetAll(resourceName)
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }
  }

  // TODO - CAS - 17/04/15 - Trap all get(s"/$resourceName.json") and give a custom error message for 404
  def addGetAll(resourceName: String): Unit = {
    controller.get(s"/$resourceName.json") { request =>
      try {
        render.static(s"$resourceName.json").contentType(utf8Json).toFuture
      } catch {
        case iae: IllegalArgumentException => render.status(500).plain(s"Could not find $resourceName.json").toFuture
      }
    }
  }

  get(rootPath) { request =>
    render.body(serialiser.serialize(resources.sorted[String])).contentType(utf8Json).toFuture
  }
}

trait HttpMethod
case object GetAll extends HttpMethod
case object Get extends HttpMethod

object Json {
  val utf8Json = s"${MediaType.Json}; charset=UTF-8"
  val serialiser = DefaultJacksonJsonSerializer
}