package trivial.rest.persistence

import trivial.rest.{Failure, Resource}

import scala.reflect.ClassTag

trait Persister {
  def create[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T]): Either[Failure, Int]

  def read[T <: Resource[T] : Manifest](resourceName: String, params: Map[String, String] = Map.empty): Either[Failure, Seq[T]]
  def read[T <: Resource[T] : Manifest](resourceName: String, id: String): Either[Failure, Seq[T]]

  def update[T <: Resource[T] : Manifest](resourceName: String, content: Seq[T]): Either[Failure, Int]

  def delete[T <: Resource[T] : Manifest](resourceName: String, id: String): Either[Failure, Int]

  def nextSequenceId: String
  def formatSequenceId(id: Int): String

  // loadAll, backup, migrate data, save
  def migrate[T <: Resource[T] : ClassTag : Manifest](forward: (T) => T, oldApiPath: Option[String]): Either[Failure, Int]
}

trait PersistenceValidator {
  def validate[T <: Resource[T] : Manifest](resourceName: String, resources: Seq[T], persister: Persister): Either[Failure, Seq[T]]
}

object CheckNothing extends PersistenceValidator {
  override def validate[T <: Resource[T] : Manifest](resourceName: String, resources: Seq[T], persister: Persister) = Right(resources)
}

abstract class DefaultValidator extends PersistenceValidator {
  override def validate[T <: Resource[T] : Manifest](resourceName: String, resources: Seq[T], persister: Persister) = {
    resources match {
      case h +: t => assertionsByType(h)
      case Nil => Right(resources)
    }
  }

  def assertionsByType[T]: PartialFunction[T, Either[Failure, Seq[T]]]

  def assertNoDupes[T <: Resource[T]](resources: Seq[T], preExisting: Either[Failure, Seq[T]]): Either[Failure, Seq[T]] =
    preExisting.right.flatMap { pres =>
      resources.find(pres.contains).fold[Either[Failure, Seq[T]]](Right(resources))(foo => Left(Failure(500, s"Duplicate resource cannot be created: $foo")))
    }
}