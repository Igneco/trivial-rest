package trivial.rest.controller

import trivial.rest.{Failure, HttpMethod}

trait TrivialController {

  /** Register a METHOD NOT SUPPORTED handler for any HttpMethod that is not supported by each resource. */
  def unsupport(path: String, httpMethod: HttpMethod, errorMsg: String)

  def success(content: String): TrivialResponse
  def failure(failure: Failure): TrivialResponse

  def get(path: String)(f: TrivialRequest => TrivialResponse)
  def post(path: String)(f: TrivialRequest => TrivialResponse)
  def put(path: String)(f: TrivialRequest => TrivialResponse)
  def delete(path: String)(f: TrivialRequest => TrivialResponse)
}

trait TrivialRequest {
  def urlParam(name: String): String
  def urlParams: Map[String,String]

  def queryParam(name: String): String
  def queryParams: Map[String,String]

  def contentString: String
}

trait TrivialResponse