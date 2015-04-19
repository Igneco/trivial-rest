resolvers += Resolver.sonatypeRepo("releases")

resolvers += "twitter-repo" at "http://maven.twttr.com"

updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val productionDependencies = Seq(
  "com.github.agmenc" %% "trivial-rest" % "0.0.1",
  "com.twitter" %% "finatra" % "1.6.0"
    exclude("org.scalatest", "scalatest_2.10")
    exclude("com.google.code.findbugs", "jsr305")
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test"
)

libraryDependencies ++= productionDependencies ++ testDependencies

// fixes the "Multiple dependencies with the same organization/name but different versions" warning
libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value

// add scala-xml dependency when needed (for Scala 2.11 and newer)
// this mechanism supports cross-version publishing
libraryDependencies := {
  CrossVersion.partialVersion(scalaVersion.value) match {
    case Some((2, scalaMajor)) if scalaMajor >= 11 => libraryDependencies.value :+ "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
    case _ => libraryDependencies.value
  }
}