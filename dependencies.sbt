resolvers += Resolver.sonatypeRepo("releases")

resolvers += "twitter-repo" at "http://maven.twttr.com"

resolvers += "Tim Tennant's repo" at "http://dl.bintray.com/timt/repo/"

updateOptions := updateOptions.value.withCachedResolution(cachedResoluton = true)

val finatraVersion = "2.0.1"

val productionDependencies = Seq(
  "org.json4s" %% "json4s-native" % "3.2.11"
    exclude("org.scala-lang", "scala-compiler")
  ,
  "org.json4s" %% "json4s-ext" % "3.2.11",

  "io.shaka" %% "naive-http" % "73",

  // TODO - CAS - 28/09/15 - Put each set of framework/library dependencies into a separate sbt file
  // TODO - CAS - 28/09/15 - Make these provided() dependencies, so that they don't pollute transitively
  // TODO - CAS - 08/09/15 - Holy shit: this is an insane dependency tree. Thanks Finatra.
  "com.twitter.finatra" %% "finatra-http" % finatraVersion withSources(),
  "com.twitter.finatra" %% "finatra-http" % finatraVersion % "test" withSources(),
  "com.twitter.finatra" %% "finatra-http" % finatraVersion % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-server" % finatraVersion % "test" withSources(),
  "com.twitter.inject" %% "inject-server" % finatraVersion % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-app" % finatraVersion % "test" withSources(),
  "com.twitter.inject" %% "inject-app" % finatraVersion % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-core" % finatraVersion % "test" withSources(),
  "com.twitter.inject" %% "inject-core" % finatraVersion % "test" classifier "tests" withSources(),
  "com.twitter.inject" %% "inject-modules" % finatraVersion % "test" withSources(),
  "com.twitter.inject" %% "inject-modules" % finatraVersion % "test" classifier "tests" withSources(),
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