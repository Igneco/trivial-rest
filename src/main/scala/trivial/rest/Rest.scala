package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.serialization.DefaultJacksonJsonSerializer
import com.twitter.finatra.{Controller, Request}
import org.json4s._
import org.json4s.native.JsonMethods._
import org.json4s.native.{JsonParser, Serialization}
import trivial.rest.persistence.Persister

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
class Rest(controller: Controller, uriRoot: String, persister: Persister) {
  private val resources = ListBuffer[String]()
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"
  
  import controller._
  
  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  val serialiser = DefaultJacksonJsonSerializer

  def resource[T <: AnyRef : ClassTag](supportedMethods: HttpMethod*)(implicit mf: scala.reflect.Manifest[T]) = {
    def resourceName = implicitly[ClassTag[T]].runtimeClass.getSimpleName.toLowerCase

    resources.append(resourceName)

    supportedMethods.foreach {
      case GetAll => addGetAll(resourceName)
      case Post => addPost(resourceName)(mf)
      case x => throw new UnsupportedOperationException(s"I haven't built support for $x yet")
    }
    
    this
  }

  def addPost[T <: AnyRef](resourceName: String)(implicit mf: scala.reflect.Manifest[T]): Unit = {
    def deserialise(body: String): Either[String, T] =
      try {
//        val asJson: JValue = JsonParser.parse(body, true)
//        val aPlanet: T = asJson.extract[T]
        Right(Serialization.read[T](body))
      } catch {
        case e: Exception => Left(s"Failed to deserialise into $resourceName, due to: $e")
      }
    
    post(pathTo(resourceName)) { request =>
      val eitherT: Either[String, T] = deserialise(request.getContentString())
      // TODO - CAS - 22/04/15 - insert the ID by copying the case class?
      val copied: Either[String, T] = eitherT.right.map(t => t)
      val szed: Either[String, String] = copied.right.map(t => Serialization.writePretty(t))
      val svzed: Either[String, Array[Byte]] = szed.right.flatMap(pj => persister.save(resourceName, pj))

      svzed match {
        case Right(bytes) => render.body(bytes).contentType(utf8Json).toFuture
        case Left(whyNot) => render.status(500).plain(whyNot).toFuture
      }
      
      // TODO - CAS - 22/04/15 - Rebuild cache of T
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

case class Planet(id: Option[String], name: String, classification: String)// extends Restable