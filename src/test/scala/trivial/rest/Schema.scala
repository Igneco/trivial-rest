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

case class MetricPerson(id: Option[String], name: String, heightInCentimetres: Int, bmi: BigDecimal) extends Resource[MetricPerson] {
  override def withId(newId: String) = copy(id = Some(newId))
}

// Example of an old Resource, no longer registered in Rest.
case class ImperialPerson(id: Option[String], name: String, heightInInches: Int, weightInPounds: Int) extends Resource[ImperialPerson] {
  override def withId(newId: String) = copy(id = Some(newId))
}

// Convert the original constructor into an auxiliary constructor
//  def this(id: Option[String], name: String, heightInInches: Int, weightInPounds: Int) = this(
//    id, name,
//    (heightInInches * 2.4).toInt,
//    (heightInInches/100)/((weightInPounds / 2.2) * (weightInPounds / 2.2)))

//object MetricPerson {
//  def apply(id: Option[String], name: String, heightInInches: Int, weightInPounds: Int): MetricPerson = {
//    convert(ImperialPerson(id, name, heightInInches, weightInPounds))
//  }
//

//
//  def convert(old: ImperialPerson): MetricPerson = {
//    println("CONVERT WAS CALLED")
//    import old._
//    val height = (heightInInches * 2.4).toInt
//    val weight = weightInPounds / 2.2
//    MetricPerson(id, name, height, (height /100)/(weight * weight))
//  }
//}