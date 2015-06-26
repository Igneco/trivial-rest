package trivial.rest.serialisation

import org.json4s.{JsonAST, JValue}
import org.json4s.JsonAST._
import org.json4s.JsonDSL._
import org.json4s.native.JsonMethods._
import org.json4s.native.JsonParser
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.{Currency, ExchangeRate, Gender}

class Json4sSerialiserSpec extends WordSpec with MustMatchers {

  // TODO - CAS - 13/05/15 - Put these in Serialiser contract test
  "We can serialise a resource which embeds another resource" in {
    val serialiser = new Json4sSerialiser
    serialiser.registerResource[Currency](formats => Right(Seq(Currency(Some("22"), "GBP", "£"))))

    val exchangeRate = ExchangeRate(Some("1"), BigDecimal("33.3"), Currency(Some("22"), "GBP", "£"))

    serialiser.serialise(Seq(exchangeRate)) mustEqual """[{"id":"1","rate":33.3,"currency":"22"}]"""
  }

  // TODO - CAS - 01/06/15 - Use Spaceship (which embeds Vector)
  "We can deserialise a resource which embeds another resource" in {
    val serialiser = new Json4sSerialiser

    val expectedCurrencies = Seq(
      Currency(Some("1"), "EUR", "€"),
      Currency(Some("2"), "GBP", "£"),
      Currency(Some("3"), "USD", "$")
    )

    val serialisedCurrencies: String = """[
                                         |  {"id":"1","isoName":"EUR","symbol":"€"},
                                         |  {"id":"2","isoName":"GBP","symbol":"£"},
                                         |  {"id":"3","isoName":"USD","symbol":"$"}
                                         |]""".stripMargin

    serialiser.deserialise[Currency](serialisedCurrencies) mustEqual Right(expectedCurrencies)
  }

  "We can deserialise data with more fields than we know about" in {
    val serialiser = new Json4sSerialiser

    val expectedCurrencies = Seq(
      Currency(None, "EUR", "€"),
      Currency(None, "GBP", "£"),
      Currency(None, "USD", "$")
    )

    val serialisedCurrencies: String = """[
                                         |  {"isoName":"EUR","symbol":"€","country":"Many"},
                                         |  {"isoName":"GBP","symbol":"£","country":"UK"},
                                         |  {"isoName":"USD","symbol":"$","country":"US"}
                                         |]""".stripMargin

    serialiser.deserialise[Currency](serialisedCurrencies) mustEqual Right(expectedCurrencies)
  }

  "With sensible defaults, we can deserialise input data which is missing necessary fields" in {
    val serialiser = (new Json4sSerialiser)
      .withDefaultFields[Currency](Currency(None, "", ""))

    val expectedCurrencies = Seq(
      Currency(None, "NZD", ""),
      Currency(None, "CHF", "")
    )

    val serialisedCurrencies: String = """[
                                         |  {"isoName":"NZD"},
                                         |  {"isoName":"CHF"}
                                         |]""".stripMargin

    serialiser.deserialise[Currency](serialisedCurrencies) mustEqual Right(expectedCurrencies)
  }

  "Provided data is given priority over default data during merging" in {
    val defaultData = parse(
      """{
        |"name": "Bob",
        |"age": 36,
        |"sex": "M",
        |"postcode":"ABC 123"
        |}""".stripMargin)

    val actualData = parse(
      """{
        "name": "Sue",
        |"age": 27,
        |"sex": "F",
        |"postcode":"DEF 456"
        |}""".stripMargin)

    (defaultData merge actualData) mustEqual actualData  // RHS wins
    (actualData merge defaultData) mustEqual defaultData // RHS wins again
  }

  "We can specify our own serialisation formats, using Boolean as a (surprisingly difficult) example" in {
    val laidBackBoolean = TypeSerialiser[Boolean](
      b => if (b) "yup" else "nope",
      {
        case "yup"  => Some(true)
        case "nope" => Some(false)
        case other  => Some(false)
      }
    )

    val serialiser = (new Json4sSerialiser).withTypeSerialiser(laidBackBoolean)

    val female = Gender(false)
    val serialisedFemale = """{"ishMael":"nope"}"""

    serialiser.serialise(female) mustEqual serialisedFemale
    serialiser.deserialise[Gender](serialisedFemale) mustEqual Right(Seq(female))
  }

  object SpuriousDecimals {
    import util.matching.Regex

    implicit class RegexContext(sc: StringContext) {
      def r = new Regex(sc.parts.mkString, sc.parts.tail.map(_ => "x"): _*)
    }

    val typeSerialiser = TypeSerialiser[BigDecimal](
    b => s"ABC $b",
    { case r"""ABC (\d+\.\d+)$amount"""  => Some(BigDecimal(amount)) }
    )
  }

  val pacificPeso: Currency = Currency(Some("1"), "ABC", "§")

  "We can specify our own serialisation formats, using a spurious BigDecimal example" in {
    val serialiser = (new Json4sSerialiser).registerResource[Currency](_ => Right(Seq(pacificPeso)))
      .withTypeSerialiser(SpuriousDecimals.typeSerialiser)

    val someRate = ExchangeRate(Some("an id"), BigDecimal("0.0000123"), pacificPeso)
    val serialisedRate = """{"id":"an id","rate":"ABC 0.0000123","currency":"1"}"""

    serialiser.serialise(someRate) mustEqual serialisedRate
    serialiser.deserialise[ExchangeRate](serialisedRate) mustEqual Right(Seq(someRate))
  }

  "We can filter a JSON AST" in {
    val serialisedCurrencies: String = """[
                                         |  {"isoName":"EUR","symbol":"€","country":"Many"},
                                         |  {"isoName":"GBP","symbol":"£","country":"UK"},
                                         |  {"isoName":"GBp","symbol":"p","country":"UK"},
                                         |  {"isoName":"USD","symbol":"$","country":"US"}
                                         |]""".stripMargin

    val ast: JValue = JsonParser.parse(serialisedCurrencies)

    val filteredCurrencies: List[JValue] = ast.filter {
      case cur@JObject(fields) => fields.exists(field => field._1 == "country" && field._2 == JString("UK"))
      case x => false
    }
    println(s"filteredCurrencies: ${filteredCurrencies}")

    val matchingCurrencies: List[JObject] = for {
      JArray(currencies) <- ast
      JObject(currency) <- currencies
      if currency contains JField("country", JString("UK"))
    } yield JObject(currency)

    println(s"matchingCurrencies: ${matchingCurrencies}")


    val serialiser = (new Json4sSerialiser)
    implicit val formats = serialiser.formatsExcept[String]
    val matchingTs: Seq[Currency] = JArray(matchingCurrencies).extract[Seq[Currency]]
    println(s"matchingTs: ${matchingTs}")
  }
}