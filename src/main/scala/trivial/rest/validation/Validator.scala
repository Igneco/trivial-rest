package trivial.rest.validation

import trivial.rest.{Failure, Restable}

trait Validator {
  def validate[T <: Restable](t: Seq[T]): Either[Failure, Seq[T]] = {
    if (t.exists(_.id.isDefined)) Left(Failure(403, "You can't POST an item with an ID - the system will allocate an ID upon resource creation"))
    else Right(t)
  }
}

class RestRulesValidator extends Validator