package trivial.rest.validation

import trivial.rest.{HardCoded, Failure, Resource}

trait Validator {
  def validate[T <: Resource[T]](t: Seq[T]): Either[Failure, Seq[T]] = {
    val idsAlreadyAllocated = t.filter(_.id.isDefined).filterNot{
      case h: HardCoded => true
      case other => false
    }
    if (idsAlreadyAllocated.nonEmpty) Left(Failure.cannotContainAnId(idsAlreadyAllocated))
    else Right(t)
  }
}

class RestRulesValidator extends Validator