package trivial.rest.persistence

import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}

import scala.reflect.io.{File, Path, Directory}

class JsonOnFileSystemSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {
  val testRoot = Directory("target/json")

  "Meets the PersisterContract terms" in {
//    new PersisterContract(new JsonOnFileSystem("target/test"))
    fail("train time")
  }
  
  "If a doc root doesn't exist, it is created" in {
    val docRoot = Directory(testRoot / "monkeys")
    docRoot.exists mustBe false

    new JsonOnFileSystem(docRoot).save("foo", "bar")

    docRoot.exists mustBe true
  }

  "If a data file doesn't exist, it is created" in {
    val docRoot = Directory(testRoot / "foo")
    val targetFile = docRoot / "record.json"
    val jofs = new JsonOnFileSystem(docRoot)
    targetFile.exists mustBe false
    
    jofs.save("record", """{"name": "bob}""")
    
    targetFile.exists mustBe true
  }

  "Saving a record adds it to the data file" in {
    val docRoot = Directory(testRoot / "bar")
    val targetFile: File = File(docRoot / "record.json")
    val jofs = new JsonOnFileSystem(docRoot)
    targetFile.exists mustBe false

    jofs.save("record", """{"name": "bob}""")

    targetFile.slurp() mustEqual """{"name": "bob}"""
  }
  
  // "Adding a second record appends to the data file" in {}

  override protected def beforeAll() = testRoot.deleteRecursively()
  override protected def afterAll()  = beforeAll()
}
