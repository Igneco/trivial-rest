package trivial.rest.controller

import trivial.rest.HttpMethod

trait Controller {
  // Intercept calls to path, and return a 405 result with the descriptive plain-text message provided
  def unsupport(path: String, httpMethod: HttpMethod, errorMsg: String)
}