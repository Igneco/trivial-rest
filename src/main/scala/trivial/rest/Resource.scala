package trivial.rest

// TODO - CAS - 23/04/15 - Introduces boilerplate. Looking for ways to remove it.
trait Resource[T <: Resource[T]] {
  def id: Option[String]

  // To return T, this has to be a self-recursive type. We can now call aCurrency.withId("x") and get a Currency back, not just a Resource.
  // Self-recursive types are not loved by all: http://logji.blogspot.se/2012/11/f-bounded-type-polymorphism-give-up-now.html
  // However, an overridden type-alias would have introduced yet more boilerplate.
  def withId(newId: String): T

  //  def createDate: String
}