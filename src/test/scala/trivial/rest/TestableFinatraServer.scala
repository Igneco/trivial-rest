package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}
import trivial.rest.TestDirectories._
import trivial.rest.persistence.JsonOnFileSystem
import trivial.rest.serialisation.Json4sSerialiser

class TestableFinatraServer(uriRoot: String = "/") extends FinatraServer {
  val testDir = provisionedTestDir
  val serialiser = new Json4sSerialiser
  val persister = new JsonOnFileSystem(testDir, serialiser)
  val controller = new Controller {}
  val rest = new RestExample(uriRoot, controller, serialiser, persister)
  register(controller)
}