package trivial.rest.serialisation

import org.json4s.native.JsonMethods._
import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest.{Currency, ExchangeRate}

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
    val serialiser = new Json4sSerialiser

    serialiser.registerDefaultFields[Currency](Currency(None, "", ""))

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
}