package trivial.rest.validation

import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest._

class RuleBasedValidatorSpec extends WordSpec with MustMatchers {
  "Our default validation does not allow clients to provide their own surrogate IDs" in {
    val validator = new RuleBasedRestValidator()
    val currency = Currency(Some("007"), "BOB", "§")

    validator.validate(Seq(currency), Post) mustEqual Left(Failure(409, s"${CommonRules.noId} 007"))
  }

  "We don't validate the natural keys on HardCoded values, because these should never change" in {
    val validator = new RuleBasedRestValidator()

    validator.validate(Seq(Gender(false)), Post) mustEqual Right(Seq(Gender(false)))
  }

  // TODO - CAS - 10/07/15 - Differentiate between HardCoded and NaturalKey, so that we can post changes to the latter but not the former

  val existingFoos = Seq(
    Foo(Some("1"), "bar"),
    Foo(Some("2"), "baz"),
    Foo(Some("3"), "bazaar")
  )

  // Some Resources might quite rightly contain duplicates, so this is specified per Resource
  "We can specify per Resource whether duplicates are allowed" in {
    val validator = new RuleBasedRestValidator().withRules[Foo](CommonRules.noDuplicates(existingFoos))

    val newFoo = Foo(None, "baz")

    validator.validate(Seq(newFoo), Post) mustEqual Left(Failure(409, s"A matching item already exists: $newFoo"))
  }

  "When multiple validations fail, multiple failures are returned" in {
    val validator = new RuleBasedRestValidator()

    validator.validate(existingFoos, Post) mustEqual Left(Failure(409, Seq(s"${CommonRules.noId} 1", s"${CommonRules.noId} 2", s"${CommonRules.noId} 3")))
  }
}