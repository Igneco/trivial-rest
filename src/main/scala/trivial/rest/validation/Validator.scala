package trivial.rest.validation

import trivial.rest._

trait Validator {
  // TODO - CAS - 03/07/15 - Implementor should define a series of validation PartialFunctions. Thus:
  //  resources.collect(/*allTheValidations*/) can gather all the error messages

//  def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod): Either[Failure, Seq[T]] = {
//    val failureReasons: Seq[String] = for {
//      resource <- resources
//      validation <- validations
//      fail <- validation(resource, httpMethod)
//    } yield fail
//
//    if (failureReasons.nonEmpty) Left(Failure.validation(httpMethod, failureReasons))
//    else Right(resources)
//  }

  def validate[T <: Resource[T]](resources: Seq[T], httpMethod: HttpMethod): Either[Failure, Seq[T]] = {
    // TODO - CAS - 03/07/15 - Split this out into a validation, and partial match on (t, httpMethod). Check that a Resource to PUT has an ID.
    if (httpMethod == Put) {
      if (resources.forall(_.id.isDefined)) Right(resources) else Left(Failure(403, "Cannot update a Resource without an ID"))
    } else {
      val idsAlreadyAllocated = resources.filter(_.id.isDefined).filterNot{
        case h: HardCoded => true
        case other => false
      }
      if (idsAlreadyAllocated.nonEmpty) Left(cannotContainAnId(idsAlreadyAllocated))
      else Right(resources)
    }
  }

  private def cannotContainAnId[T](idsAlreadyAllocated: Seq[T]) = Failure(403, s"Validation failure. You can't POST an item with an ID - the system will allocate an ID upon resource creation. Offending item(s):${idsAlreadyAllocated.mkString("\n    ")}")
}

class RestRulesValidator extends Validator