package trivial.rest

import org.json4s.JValue

case class Failure(statusCode: Int, reason: String)

object Failure {
  def cannotContainAnId[T](idsAlreadyAllocated: Seq[T]) = Failure(403, s"Validation failure. You can't POST an item with an ID - the system will allocate an ID upon resource creation. Offending item(s):${idsAlreadyAllocated.mkString("\n    ")}")

  def notAnArray(body: String, parsed: JValue) = Failure(400,
    s"Received data was not in the form of a JSON array of resource objects. Data received was:\n\n$body\n\nand parses to:$parsed")

  def deserialisation(e: Exception) = Failure(500, s"Failed to deserialise into [T], due to: $e")

  def persistence(path: String, trace: String) = Failure(500, s"Failed to persist at $path: $trace")
}