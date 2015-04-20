package trivial.rest

import com.twitter.finatra.{Controller, FinatraServer}

class RestApp extends Controller with Rest {
  resource[Spaceship](GetAll)
  resource[Vector](GetAll)
}

object RestApp extends FinatraServer {
  register(new RestApp)
}