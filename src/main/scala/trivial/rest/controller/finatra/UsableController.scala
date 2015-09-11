package trivial.rest.controller.finatra

import com.twitter.finatra.http.Controller
import com.twitter.finatra.http.response.ResponseBuilder

/**
 * Make the response available when wiring URL matchers to handlers.
 */
abstract class UsableController extends Controller {
  override def response: ResponseBuilder = super.response
}