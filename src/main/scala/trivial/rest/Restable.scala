package trivial.rest

// TODO - CAS - 23/04/15 - Introduces boilerplate. Looking for ways to remove it.
trait Restable {
  def id: Option[String]
  def withId(id: String): Restable
  //  def createDate: String
}