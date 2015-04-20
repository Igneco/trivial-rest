package trivial.rest

import com.twitter.finatra.test.SpecHelper
import com.twitter.finatra.{Controller, FinatraServer}
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, WordSpec}

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper {
  override def server = new FinatraServer {
    val app = new Controller with Rest {
      resource[Spaceship](GetAll)
      resource[Vector](GetAll)
    }
    register(app)
  }

  // TODO - CAS - 20/04/15 - This is a lie. It behaves differently when running normally -
  // charset is not defined. Need to debug the Finatra source to find out why.
  "Charset for JSON data is UTF-8" in {
    get("/spaceship.json")

    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }
}