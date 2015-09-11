resolvers += Resolver.sonatypeRepo("releases")

resolvers += "twitter-repo" at "http://maven.twttr.com"

updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val productionDependencies = Seq(
  "org.json4s" %% "json4s-native" % "3.2.11"
    exclude("org.scala-lang", "scala-compiler")
  ,
  "org.json4s" %% "json4s-ext" % "3.2.11",

  // TODO - CAS - 08/09/15 - Holy shit: this is an insane dependency tree. Thanks Finatra.
  "com.twitter.finatra" %% "finatra-http" % "2.0.0.RC1" withSources(),
  "com.twitter.finatra" %% "finatra-http" % "2.0.0.RC1" % "test" withSources(),
  "com.twitter.finatra" %% "finatra-http" % "2.0.0.RC1" % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-server" % "2.0.0.RC1" % "test" withSources(),
  "com.twitter.inject" %% "inject-server" % "2.0.0.RC1" % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-app" % "2.0.0.RC1" % "test" withSources(),
  "com.twitter.inject" %% "inject-app" % "2.0.0.RC1" % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-core" % "2.0.0.RC1" % "test" withSources(),
  "com.twitter.inject" %% "inject-core" % "2.0.0.RC1" % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-modules" % "2.0.0.RC1" % "test" withSources(),
  "com.twitter.inject" %% "inject-modules" % "2.0.0.RC1" % "test" classifier "tests" withSources(),
  "com.typesafe.scala-logging" %% "scala-logging-slf4j" % "2.1.2",
  "org.slf4j" % "slf4j-simple" % "1.7.12"
  //    exclude("org.scalatest", "scalatest_2.10")
  //    exclude("com.google.code.findbugs", "jsr305")
)

val testDependencies = Seq(
  "org.scalatest" %% "scalatest" % "2.2.4" % "test",
  "junit" % "junit" % "4.11" % "test",
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
    case Some((2, scalaMajor)) if scalaMajor >= 11 => libraryDependencies.value :+ "org.scala-lang.modules" %% "scala-xml" % "1.0.5"
    case _ => libraryDependencies.value
  }
}