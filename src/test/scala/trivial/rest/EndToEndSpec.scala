package trivial.rest

import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, WordSpec}

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper {

  val existingCurrencies = """{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}"""

  override val server = new TestableFinatraServer("/my/api/")

  "Charset for JSON data is UTF-8" in {
    get("/my/api/foo")

    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }

  "We can get all ExchangeRates" in {
    get("/my/api/exchangerate")

    response.body must equal(s"""[$existingCurrencies]""")
  }

  "We can post an ExchangeRate" in {
    val rate = """[{"rate":55.5,"currency":"1"}]"""

    post("/my/api/exchangerate", body = rate)

    response.body must equal("""{"addedCount":"1"}""")
  }
}