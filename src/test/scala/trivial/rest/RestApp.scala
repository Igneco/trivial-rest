package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}

object RestApp extends FinatraServer {
  val app = new Controller with Rest {
    resource[Spaceship](GetAll)
    resource[Vector](GetAll)
  }
  register(app)
}