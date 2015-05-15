package trivial.rest.caching

// See http://stackoverflow.com/questions/25129721/scala-memoization-how-does-this-scala-memo-work

/**
 * Generic way to create memoized functions (even recursive and multiple-arg ones)
 *
 * @param f the function to memoize
 * @tparam K the keys we should use in cache instead of I
 * @tparam O output of f
 */
case class Memo[K, O](f: K => O) extends (K => O) {
  import collection.mutable.{Map => Dict}
  val cache = Dict.empty[K, O]
  override def apply(x: K) = cache getOrElseUpdate (x, f(x))
}

object Memo {
  def memo[K, O](f: K => O): Memo[K, O] = Memo(f)
}