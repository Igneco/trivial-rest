package trivial.rest

import com.twitter.finatra.FinatraServer
import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.persistence.{FileSystem, JsonOnFileSystem}

import scala.reflect.io.{File, Directory}

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper with BeforeAndAfterAll {

  override protected def beforeAll() = cleanTestDirs()
  override protected def afterAll()  = beforeAll()

  val existingCurrencies = """{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}"""

  override def server = new FinatraServer {
    register(new RestfulControllerExample(new JsonOnFileSystem(provisionedTestDir)))
  }

  "Charset for JSON data is UTF-8" in {
    get("/foo")

    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }

  "We can get all ExchangeRates" in {
    get("/exchangerate")

    response.body must equal(s"""[$existingCurrencies]""")
  }

  "We can post an ExchangeRate" in {
    val newCurrency = """[{"rate":55.5,"currency":"1"}]"""
    val newCurrencyWithId = """{"id":"101","rate":55.5,"currency":"1"}"""

    post("/exchangerate", body = newCurrency)

    response.body must equal("""{"addedCount":"1"}""")
  }
}