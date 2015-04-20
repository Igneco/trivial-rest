package trivial.rest

import com.twitter.app.App
import com.twitter.finagle.http.{MediaType, Request => FinagleRequest, Response => FinagleResponse}
import com.twitter.finagle.{Service, SimpleFilter}

class RestOverridesFilter extends SimpleFilter[FinagleRequest, FinagleResponse] with App {

  def apply(request: FinagleRequest, service: Service[FinagleRequest, FinagleResponse]) =
    service(request) map { response =>
      response.contentType.filter(_ == MediaType.Json).foreach(ct => response.setContentType(ct, "UTF-8"))
      response
    }
}