package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.TestDirectories._
import trivial.rest.persistence.JsonOnFileSystem
import trivial.rest.serialisation.Json4sSerialiser

abstract class TestableFinatraServer extends FinatraServer {
  val testDir = provisionedTestDir
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(testDir, serialiser)
  val controller = new Controller {}
  val rest = new RestExample("/", controller, serialiser, persister)
  register(controller)
}