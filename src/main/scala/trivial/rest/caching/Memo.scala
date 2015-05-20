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
   * Use this for parameterised methods, or anywhere else you can't use the function as a
   * key. In all other cases, the simpler memo() method, below, is probably better.
   *
   * val f = method _
   * def memoised(input: String) = memo ("allTheThings") { f } (input)
   */
  def memo[I, O](key: Any)(functionToMemorise: I => O): Cache[I, O] =
    cacheOfCaches.getOrElseUpdate(key, Cache[I, O](functionToMemorise)).asInstanceOf[Cache[I, O]]

  def unMemo(key: Any): Unit = cacheOfCaches.get(key).foreach(_.invalidate())

  /**
   * Use this for simple functions and un-parameterised methods
   *
   * BEWARE - uses the function as a key to locate the memoised cache. Does not work when the
   * functions are created on the fly, as each is a different instance.
   *
   * As per memo with a key, except the function is used as the key. Does NOT work for
   * methods which assign the function on the fly - use an explicit key via the other memo
   * method, instead.
   *
   * @param functionToMemorise the function to memoise
   * @tparam I the input parameter type of function f
   * @tparam O the output type of function f
   * @return a Cache[I, O]
   */
  def memo[I, O](functionToMemorise: I => O): Cache[I, O] =
    cacheOfCaches.getOrElseUpdate(functionToMemorise, Cache[I, O](functionToMemorise)).asInstanceOf[Cache[I, O]]
}

case class Cache[I, O](functionToMemorise: I => O) extends (I => O) {
  val cache = mutable.Map.empty[I, O]

  def apply(input: I) = cache getOrElseUpdate (input, functionToMemorise(input))

  def invalidate(): Unit = cache.clear()
}