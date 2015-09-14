package trivial.rest

import com.twitter.finatra.http.HttpServer
import com.twitter.finatra.http.filters.CommonFilters
import com.twitter.finatra.http.routing.HttpRouter
import trivial.rest.controller.finatra.{NonHidingExceptionsMapper, UsableController}

class TrivialFinatraServer extends HttpServer {
  val controller = new UsableController {}

  override def configureHttp(router: HttpRouter) =
    router
      .exceptionMapper[NonHidingExceptionsMapper]
      .filter[CommonFilters]
      .add(controller)
}
