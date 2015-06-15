package trivial.rest

import trivial.rest.Classy._

import scala.reflect.ClassTag

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

// TODO - CAS - 09/06/15 - Make Classy work with Manifests as well as ClassTags.
object Classy {
  def apply[T : ClassTag]: String = name[T].toLowerCase
  def runtimeClass[T : ClassTag]: Class[_] = implicitly[ClassTag[T]].runtimeClass
  def name[T : ClassTag]: String = name(runtimeClass[T])
  def name(clazz: Class[_]): String = clazz.getSimpleName.stripSuffix("$")
}