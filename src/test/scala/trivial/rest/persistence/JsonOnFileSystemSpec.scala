package trivial.rest.persistence

import org.scalatest.{MustMatchers, WordSpec}

import scala.reflect.io.Directory

class JsonOnFileSystemSpec extends WordSpec with MustMatchers {
  "Meets the PersisterContract terms" in {
//    new PersisterContract(new JsonOnFileSystem("target/test"))
    fail("train time")
  }
  
  "If a doc root doesn't exist, it is created" in {
    val docRoot = Directory("target/test/monkeys")
    docRoot.exists mustBe false

    new JsonOnFileSystem(docRoot).save("foo", "bar")

    docRoot.exists mustBe true
  }
  
  "Saving a new resource adds it to the relevant file" in {
    new JsonOnFileSystem(Directory("target/test/jsonOnFileSystem"))
  }
}