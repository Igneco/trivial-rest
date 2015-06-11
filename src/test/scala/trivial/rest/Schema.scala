package trivial.rest

import java.math.MathContext

import trivial.rest.ImperialToMetricConverter._

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

  // Auxiliary constructor matching old data structure
  def this(id: Option[String], name: String, heightInInches: Int, weightInPounds: Int) =
    this(
      id, name,
      (inchesToMetres(heightInInches) * 100).toInt,
      calcBmi(inchesToMetres(heightInInches), poundsToKilograms(weightInPounds))
    )
}

// json4s requires you to declare a companion object, in addition to the auxiliary case class constructor defined above
object MetricPerson

// Case class auxiliary constructors can't call out to converter functions in a companion object: it must be an unrelated object.
object ImperialToMetricConverter {
  def inchesToMetres(inches: Int): Double = (inches * 2.54)/100
  def poundsToKilograms(pounds: Int): Double = pounds / 2.2
  def calcBmi(heightInMetres: Double, weightInKilograms: Double): BigDecimal = {
    val unrounded: BigDecimal = weightInKilograms / (heightInMetres * heightInMetres)
    unrounded.round(new MathContext(3))
  }
}