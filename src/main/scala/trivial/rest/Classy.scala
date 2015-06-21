package trivial.rest

import scala.reflect.ClassTag

object Classy {
  def runtimeClass[T : ClassTag]: Class[_] = implicitly[ClassTag[T]].runtimeClass
  def name[T : ClassTag]: String = name(runtimeClass[T])
  def name(clazz: Class[_]): String = clazz.getSimpleName.stripSuffix("$")
}