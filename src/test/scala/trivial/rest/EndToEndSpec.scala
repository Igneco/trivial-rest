package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, OneInstancePerTest, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.validation.CommonRules

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper with OneInstancePerTest {

  val existingCurrencies = """{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}"""

  override val server = new DemoApp(provisionedTestDir, "/my/api")

  "Charset for JSON data is UTF-8" in {
    get("/my/api/foo")

    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }

  "We can get all ExchangeRates" in {
    get("/my/api/exchangerate") --> s"""[$existingCurrencies]"""
  }

  "We can post an ExchangeRate" in {
    val rate = """[{"rate":55.5,"currency":"1"}]"""

    post("/my/api/exchangerate", body = rate) --> """{"addedCount":"1"}"""
  }

  "We can get one ExchangeRate by ID" in {
    get("/my/api/exchangerate/2") --> s"""{"id":"2","rate":44.4,"currency":"3"}"""
  }

  "get by query" in { pending }

  "delete" in { pending }

  val fooMonkeysNoId = """[{"bar":"monkeys"}]"""
  val fooPenguinsWithId = """[{"id":"0000101","bar":"penguins"}]"""

  "We can amend an item and its ID stays the same" in {
    post("/my/api/foo", body = fooMonkeysNoId)           --> """{"addedCount":"1"}"""

    get("/my/api/foo/0000101")                           --> """{"id":"0000101","bar":"monkeys"}"""

    put("/my/api/foo/0000101", body = fooPenguinsWithId) --> """{"updatedCount":"1"}"""

    get("/my/api/foo/0000101")                           --> """{"id":"0000101","bar":"penguins"}"""
  }

  "Resources can fail validation checks on POST and PUT" in {
    post("/my/api/foo", body = fooPenguinsWithId)     --> (409 -> s"${CommonRules.noId} 0000101")

    put("/my/api/foo/0000101", body = fooMonkeysNoId) --> (409 -> "Resource to update must have an ID")
  }

  implicit class Expectation(thingToExecute: => Unit) {
    def -->(expectedBody: String) = {
      assert(expectedBody, 200)

      // TODO - CAS - 10/07/15 - Make POST response failures (below) proper JSON, too.
      response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
    }

    def -->(expected: (Int, String)) = { assert(expected._2, expected._1) }

    private def assert(body: String, code: Int) = {
      thingToExecute
      response.body mustEqual body
      response.code mustEqual code
    }
  }
}