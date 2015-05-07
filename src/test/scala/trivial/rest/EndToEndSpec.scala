package trivial.rest

import com.twitter.finatra.FinatraServer
import com.twitter.finatra.test.SpecHelper
import org.jboss.netty.handler.codec.http.HttpHeaders.Names._
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.persistence.JsonOnFileSystem

import scala.reflect.io.Directory

class EndToEndSpec extends WordSpec with MustMatchers with SpecHelper {
  
  override def server = new FinatraServer {
    register(new RestfulControllerExample(new JsonOnFileSystem(Directory("src/test/resources"))))
  }

  "Charset for JSON data is UTF-8" in {
    get("/foo")

    response.code must equal(200)
    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }
  
  "We can get all ExchangeRates" in {
    get("/exchangerate")

    response.body must equal("""[{"id":"1","rate":33.3,"currency":"2"},{"id":"2","rate":44.4,"currency":"3"}]""")
    response.code must equal(200)
    response.getHeader(CONTENT_TYPE) must equal("application/json; charset=UTF-8")
  }
  
  "We can post an ExchangeRate" in {
    pending
  }
}