package trivial.rest

// TODO - CAS - 23/04/15 - Introduces boilerplate. Looking for ways to remove it.
trait Resource[T <: Resource[T]] {
  def id: Option[String]

  def withId(newId: String): T

  //  def createDate: String
}