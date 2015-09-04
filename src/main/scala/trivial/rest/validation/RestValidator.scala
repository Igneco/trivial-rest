package trivial.rest.validation

import trivial.rest._

trait RestValidator {
  def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod): Either[Failure, Seq[T]]
}

trait RestValidationRule {
  def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod): Seq[Failure]
}

class RuleBasedRestValidator(rules: Seq[RestValidationRule] = Seq(CommonRules.newResourcesCannotHaveAnId, CommonRules.resourcesToUpdateMustHaveAnId)) extends RestValidator {
  def withRules[T <: Resource[T]](extraRules: RestValidationRule*) = new RuleBasedRestValidator(rules ++ extraRules)

  override def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod): Either[Failure, Seq[T]] =
    rules flatMap (rule => rule.validate(resources, httpMethod)) match {
      case Nil => Right(resources)
      case failures => Left(compress(failures))
    }

  private def compress(fail: Seq[Failure]): Failure = fail reduceLeft flatten

  private def flatten(acc: Failure, next: Failure): Failure = next.copy(statusCode = Math.max(acc.statusCode, next.statusCode), reasons = acc.reasons ++ next.reasons)
}

object CommonRules {
  def noDuplicates[T <: Resource[T]](allT: => Seq[T]): RestValidationRule = new RestValidationRule {
    val withoutIds = allT.map(_.withId(None))

    override def validate[T](resources: Seq[T], httpMethod: HttpMethod): Seq[Failure] =
      (resources filter withoutIds.contains) map (t => Failure(409, s"A matching item already exists: $t"))
  }

  def resourcesToUpdateMustHaveAnId: RestValidationRule = new RestValidationRule {
    override def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod) =
      resources collect {
        case r if r.id.isEmpty && httpMethod == Put => Failure(409, s"Resource to update must have an ID")
      }
  }

  val noId = "You can't POST an item with an ID; the system will allocate an ID upon resource creation. Offending ID:"

  def newResourcesCannotHaveAnId: RestValidationRule = new RestValidationRule {
    override def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod) = {
      val idsAlreadyAllocated: Seq[T] = resources.filter(_.id.isDefined).filterNot{
        case h: HardCoded => true
        case other => false
      }

      if (httpMethod == Post) idsAlreadyAllocated map {t =>
        Failure(409, s"$noId ${t.id.getOrElse("None")}")
      }
      else Nil
    }
  }
}