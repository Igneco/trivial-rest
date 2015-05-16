package trivial.rest.caching

import org.scalatest.{MustMatchers, WordSpec}

class MemoSpec extends WordSpec with MustMatchers {
  "Memoising a function stops it being called repeatedly for the same input value" in {
    var expensiveCallCount = 0

    class MemoDemo extends Memo {
      val func = (input: String) => {
        expensiveCallCount = expensiveCallCount + 1
        input.toUpperCase
      }

      def memoised(input: String) = memo { func } (input)
    }

    val demo = new MemoDemo
    demo.memoised("a") mustEqual "A"
    demo.memoised("b") mustEqual "B"
    demo.memoised("a") mustEqual "A"
    demo.memoised("a") mustEqual "A"
    demo.memoised("a") mustEqual "A"

    expensiveCallCount mustEqual 2
  }

  "We allocate new memoisation caches using the function as a key" in {
    new Memo {
      val func1 = (input: String) => { input.toUpperCase }
      val func2 = (input: String) => { input.toLowerCase }
      val func3 = (input: String) => { input }

      memo { func1 }
      memo { func2 }
      memo { func3 }
      memo { func1 }
      memo { func1 }
      memo { func1 }

      this.cacheOfCaches.size mustEqual 3
    }
  }
}