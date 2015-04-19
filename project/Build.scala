import sbt._
import Keys._

object Build extends Build {

  lazy val main = Project(id = "trivial-rest", base = file("."))
}
