package trivial.rest.controller.finatra

import com.twitter.finagle.httpx.Request
import com.twitter.finatra.http.response.ResponseBuilder
import trivial.rest._
import trivial.rest.controller.Controller

abstract class FinatraController extends com.twitter.finatra.http.Controller with Controller {
  /**
   * Make the response available when wiring URL matchers to handlers.
   */
  override def response: ResponseBuilder = super.response

  override def unsupport(path: String, httpMethod: HttpMethod, errorMsg: String) = {
    def methodNotSupported = { request: Request =>
      response.status(405).plain(errorMsg).toFuture
    }

    httpMethod match {
      case GetAll => get(path) { methodNotSupported }
      case Post => post(path) { methodNotSupported }
      case Get => // get(path) { methodNotSupported(GetAll) } // us2(get, Get, ":idParam")
      case Put => put(path) { methodNotSupported }
      case Delete => delete(path) { methodNotSupported }
      case x => throw new UnsupportedOperationException(s"I haven't built unsupport for $x yet")
    }
  }




}