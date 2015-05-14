package trivial.rest.persistence

import org.json4s.native.Serialization
import org.json4s.{Formats, NoTypeHints}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.serialisation.{Serialiser, Json4sSerialiser, ResourceSerialiser}
import trivial.rest._

import scala.reflect.ClassTag
import scala.reflect.io.{Directory, File}

class JsonOnFileSystemSpec extends WordSpec with MustMatchers with BeforeAndAfterAll with MockFactory {

  override protected def beforeAll() = cleanTestDirs()
  override protected def afterAll()  = beforeAll()

  implicit val formats: Formats = Serialization.formats(NoTypeHints)
  val serialiser = new Json4sSerialiser

  "TODO - Meets the PersisterContract terms" in {
    // new PersisterContract(new JsonOnFileSystem("target/test"))
    pending
  }

  "We can loadAll when the data file exists but is empty" in {
    val docRoot = nextTestDir
    File(docRoot / "foo.json")
      .createFile()
      .writeAll( "[]")

    new JsonOnFileSystem(docRoot, serialiser).loadAll("foo") mustEqual Right(Seq[Foo]())
  }

  "We can loadAll" in {
    val docRoot = nextTestDir
    File(docRoot / "foo.json")
      .createFile()
      .writeAll( """[{"id":"1","bar":"bar"}]""")

    new JsonOnFileSystem(docRoot, serialiser).loadAll[Foo]("foo") mustEqual Right(Seq(Foo(Some("1"), "bar")))
  }

  "We delegate deserialisation to the Serialiser" in {
    val docRoot = nextTestDir
    File(docRoot / "exchangerate.json")
      .createFile()
      .writeAll("<Stuff loaded from disk>")

    val expected = Seq(
      ExchangeRate(Some("1"), 33.3, Currency(Some("2"), "GBP", "£")),
      ExchangeRate(Some("2"), 44.4, Currency(Some("3"), "USD", "$"))
    )

    val serialiserMock: Serialiser = mock[Serialiser]

    val jofs = new JsonOnFileSystem(docRoot, serialiserMock)

    def serialiser_expects_deserialise[T <: Resource[T] : ClassTag](body: String, returns: Seq[T]) = {
      (serialiserMock.deserialise[T](_: String)(_: Manifest[T])).expects(body, *).returning(Right(returns))
    }

    serialiser_expects_deserialise("<Stuff loaded from disk>", expected)

    jofs.loadAll[ExchangeRate]("exchangerate")
  }

  "We always persist resources as flat resources, with ID-references to component resources" in {
    pending
  }

  "If a doc root doesn't exist, it is created" in {
    val docRoot = Directory(nextTestDirPath)
    docRoot.exists mustBe false

    new JsonOnFileSystem(docRoot, serialiser).save("foo", Seq(Foo(None, "bar")))

    docRoot.exists mustBe true
  }

  "If a data file doesn't exist, it is created" in {
    val docRoot = nextTestDir
    val targetFile = docRoot / "record.json"
    val jofs = new JsonOnFileSystem(docRoot, serialiser)
    targetFile.exists mustBe false

    jofs.save("record", Seq(Foo(None, "bar")))

    targetFile.exists mustBe true
  }

  "Saving a record adds it to the data file" in {
    val docRoot = nextTestDir
    val jofs = new JsonOnFileSystem(docRoot, serialiser)

    jofs.save("foo", Seq(Foo(Some("1"), "bar")))
    jofs.save("foo", Seq(Foo(Some("2"), "baz")))

    File(docRoot / "foo.json").slurp() mustEqual """[{"id":"1","bar":"bar"},{"id":"2","bar":"baz"}]"""
  }

  "Each successive item gets a new, unique, sequence ID" in {
    val docRoot = nextTestDir
    val jofs = new JsonOnFileSystem(docRoot, serialiser)

    jofs.nextSequenceNumber mustBe 1
    jofs.nextSequenceNumber mustBe 2
  }

  // "Adding a second record appends to the data file" in {}
}