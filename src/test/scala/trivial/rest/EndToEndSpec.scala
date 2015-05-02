package trivial.rest

import com.twitter.finatra.FinatraServer
import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, WordSpec}

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper {
  
  override def server = new FinatraServer {
    register(new RestfulControllerExample)
  }

  "Charset for JSON data is UTF-8" in {
    get("/foo")

    response.code must equal(200)
    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }
}