package trivial.rest.persistence

import org.json4s.JsonAST.JNothing
import org.json4s.native.Serialization
import org.json4s.{Formats, NoTypeHints}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.serialisation.{Serialiser, Json4sSerialiser, TypeSerialiser}
import trivial.rest._

import scala.reflect.ClassTag
import scala.reflect.io.{Path, Directory, File}

class JsonOnFileSystemSpec extends WordSpec with MustMatchers with MockFactory {

  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  val serialiser = new Json4sSerialiser

  "TODO - Meets the PersisterContract terms" in {
    // new PersisterContract(new JsonOnFileSystem("target/test"))
    pending
  }

  "We can loadAll when the data file exists but is empty" in {
    val docRoot = nextTestDir
    writeTestData("[]", docRoot / "foo.json")

    new JsonOnFileSystem(docRoot, serialiser).read[Foo]("foo") mustEqual Right(Seq[Foo]())
  }

  def writeTestData(data: String, target: Path) = File(target).createFile().writeAll(data)

  "We can loadAll" in {
    val docRoot = nextTestDir
    writeTestData( """[{"id":"1","bar":"bar"}]""", docRoot / "foo.json")

    new JsonOnFileSystem(docRoot, serialiser).read[Foo]("foo") mustEqual Right(Seq(Foo(Some("1"), "bar")))
  }

  trait MockedSerialiser {
    val serialiserMock: Serialiser = mock[Serialiser]
    val anyJson = JNothing.asInstanceOf[serialiserMock.JsonRepresentation]

    def serialiser_expects_deserialise_complete[T <: Resource[T] : ClassTag](body: String, returns: Seq[T]) = {
      (serialiserMock.deserialiseToJson[T](_: String)(_: Manifest[T])).expects(body, *).returning(anyJson)
      (serialiserMock.deserialiseToType[T](_: serialiserMock.JsonRepresentation)(_: Manifest[T])).expects(anyJson, *).returning(Right(returns))
    }
  }

  "We delegate deserialisation to the Serialiser" in new MockedSerialiser {
    val docRoot = nextTestDir
    writeTestData("<Stuff loaded from disk>", docRoot / "exchangerate.json")

    val expected = Seq(
      ExchangeRate(Some("1"), 33.3, Currency(Some("2"), "GBP", "£")),
      ExchangeRate(Some("2"), 44.4, Currency(Some("3"), "USD", "$"))
    )

    val jofs = new JsonOnFileSystem(docRoot, serialiserMock)

    serialiser_expects_deserialise_complete("<Stuff loaded from disk>", expected)

    jofs.read[ExchangeRate]("exchangerate")
  }

  "Memoising loadAll means we only hit the serialiser once per type" in new MockedSerialiser {
    val docRoot = nextTestDir
    writeTestData("<Stuff loaded from disk>", docRoot / "exchangerate.json")

    val expected = Seq(
      ExchangeRate(Some("1"), 33.3, Currency(Some("2"), "GBP", "£")),
      ExchangeRate(Some("2"), 44.4, Currency(Some("3"), "USD", "$"))
    )

    val jofs = new JsonOnFileSystem(docRoot, serialiserMock)

    serialiser_expects_deserialise_complete("<Stuff loaded from disk>", expected)

    jofs.read[ExchangeRate]("exchangerate")
    jofs.read[ExchangeRate]("exchangerate")
    jofs.read[ExchangeRate]("exchangerate")
  }

  "If a doc root doesn't exist, it is created" in {
    val docRoot = Directory(nextTestDirPath)
    docRoot.exists mustBe false

    new JsonOnFileSystem(docRoot, serialiser).create("foo", Seq(Foo(None, "bar")))

    docRoot.exists mustBe true
  }

  "If a data file doesn't exist, it is created" in {
    val docRoot = nextTestDir
    val targetFile = docRoot / "record.json"
    val jofs = new JsonOnFileSystem(docRoot, serialiser)
    targetFile.exists mustBe false

    jofs.create("record", Seq(Foo(None, "bar")))

    targetFile.exists mustBe true
  }

  "Saving a record adds it to the data file" in {
    val docRoot = nextTestDir
    val jofs = new JsonOnFileSystem(docRoot, serialiser)

    jofs.create("foo", Seq(Foo(Some("1"), "bar")))
    jofs.create("foo", Seq(Foo(Some("2"), "baz")))

    File(docRoot / "foo.json").slurp() mustEqual """[{"id":"1","bar":"bar"},{"id":"2","bar":"baz"}]"""
  }

  "Each successive item gets a new, unique, zero-padded sequence ID" in {
    val docRoot = nextTestDir
    val jofs = new JsonOnFileSystem(docRoot, serialiser)

    jofs.nextSequenceId mustBe "0000001"
    jofs.nextSequenceId mustBe "0000002"
  }

  "We can disallow the persistence of duplicate resources of a specific type" in {
    val docRoot = nextTestDir
    val customValidator = new PersistenceValidator {
      override def validate[T <: Resource[T] : Manifest](resourceName: String, resources: Seq[T], persister: Persister) = {
        resources match {
          case h +: t => h match {
            case f: Foo => assertNoDupes(resources, persister.read[T](resourceName))
          }
          case Nil => Right(resources)
        }
      }

      def assertNoDupes[T <: Resource[T]](resources: Seq[T], preExisting: Either[Failure, Seq[T]]): Either[Failure, Seq[T]] =
        preExisting.right.flatMap { pres =>
          resources.find(pres.contains).fold[Either[Failure, Seq[T]]](Right(resources))(foo => Left(Failure(409, s"Duplicate resource cannot be created: $foo")))
        }
    }

    val jofs = new JsonOnFileSystem(docRoot, serialiser, customValidator)

    jofs.create("foo", Seq(Foo(Some("1"), "bar")))
    jofs.create("foo", Seq(Foo(Some("1"), "bar"))) mustEqual Left(Failure(409, "Duplicate resource cannot be created: Foo(Some(1),bar)"))

//    jofs.create("foo", Seq(Foo(Some("2"), "baz")))
//    jofs.create("foo", Seq(Foo(None, "baz"))) mustEqual Left(Failure(409, "Duplicate resource cannot be created: Foo(Some(1),bar)"))

//    jofs.create("ccy", Seq(Currency(Some("1"), "NZD", "$")))
//    jofs.create("ccy", Seq(Currency(Some("1"), "NZD", "$"))) mustEqual 1
  }

  "Exceptions are stored in the /exception subfolder" in {
    pending
  }

  "Migrations are stored in the /migration subfolder" in {
    pending
  }

//  "Migrating a Resource type to a new name changes the name of the target file" in { fail("Nope") }
//
//  "We create a copy of the data file before we migrate it" in { fail("Nope") }

  // "Adding a second record appends to the data file" in {}
}