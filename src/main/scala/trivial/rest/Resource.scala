package trivial.rest

import scala.reflect.ClassTag

// TODO - CAS - 23/04/15 - Introduces boilerplate. Looking for ways to remove it.
trait Resource[T <: Resource[T]] {
  def id: Option[String]

  // To return T, this has to be a self-recursive type. We can now call aCurrency.withId("x") and get a Currency back, not just a Resource.
  // Self-recursive types are not loved by all: http://logji.blogspot.se/2012/11/f-bounded-type-polymorphism-give-up-now.html
  // However, an overridden type-alias would have introduced yet more boilerplate.
  def withId(newId: Option[String]): T
}

object Resource {
  def name[T : ClassTag]: String = Classy.name[T].toLowerCase
}

// TODO - CAS - 22/06/15 - Make trying to POST one of these a compile failure
trait HardCoded {
  def withId(newId: Option[String]): this.type = this
}