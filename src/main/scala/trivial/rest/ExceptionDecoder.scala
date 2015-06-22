package trivial.rest

import java.io.{PrintWriter, StringWriter}

object ExceptionDecoder {
  def readable(t: Throwable) = s"${t.getMessage}\n${trace(t)}"

  def trace(t: Throwable) = {
    val sw = new StringWriter()
    t.printStackTrace(new PrintWriter(sw))
    sw.toString
  }

  def huntCause(e: Throwable, causes: Seq[String]): String = Option(e.getCause) match {
    case Some(throwable) => huntCause(throwable, causes :+ e.getMessage)
    case None => (causes :+ e.getMessage).mkString("\n") + "\n" + trace(e)
  }
}