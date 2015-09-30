package trivial.rest.controller.naive

import io.shaka.http.HttpServer
import io.shaka.http.Request._
import io.shaka.http.Response.respond
import io.shaka.http.Status.NOT_FOUND

object ExampleNaiveServer extends App {
  val server = HttpServer(8080).handler(request => respond("Hello World!")).start()

  server.handler{
    case request@GET(echoUrl) => respond(echoUrl)
//    case request@POST("/some/restful/thing") => respond(...)
    case _ => respond("doh!").status(NOT_FOUND)
  }
}