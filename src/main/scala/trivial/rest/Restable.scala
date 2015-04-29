package trivial.rest

// TODO - CAS - 23/04/15 - Introduces boilerplate. Looking for ways to remove it.
trait Restable {
  def id: Option[String]

  // TODO - CAS - 27/04/15 - Can't use this.type here, because it returns a new instance. Ideas?
  def withId(newId: String): Restable

  //  def createDate: String
}