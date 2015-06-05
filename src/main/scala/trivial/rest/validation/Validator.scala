package trivial.rest.validation

import trivial.rest.{Failure, Resource}

trait Validator {
  def validate[T <: Resource[T]](t: Seq[T]): Either[Failure, Seq[T]] = {
    if (t.exists(_.id.isDefined)) Left(Failure.cannotContainAnId)
    else Right(t)
  }
}

class RestRulesValidator extends Validator