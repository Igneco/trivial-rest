package trivial.rest.serialisation

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
}