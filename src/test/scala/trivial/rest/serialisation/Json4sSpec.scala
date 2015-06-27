package trivial.rest.serialisation

import org.json4s._
import org.json4s.JsonAST.{JArray, JObject, JString, JField}
import org.json4s.native.JsonParser
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.Currency

class Json4sSpec extends WordSpec with MustMatchers {
  private val serialisedCurrencies: String = """[
                                       |  {"isoName":"EUR","symbol":"€","country":"Many"},
                                       |  {"isoName":"GBP","symbol":"£","country":"UK"},
                                       |  {"isoName":"GBp","symbol":"p","country":"UK"},
                                       |  {"isoName":"USD","symbol":"$","country":"US"}
                                       |]""".stripMargin

  private val exampleFieldConstraints: List[(String, JsonAST.JValue)] = List(
    JField("country", JString("UK")),
    JField("symbol", JString("£"))
  )

  private def matches(fieldConstraints: List[(String, JsonAST.JValue)], fieldsInT: List[(String, JsonAST.JValue)]) =
    !fieldConstraints.exists(field => !fieldsInT.contains(field))

  private def extractTheTs(matchingCurrencies: List[JObject]): Seq[Currency] = {
    implicit val formats = (new Json4sSerialiser).formatsExcept[String]
    JArray(matchingCurrencies).extract[Seq[Currency]]
  }

  "We can filter a JSON AST in a for-comp" in {
    val ast: JValue = JsonParser.parse(serialisedCurrencies)

    val matchingCurrencies: List[JObject] = for {
      JArray(currencies) <- ast
      JObject(currency) <- currencies if matches(exampleFieldConstraints, currency)
    } yield JObject(currency)

    extractTheTs(matchingCurrencies)
  }

  "We can filter a JSON AST with a simple filter" in {
    val ast: JValue = JsonParser.parse(serialisedCurrencies)

    val filteredCurrencies: List[JValue] = ast.filter {
      case JObject(fields) => matches(exampleFieldConstraints, fields)
      case x => false
    }

    extractTheTs(filteredCurrencies.asInstanceOf[List[JObject]])
  }
}