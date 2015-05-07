package trivial.rest

// TODO - CAS - 01/05/15 - find a way to know that T is a case class
// See Travis Brown's answer at http://stackoverflow.com/questions/13446528/howto-model-named-parameters-in-method-invocations-with-scala-macros/13447439#13447439

case class Foo(id: Option[String], bar: String) extends Resource[Foo] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Currency(id: Option[String], isoName: String, symbol: String) extends Resource[Currency] {
  override def withId(newId: String) = copy(id = Some(newId))
}

// TODO - CAS - 06/05/15 - Needs a date -> Joda time de/serialisers
case class ExchangeRate(id: Option[String], rate: BigDecimal, currency: Currency) extends Resource[ExchangeRate] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Spaceship(id: Option[String], name: String, personnel: Int, bearing: Vector) extends Resource[Spaceship] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Vector(id: Option[String], angle: BigDecimal, magnitude: BigDecimal) extends Resource[Vector] {
  override def withId(newId: String) = copy(id = Some(newId))
}

case class Planet(id: Option[String], name: String, classification: String) extends Resource[Planet] {
  override def withId(newId: String) = copy(id = Some(newId))
}