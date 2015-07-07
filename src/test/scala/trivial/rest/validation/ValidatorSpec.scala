package trivial.rest.validation

import org.scalatest.{MustMatchers, WordSpec}
import trivial.rest._

class ValidatorSpec extends WordSpec with MustMatchers {
  "Our default validation does not allow clients to provide their own surrogate IDs" in {
    val validator = new Validator {}
    val currency = Currency(Some("Not allowed to POST an ID"), "BOB", "§")

    validator.validate(Seq(currency), Post) match {
      case Right(x) => fail("Should have bailed")
      case Left(f @ Failure(code, reason)) => f mustEqual Failure(409, s"Validation failure. You can't POST an item with an ID - the system will allocate an ID upon resource creation. Offending item(s):$currency")
    }
  }

  "We don't validate the natural keys on HardCoded values, because these should never change" in {
    val validator = new Validator {}

    validator.validate(Seq(Gender(false)), Post) mustEqual Right(Seq(Gender(false)))
  }

  "We can specify per Resource whether duplicates are allowed" in {
    // Some Resources might quite rightly contain duplicates
    // We should specify per Resource whether duplicates are allowed
    pending
  }

  "When multiple validations fail, multiple failures are returned, and the highest HTTP error code wins" in {
    // Option 1: change Either[Failure, Seq[T]] to Either[Seq[Failure], Seq[T]]
    // Option 2: Allow multiple failure reasons to be stored in a Failure, always favouring the highest HTTP error code
    pending
  }
}