package trivial.rest.controller.finatra

import com.twitter.finagle.httpx.{MediaType, Response, Request}
import com.twitter.finatra.http.response.ResponseBuilder
import com.twitter.util.Future
import trivial.rest._
import trivial.rest.controller.{TrivialRequest, TrivialResponse, TrivialController}

abstract class FinatraController extends com.twitter.finatra.http.Controller with TrivialController {
  private val utf8Json = s"${MediaType.Json}; charset=UTF-8"

  /** Make the response available when wiring URL matchers to handlers. */
  override def response: ResponseBuilder = super.response

  /** Registers a METHOD NOT SUPPORTED handler for any HttpMethod that is not supported by each resource. */
  override def unsupport(path: String, httpMethod: HttpMethod, errorMsg: String) = {
    def methodNotSupported = { request: Request => response.status(405).plain(errorMsg).toFuture }

    httpMethod match {
      case GetAll => super[Controller].get(path) { methodNotSupported }
      case Post => super[Controller].post(path) { methodNotSupported }
      case Get => // get(path) { methodNotSupported(GetAll) } // us2(get, Get, ":idParam")
      case Put => super[Controller].put(path) { methodNotSupported }
      case Delete => super[Controller].delete(path) { methodNotSupported }
      case x => throw new UnsupportedOperationException(s"I haven't built unsupport for $x yet")
    }
  }

  override def success(content: String) = FinatraResponse(response.ok.body(content).contentType(utf8Json).toFuture)

  override def failure(failure: Failure) = FinatraResponse(response.status(failure.statusCode).plain(failure.describe).toFuture)

  // TODO - CAS - 25/09/15 - Find out why Finatra doesn't support refactoring of the function block to a common function
  override def get(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = super[Controller].get(path){ request: Request =>
    f(FinatraRequest(request)).asInstanceOf[FinatraResponse].underlying
  }
  override def post(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = super[Controller].post(path){ request: Request =>
    f(FinatraRequest(request)).asInstanceOf[FinatraResponse].underlying
  }
  override def put(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = super[Controller].put(path){ request: Request =>
    f(FinatraRequest(request)).asInstanceOf[FinatraResponse].underlying
  }
  override def delete(path: String)(f: (TrivialRequest) => TrivialResponse): Unit = super[Controller].delete(path){ request: Request =>
    f(FinatraRequest(request)).asInstanceOf[FinatraResponse].underlying
  }

  private def toFinatra(f: (TrivialRequest) => TrivialResponse): (Request) => Future[Response] = { request: Request =>
    f(FinatraRequest(request)).asInstanceOf[FinatraResponse].underlying
  }
}

case class FinatraRequest(request: Request) extends TrivialRequest {
  override def urlParam(name: String): String = request.params(name)
  override def urlParams: Map[String, String] = request.params
  override def queryParam(name: String): String = request.params(name)
  override def queryParams: Map[String, String] = request.params
  override def contentString: String = request.getContentString()
}

case class FinatraResponse(underlying: Future[Response]) extends TrivialResponse