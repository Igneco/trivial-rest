package trivial.rest.validation

import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest._

class RestRulesValidatorSpec extends WordSpec with MustMatchers {
  "Our default validation does not allow clients to provide their own surrogate IDs" in {
    val validator = new RuleBasedValidator()
    val currency = Currency(Some("007"), "BOB", "§")

    validator.validate(Seq(currency), Post) match {
      case Right(x) => fail("Should have bailed")
      case Left(f @ Failure(code, reason)) => f mustEqual Failure(409, "You can't POST an item with an ID; the system will allocate an ID upon resource creation. Offending ID: 007")
    }
  }

  "We don't validate the natural keys on HardCoded values, because these should never change" in {
    val validator = new RuleBasedValidator()

    validator.validate(Seq(Gender(false)), Post) mustEqual Right(Seq(Gender(false)))
  }

  // TODO - CAS - 10/07/15 - Differentiate between HardCoded and NaturalKey, so that we can post changes to the latter but not the former

  // Some Resources might quite rightly contain duplicates, so this is specified per Resource
  "We can specify per Resource whether duplicates are allowed" in {
    val existingFoos = Seq(
      Foo(Some("1"), "bar"),
      Foo(Some("2"), "baz"),
      Foo(Some("3"), "bazaar")
    )

    val validator = new RuleBasedValidator().withRules[Foo](CommonRules.noDuplicates(existingFoos))

    val newFoo = Foo(None, "baz")

    validator.validate(Seq(newFoo), Post) mustEqual Left(Failure(409, s"A matching item already exists: $newFoo"))
  }

  "When multiple validations fail, multiple failures are returned, and the highest HTTP error code wins" in {
    // Option 1: change Either[Failure, Seq[T]] to Either[Seq[Failure], Seq[T]]
    // Option 2: Allow multiple failure reasons to be stored in a Failure, always favouring the highest HTTP error code
    pending
  }
}