package trivial.rest

import scala.math.BigDecimal.RoundingMode

// Case class auxiliary constructors can't call out to converter functions in a companion object: it must be an unrelated object.
object ImperialAndMetricConverter {
  def inchesToMetres(inches: Int): Double = inches * 2.54 / 100
  def poundsToKilograms(pounds: Int): Double = pounds / 2.2
  def calcBmi(heightInMetres: Double, weightInKilograms: Double): BigDecimal =
    nDecimalPlaces(1, weightInKilograms / (heightInMetres * heightInMetres))

  def viewAsImperial(mp: MetricPerson): ImperialPerson = {
    val heightInInches: Double = mp.heightInCentimetres / 2.54
    val heightInMetres: Double = mp.heightInCentimetres / 100.0

    val weight: Int = roundToInt(mp.bmi.toDouble * heightInMetres * heightInMetres * 2.2)

    ImperialPerson(mp.id, mp.name, heightInInches, weight)
  }

  implicit def roundToInt(double: Double): Int = nDecimalPlaces(0, double).toInt
  implicit def nDecimalPlaces(decimalPlaces: Int, double: Double) = BigDecimal(double).setScale(decimalPlaces, RoundingMode.HALF_UP)
}

// Example of a former Resource, no longer registered in Rest, but retained for backwards-compatibility
case class ImperialPerson(id: Option[String], name: String, heightInInches: Int, weightInPounds: Int)