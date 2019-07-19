package io.getquill.async.effect.postgres

import cats.effect.IO
import com.github.mauricio.async.db.QueryResult
import io.getquill.ReturnAction.ReturnColumns
import io.getquill.{ Literal, PostgresAsyncEffectContext, ReturnAction, Spec, PostgresDialect }

class PostgresAsyncContextSpec extends Spec {

  import testContext.{ io => _, _ }

  "run non-batched action" in {
    val insert = quote { (i: Int) =>
      qr1.insert(_.i -> i)
    }
    awaitIO(testContext.io(insert(lift(1))).run) mustEqual 1
  }

  "Insert with returning with single column table" in {
    val inserted: Long = awaitIO(testContext.io {
      qr4.insert(lift(TestEntity4(0))).returningGenerated(_.i)
    }.run)
    awaitIO(testContext.io(qr4.filter(_.i == lift(inserted))).run)
      .head.i mustBe inserted
  }
  "Insert with returning with multiple columns" in {
    awaitIO(testContext.io(qr1.delete).run)
    val inserted = awaitIO(testContext.io {
      qr1.insert(lift(TestEntity("foo", 1, 18L, Some(123)))).returning(r => (r.i, r.s, r.o))
    }.run)
    (1, "foo", Some(123)) mustBe inserted
  }

  "performIO" in {
    awaitIO(testContext.io(qr4).runTrans)
  }

  "probe" in {
    probe("select 1").toOption mustBe defined
  }

  "cannot extract" in {
    object ctx extends PostgresAsyncEffectContext[IO, PostgresDialect, Literal](PostgresDialect, Literal, "testPostgresDB") {
      override def extractActionResult[O](
        returningAction:    ReturnAction,
        returningExtractor: ctx.Extractor[O]
      )(result: QueryResult) =
        super.extractActionResult(returningAction, returningExtractor)(result)
    }
    intercept[IllegalStateException] {
      ctx.extractActionResult(ReturnColumns(List("w/e")), row => 1)(new QueryResult(0, "w/e"))
    }
    ctx.close
  }

  "prepare" in {
    testContext.prepareParams("", ps => (Nil, ps ++ List("Sarah", 127))) mustEqual List("'Sarah'", "127")
  }

  override protected def beforeAll(): Unit = {
    awaitIO(testContext.io(qr1.delete).run)
    ()
  }
}
