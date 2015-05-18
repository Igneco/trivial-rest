package trivial.rest.caching

import scala.collection.mutable

/**
 * Usage:
 *
 * class MemoDemo extends Memo {
 *    // Functions:
 *    private val func = (input: String) => { /* Do something expensive here */ }
 *    def memoisedFunction(input: String) = memo { func } (input)
 *
 *    // Methods:
 *    private def method(input: String) = { /* Do something expensive here */ }
 *    val f = method _  // Assign this explicitly, not when calling memo (below)
 *    def memoisedMethod(input: String) = memo { f } (input)
 * }
 */
trait Memo {
  val cacheOfCaches = mutable.Map.empty[Any, Cache[_, _]]

  /**
   * BEWARE - if you want to convert a method to a function, you must assign the function
   * outside the call to memo. Otherwise, memoisation doesn't work and the method gets called
   * every time. Correct usage:
   *
   * val f = method _
   * def memoised(input: String) = memo { f } (input)
   *
   * @param key A name or number or object to identify this cache
   * @param f the function to memoise
   * @tparam I the input parameter type of function f
   * @tparam O the output type of function f
   * @return a Cache[I, O]
   */
  def memo[I, O](key: Any)(f: I => O): Cache[I, O] =
    cacheOfCaches.getOrElseUpdate(key, Cache[I, O](f)).asInstanceOf[Cache[I, O]]
}

case class Cache[I, O](f: I => O) extends (I => O) {
  val cache = mutable.Map.empty[I, O]

  def apply(x: I) = cache getOrElseUpdate (x, f(x))

  def invalidate(): Unit = cache.clear()
}