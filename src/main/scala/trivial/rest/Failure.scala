package trivial.rest

import org.json4s.JValue

case class Failure(statusCode: Int, reasons: Seq[String]) {
  def describe = reasons mkString "\n"
}

object Failure {
  def apply(statusCode: Int, reason: String): Failure = Failure(statusCode, Seq(reason))
}

object FailFactory {
  def validation(httpMethod: HttpMethod, reasons: Seq[String]) = Failure(403, s"Validation failure(s) during $httpMethod:\n$reasons")

  // TODO - CAS - 26/06/15 - Pretty-print parsed?
  def unexpectedJson(parsed: JValue) = Failure(400,
    s"Received data was not in the form of a JSON array of resource objects. Data received parses to:\n$parsed")

  def deserialisation(e: Exception) = Failure(500, s"Failed to deserialise into [T], due to: $e")

  def persistCreate(path: String, trace: String) = persistAny(path, trace, "create")
  def persistUpdate(path: String, trace: String) = persistAny(path, trace, "update")
  private def persistAny(path: String, trace: String, flavour: String) = Failure(500, s"Failed to $flavour resources at $path: $trace")
}