package io.getquill.quotation

import io.getquill._
import io.getquill.testContext._

class FooSpec extends Spec {

  case class Foo(i: Long, j4: String)

  "foo" in {
    val q = quote {
      query[Foo]
    }
    testContext.run(q)
  }
}
