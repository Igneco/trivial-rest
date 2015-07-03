package trivial.rest

import com.twitter.finagle.http.MediaType
import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, OneInstancePerTest, WordSpec}

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper with OneInstancePerTest {

  val existingCurrencies = """{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}"""

  override val server = new TestableFinatraServer("/my/api/")

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

  "We can amend an item and its ID stays the same" in {
    val fooMonkeys = """[{"bar":"monkeys"}]"""
    val fooPenguins = """[{"id":"0000101","bar":"penguins"}]"""

    post("/my/api/foo", body = fooMonkeys)         --> """{"addedCount":"1"}"""

    get("/my/api/foo/0000101")                     --> """{"id":"0000101","bar":"monkeys"}"""

    put("/my/api/foo/0000101", body = fooPenguins) --> """{"updatedCount":"1"}"""

    get("/my/api/foo/0000101")                     --> """{"id":"0000101","bar":"penguins"}"""
  }

  implicit class ExpectedSuccess(thingToExecute: => Unit) {
    def -->(expectedBody: String): Unit = {
      thingToExecute
      response.body must equal(expectedBody)
      response.code must equal(200)
      response.getHeader(Names.CONTENT_TYPE) must equal(s"${MediaType.Json}; charset=UTF-8")
    }
  }
}