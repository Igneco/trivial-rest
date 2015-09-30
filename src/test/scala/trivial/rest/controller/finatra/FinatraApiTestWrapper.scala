package trivial.rest.controller.finatra

import com.twitter.finagle.httpx.Status
import com.twitter.finatra.http.test.EmbeddedHttpServer
import trivial.rest.RestTestServer
import trivial.rest.persistence.Persister
import trivial.rest.serialisation.Serialiser
import trivial.rest.validation.RestValidator

import scala.reflect.io.Directory

class FinatraApiTestWrapper(docRoot: Directory,
                        uriRoot: String,
                        serialiser: Serialiser,
                        persister: Persister,
                        validator: RestValidator) extends RestTestServer {

  val underlying = new EmbeddedHttpServer(
    new TestFinatraServer(docRoot, uriRoot, serialiser, persister, validator)
  )

  override def httpGet(path: String, expectedStatus: Int, expectedHeaders: Map[String, String], expectedBody: String) =
    underlying.httpGet(
      path = path,
      andExpect = Status(expectedStatus),
      headers = expectedHeaders,
      withBody = expectedBody
    )

  override def httpDelete(path: String, expectedStatus: Int, expectedHeaders: Map[String, String], expectedBody: String) =
    underlying.httpDelete(
      path = path,
      andExpect = Status(expectedStatus),
      withBody = expectedBody
    )

  override def httpPut(path: String, putBody: String, expectedStatus: Int, expectedHeaders: Map[String, String], expectedBody: String) =
    underlying.httpPut(
      path = path,
      putBody = putBody,
      andExpect = Status(expectedStatus),
      headers = expectedHeaders,
      withBody = expectedBody
    )

  override def httpPost(path: String, postBody: String, expectedStatus: Int, expectedHeaders: Map[String, String], expectedBody: String) =
    underlying.httpPost(
      path = path,
      postBody = postBody,
      andExpect = Status(expectedStatus),
      headers = expectedHeaders,
      withBody = expectedBody
    )
}