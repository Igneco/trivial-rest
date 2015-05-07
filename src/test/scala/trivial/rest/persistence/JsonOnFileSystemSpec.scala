package trivial.rest.persistence

import org.json4s.native.Serialization
import org.json4s.{Formats, NoTypeHints}
import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}
import trivial.rest.TestDirectories._
import trivial.rest.serialisation.Serialiser
import trivial.rest.{Currency, ExchangeRate, Foo}

import scala.reflect.io.{Directory, File}

class JsonOnFileSystemSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {

  override protected def beforeAll() = cleanTestDirs()
  override protected def afterAll()  = beforeAll()

  implicit val formats: Formats = Serialization.formats(NoTypeHints)

  "TODO - Meets the PersisterContract terms" in {
    // new PersisterContract(new JsonOnFileSystem("target/test"))
    pending
  }
  
  "We can loadAll when the data file exists but is empty" in {
    val docRoot = Directory(nextTestDir)
    docRoot.createDirectory()
    File(docRoot / "foo.json")
      .createFile()
      .writeAll( "[]")

    new JsonOnFileSystem(docRoot).loadAll("foo") mustEqual Right(Seq[Foo]())
  }
  
  "We can loadAll" in {
    val docRoot = Directory(nextTestDir)
    docRoot.createDirectory()
    File(docRoot / "foo.json")
      .createFile()
      .writeAll( """[{"id":"1","bar":"bar"}]""")

    new JsonOnFileSystem(docRoot).loadAll[Foo]("foo") mustEqual Right(Seq(Foo(Some("1"), "bar")))
  }
  
  "We can deserialise (load) resources which contain ID references to other resources" in {
    pending
    val docRoot = Directory(nextTestDir)
    docRoot.createDirectory()
    val resourcesDir = Directory("src/test/resources")
    FileSystem.copy(File(resourcesDir / "currency.json"), File(docRoot / "currency.json"))
    FileSystem.copy(File(resourcesDir / "exchangerate.json"), File(docRoot / "exchangerate.json"))

    val expected = Seq(
      ExchangeRate(Some("1"), 33.3, Currency(Some("2"), "GBP", "£")),
      ExchangeRate(Some("2"), 44.4, Currency(Some("3"), "USD", "$"))
    )

    implicit val formats = Serialization.formats(NoTypeHints) +
      Serialiser[ExchangeRate](_.id.getOrElse(""), _ => None) +
      Serialiser[Currency](_.id.getOrElse(""), _ => None)

    new JsonOnFileSystem(docRoot).loadAll[ExchangeRate]("exchangerate") mustEqual Right(expected)
  }

  "We always persist resources as flat resources, with ID-references to component resources" in {
    pending
  }

  "If a doc root doesn't exist, it is created" in {
    val docRoot = Directory(nextTestDir)
    docRoot.exists mustBe false

    new JsonOnFileSystem(docRoot).save("foo", Seq(Foo(None, "bar")))

    docRoot.exists mustBe true
  }

  "If a data file doesn't exist, it is created" in {
    val docRoot = Directory(nextTestDir)
    val targetFile = docRoot / "record.json"
    val jofs = new JsonOnFileSystem(docRoot)
    targetFile.exists mustBe false

    jofs.save("record", Seq(Foo(None, "bar")))

    targetFile.exists mustBe true
  }

  "Saving a record adds it to the data file" in {
    val docRoot = Directory(nextTestDir)
    val targetFile: File = File(docRoot / "foo.json")
    val jofs = new JsonOnFileSystem(docRoot)
    targetFile.exists mustBe false

    val one = jofs.save("foo", Seq(Foo(Some("1"), "bar")))
    val two = jofs.save("foo", Seq(Foo(Some("2"), "baz")))

    targetFile.slurp() mustEqual """[{"id":"1","bar":"bar"},{"id":"2","bar":"baz"}]"""
  }
  
  "Sequence numbers are stored in a write-behind file on disk" in {
    val docRoot = Directory(nextTestDir)
    val jofs = new JsonOnFileSystem(docRoot)

    jofs.nextSequenceNumber mustBe 1
    jofs.nextSequenceNumber mustBe 2
  }
  
  // "Adding a second record appends to the data file" in {}
}