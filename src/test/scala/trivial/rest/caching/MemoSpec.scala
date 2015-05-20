package trivial.rest.caching

import org.scalatest.{MustMatchers, WordSpec}

class MemoSpec extends WordSpec with MustMatchers {
  "Memoising a function stops it being called repeatedly for the same input value" in {
    var expensiveCallCount = 0

    class MemoDemo extends Memo {
      private val func = (input: String) => {
        expensiveCallCount = expensiveCallCount + 1
        input.toUpperCase
      }

      def memoised(input: String) = memo (this) { func } (input)
    }

    val demo = new MemoDemo
    demo.memoised("a") mustEqual "A"
    demo.memoised("b") mustEqual "B"
    demo.memoised("a") mustEqual "A"
    demo.memoised("a") mustEqual "A"
    demo.memoised("a") mustEqual "A"

    expensiveCallCount mustEqual 2
  }

  "We can allocate new memoisation caches using the function as a key" in {
    // BEWARE - the compiler requires functions to be assigned to vals, otherwise multiple
    // instances of the same function will result in multiple caches, i.e. no benefit.
    val demo = new Memo {
      val func1 = (input: String) => { input.toUpperCase }
      val func2 = (input: String) => { input.toLowerCase }
      val func3 = (input: String) => { input }
    }

    import demo._

    memo { func1 }
    memo { func2 }
    memo { func3 }
    memo { func1 }
    memo { func1 }
    memo { func1 }

    cacheOfCaches.size mustEqual 3
  }

  "We can blat a cache programmatically" in {
    var expensiveCallCount = 0

    new Memo {
      val func = (input: String) => {
        expensiveCallCount = expensiveCallCount + 1
        input.toUpperCase
      }

      memo (this) { func } ("a")
      memo (this) { func } ("a")
      memo (this) { func } ("a")
      memo (this) { func } invalidate()
      memo (this) { func } ("a")
    }

    expensiveCallCount mustEqual 2
  }

  "We can update an individual cached value" in {
    pending
//    fail("Monkeys")
  }

  "How to memoise methods" in {
    var expensiveCallCount = 0

    class MemoDemo extends Memo {
      private def method(input: String) = {
        expensiveCallCount = expensiveCallCount + 1
        input.toUpperCase
      }

      // Only works if declared outside the call to memo {}
      val f = method _

      def memoised(input: String) = memo ("myMethod") { f } (input)
    }

    val demo = new MemoDemo
    demo.memoised("a") mustEqual "A"
    demo.memoised("a") mustEqual "A"
    demo.memoised("a") mustEqual "A"

    expensiveCallCount mustEqual 1
  }

  "How NOT to memoise methods" in {
    var expensiveCallCount = 0

    class MemoDemo extends Memo {
      private def method(input: String) = {
        expensiveCallCount = expensiveCallCount + 1
        input.toUpperCase
      }

      // Assignment is inside the method, and f gets a new hashCode each time
      def wrongOne(input: String) = memo {
        val f = method _
        f
      } (input)

      // Assignment is still inside the method, and f still gets a new hashCode each time
      def wrongTwo(input: String) = {
        val f = method _
        memo { f } (input)
      }
    }

    val demo = new MemoDemo
    demo.wrongOne("a") mustEqual "A"
    demo.wrongOne("a") mustEqual "A"
    expensiveCallCount mustEqual 2

    demo.wrongTwo("a") mustEqual "A"
    demo.wrongTwo("a") mustEqual "A"
    expensiveCallCount mustEqual 4
  }
}