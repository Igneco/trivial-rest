package trivial.rest

import org.json4s.JValue

case class Failure(statusCode: Int, reason: String)

object Failure {
  def notAnArray(body: String, parsed: JValue) = Failure(400, s"Received data was not in the form of a JSON array of resource objects. Data received was:\n$body\nand parses to:$parsed")
}