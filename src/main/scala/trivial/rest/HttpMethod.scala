package trivial.rest

trait HttpMethod
case object GetAll extends HttpMethod
case object Get extends HttpMethod
case object Put extends HttpMethod
case object Post extends HttpMethod
case object Delete extends HttpMethod