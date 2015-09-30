package trivial.rest.controller.naive

import io.shaka.http.Http.HttpHandler
import io.shaka.http.Request.GET
import io.shaka.http.Response.respond
import io.shaka.http.Status.NOT_FOUND
import io.shaka.http.{Request, Response, HttpServer}
import trivial.rest.{HttpMethod, Failure}
import trivial.rest.controller.{TrivialResponse, TrivialRequest, TrivialController}

import scala.collection.mutable.ListBuffer

class NaiveController(port: Int) extends HttpServer(port, NoddyLogger) with TrivialController {
  private val handlers: ListBuffer[PartialFunction[Request, Response]] = ListBuffer.empty
  private val mutableHandler: HttpHandler = (req: Request) =>
    handlers
      .reduceLeft { (a, b) => a orElse b }
      .applyOrElse(req, notFound)

  def notFound: (Request) => Response = r => respond(s"${r.method} ${r.url} 404 NOT_FOUND").status(NOT_FOUND)

  this.handler(mutableHandler)

  override def unsupport(path: String, httpMethod: HttpMethod, errorMsg: String): Unit = ???

  override def success(content: String): TrivialResponse = NaiveResponse(respond(""))

  override def failure(failure: Failure): TrivialResponse = NaiveResponse(respond(""))

  override def get(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = {
    val `Y` = path
    def handle(request: Request) = f(NaiveRequest(request)).asInstanceOf[NaiveResponse].underlying
    handlers.append({ case request@GET(`Y`) =>  handle(request) })
  }

  override def post(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = ???

  override def put(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = ???

  override def delete(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = ???
}

case class NaiveRequest(request: Request) extends TrivialRequest {
  import io.shaka.http.RequestMatching.URLMatcher

  private val params: Map[String, String] = request.url match {
    case url"/.*/$id" => Map("id" -> id)
    case url"/$resourceName" => Map("unsupportedResourceName" -> resourceName)
    // TODO - CAS - 28/09/15 - Handle query params better
    case url"/.*?$a=$b" => Map(a -> b)
  }

  override def urlParam(name: String): String = params(name)
  override def urlParams: Map[String, String] = params
  override def queryParam(name: String): String = params(name)
  override def queryParams: Map[String, String] = params
  override def contentString: String = request.entityAsString
}

case class NaiveResponse(underlying: Response) extends TrivialResponse

object NoddyLogger extends ((String) => Unit) {
  override def apply(output: String): Unit = println(output)
}