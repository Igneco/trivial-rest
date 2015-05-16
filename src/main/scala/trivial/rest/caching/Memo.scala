package trivial.rest.caching

import collection.mutable.Map

trait Memo {
  val cacheOfCaches = Map.empty[Function1[_, _], Cache[_, _]]

  def memo[I, O](f: I => O): Cache[I, O] =
    cacheOfCaches.getOrElseUpdate(f, Cache[I, O](f)).asInstanceOf[Cache[I, O]]
}

case class Cache[I, O](f: I => O) extends (I => O) {
  val cache = Map.empty[I, O]

  def apply(x: I) = cache getOrElseUpdate (x, f(x))
}