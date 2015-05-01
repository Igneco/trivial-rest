package trivial.rest

// TODO - CAS - 01/05/15 - find a way to know that T is a case class
trait IdReceptive {
  // override def withId(newId: String) = copy(id = Some(newId))
}

case class Foo(id: Option[String], bar: String) extends Restable[Foo] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Spaceship(id: Option[String], name: String, personnel: Int, bearing: Vector) extends Restable[Spaceship] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Vector(id: Option[String], angle: BigDecimal, magnitude: BigDecimal) extends Restable[Vector] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Planet(id: Option[String], name: String, classification: String) extends Restable[Planet] {
  override def withId(newId: String) = copy(id = Some(newId))
}