package trivial.rest.controller.finatra

import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter

class TrivialFinatraServer extends HttpServer {
  val controller = new FinatraController {}

  override def configureHttp(router: HttpRouter) =
    router
      .exceptionMapper[NonHidingExceptionsMapper]
      .filter[CommonFilters]
      .add(controller)
}
