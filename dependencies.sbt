resolvers += Resolver.sonatypeRepo("releases")

resolvers += "twitter-repo" at "http://maven.twttr.com"

updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val productionDependencies = Seq(
  "com.twitter" %% "finatra" % "1.6.0"
    exclude("org.scalatest", "scalatest_2.10")
    exclude("com.google.code.findbugs", "jsr305"),
  "org.json4s" %% "json4s-native" % "3.2.11"
    exclude("org.scala-lang", "scala-compiler"),
  "org.json4s" %% "json4s-ext" % "3.2.11"
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "org.mockito" % "mockito-all" % "2.0.2-beta" % "test",
  "org.scalamock" %% "scalamock-scalatest-support" % "3.2.1" % "test"
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