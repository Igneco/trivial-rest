package trivial.rest.json

import org.json4s._
import org.json4s.native.{Serialization, JsonParser}
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.Planet

class JsonParsingSpec extends WordSpec with MustMatchers {
  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  
  "We can parse serialised JSON to a case class type" in  {
    val json = """{"name": "Earth", "classification": "tolerable"}"""

    val asJson = JsonParser.parse(json, true)
    println(s"asJson: ${asJson}")
    val planet: Planet = asJson.extract[Planet]
    
    planet mustEqual Planet(None, "Earth", "tolerable")
  }
}
