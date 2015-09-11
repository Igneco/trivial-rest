package trivial.rest.controller.finatra

import com.google.inject.Inject
import com.twitter.finagle.httpx.{Request, Response}
import com.twitter.finatra.http.exceptions.ExceptionMapper
import com.twitter.finatra.http.response.ResponseBuilder

import scala.compat.Platform._

/**
 * Finatra has jumped the shark. Here we gather some workarounds.
 */
object PatchesForNastyFinatraV2 {
  // copied verbatim from https://github.com/twitter/finatra/blob/master/http/README.md#exception-mappers
  // ... and it doesn't even compile
//  @Singleton
//  class NotFoundFilter @Inject()(
//                                  response: ResponseBuilder)
//    extends SimpleFilter[Request, Response] {
//
//    def apply(request: Request, service: Service[Request, Response]): Future[Response] = {
//      service(request) map { origResponse: Response =>
//        if (origResponse.status == Status.NotFound)
//          response.notFound("bar")
//        else
//          origResponse
//      }
//    }
//  }
}

class NonHidingExceptionsMapper @Inject()(response: ResponseBuilder) extends ExceptionMapper[Exception] {
  override def toResponse(request: Request, e: Exception): Response = {
    response.internalServerError(s"${e.getMessage}\n${e.getStackTrace.mkString("", EOL, EOL)}").response
  }
}