package trivial.rest.persistence

import org.scalatest.{BeforeAndAfterAll, MustMatchers, WordSpec}

import scala.reflect.io.Directory

class JsonOnFileSystemSpec extends WordSpec with MustMatchers with BeforeAndAfterAll {
  val docRoot = Directory("target/json")

  "Meets the PersisterContract terms" in {
//    new PersisterContract(new JsonOnFileSystem("target/test"))
    fail("train time")
  }
  
  "If a doc root doesn't exist, it is created" in {
    val monkeys = Directory(docRoot / "monkeys")
    monkeys.exists mustBe false

    new JsonOnFileSystem(monkeys).save("foo", "bar")

    monkeys.exists mustBe true
  }
  
  "Saving a new resource adds it to the relevant file" in {
    new JsonOnFileSystem(Directory(docRoot / "bob"))
  }

  override protected def beforeAll() = docRoot.deleteRecursively()
  override protected def afterAll()  = beforeAll()
}
