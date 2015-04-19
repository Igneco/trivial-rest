import sbt._
import Keys._

object Build extends Build {

  def sharedSettings = Seq(
    scalaVersion:= "2.10.5",
    scalacOptions += "-deprecation",
    libraryDependencies ++= Seq(
      "com.twitter" %% "finatra" % "1.6.0",
      "junit" % "junit" % "4.11" % "test->default",
      "org.scalatest" %% "scalatest" % "2.2.0" % "test"
    ),
    // add scala-xml dependency when needed (for Scala 2.11 and newer)
    // this mechanism supports cross-version publishing
    libraryDependencies := {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, scalaMajor)) if scalaMajor >= 11 => libraryDependencies.value :+ "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
        case _ => libraryDependencies.value
      }
    }
  )

  lazy val main = Project(id = "trivial-rest", base = file(".")).settings(sharedSettings: _*)
}
