package trivial.rest.persistence

import trivial.rest.{Failure, Resource}

import scala.reflect.ClassTag

trait Persister {
  def save[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T]): Either[Failure, Int]
  def loadAll[T <: Resource[T] : Manifest](resourceName: String): Either[Failure, Seq[T]]
  def nextSequenceId: String
  def formatSequenceId(id: Int): String

  // loadAll, backup, migrate data, save
  def migrate[T <: Resource[T] : ClassTag : Manifest](forward: (T) => T, oldApiPath: Option[String]): Either[Failure, Int]
}