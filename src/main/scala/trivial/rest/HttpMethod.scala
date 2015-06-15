package trivial.rest

import trivial.rest.Classy._

trait HttpMethod {
  override def toString = name(this.getClass).toUpperCase
}

case object GetAll extends HttpMethod {
  override def toString = "GET all"
}

case object Get extends HttpMethod
case object Put extends HttpMethod
case object Post extends HttpMethod
case object Delete extends HttpMethod

object HttpMethod {
  def all: Set[HttpMethod] = Set(GetAll, Get, Put, Post, Delete)
}

