package trivial.rest

import com.twitter.finagle.httpx.Status._
import com.twitter.finatra.http.test.EmbeddedHttpServer
import com.twitter.inject.server.FeatureTest
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import trivial.rest.TestDirectories._
import trivial.rest.controller.finatra.TestFinatraServer
import trivial.rest.persistence.JsonOnFileSystem
import trivial.rest.serialisation.Json4sSerialiser
import trivial.rest.validation.{RuleBasedRestValidator, CommonRules}

import scala.reflect.io.Directory

class EndToEndSpec extends FeatureTest {

  val existingCurrencies = """{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}"""

  val docRoot = provisionedTestDir
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(docRoot, serialiser) {
    override def nextSequenceId = formatSequenceId(10)
  }
  val validator = new RuleBasedRestValidator()
  private val demoApp = new TestFinatraServer(docRoot, "/my/api", serialiser, persister, validator)

  override val server = new EmbeddedHttpServer(demoApp)

  "Charset for JSON data is UTF-8" in {
    server.httpGet(
      path = "/my/api/foo",
      andExpect = Ok,
      headers = Map(CONTENT_TYPE -> "application/json; charset=UTF-8")
    )
  }

  "We can get all ExchangeRates" in {
    server.httpGet(
      path = "/my/api/exchangerate",
      andExpect = Ok,
      withBody = s"""[$existingCurrencies]"""
    )
  }

  "We can post an ExchangeRate" in {
    val rate = """[{"rate":55.5,"currency":"1"}]"""

    server.httpPost(
      path = "/my/api/exchangerate",
      postBody = rate,
      andExpect = Ok,
      withBody = """{"addedCount":"1"}"""
    )
  }

  "We can get one ExchangeRate by ID" in {
    server.httpGet(
      path = "/my/api/exchangerate/2",
      andExpect = Ok,
      withBody = s"""{"id":"2","rate":44.4,"currency":"3"}"""
    )
  }

  "get by query" in { pending }

  "delete" in { pending }

  val fooMonkeysNoId = """[{"bar":"monkeys"}]"""
  val fooPenguinsWithId = """[{"id":"0000101","bar":"penguins"}]"""

  "We can amend an item and its ID stays the same" in {
    server.httpPost(
      path = "/my/api/foo",
      postBody = fooMonkeysNoId,
      andExpect = Ok,
      withBody = """{"addedCount":"1"}"""
    )

    server.httpGet(
      path = "/my/api/foo",
      andExpect = Ok,
      withBody = """[{"id":"0000010","bar":"monkeys"}]"""
    )

    server.httpGet(
      path = "/my/api/foo/0000010",
      andExpect = Ok,
      withBody = """{"id":"0000010","bar":"monkeys"}"""
    )

    server.httpPut(
      path = "/my/api/foo/1",
      putBody = fooPenguinsWithId,
      andExpect = Ok,
      withBody = """{"updatedCount":"1"}"""
    )

    server.httpGet(
      path = "/my/api/foo/0000101",
      andExpect = Ok,
      withBody = """{"id":"0000101","bar":"penguins"}"""
    )
  }

  "Resources can fail validation checks on POST and PUT" in {
    server.httpPost(
      path = "/my/api/foo",
      postBody = fooPenguinsWithId,
      andExpect = Conflict,
      withBody = s"${CommonRules.noId} 0000101"
    )

    server.httpPut(
      path = "/my/api/foo/0000101",
      putBody = fooMonkeysNoId,
      andExpect = Conflict,
      withBody = "Resource to update must have an ID"
    )
  }
}