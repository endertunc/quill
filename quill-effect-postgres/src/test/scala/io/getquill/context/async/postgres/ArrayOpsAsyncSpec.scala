package io.getquill.async.effect.postgres

import io.getquill.context.sql.ArrayOpsSpec

class ArrayOpsAsyncSpec extends ArrayOpsSpec {
  val ctx = testContext

  import ctx.{ io => _, _ }

  "contains" in {
    awaitIO(ctx.io(`contains`.`Ex 1 return all`).run) mustBe `contains`.`Ex 1 expected`
    awaitIO(ctx.io(`contains`.`Ex 2 return 1`).run) mustBe `contains`.`Ex 2 expected`
    awaitIO(ctx.io(`contains`.`Ex 3 return 2,3`).run) mustBe `contains`.`Ex 3 expected`
    awaitIO(ctx.io(`contains`.`Ex 4 return empty`).run) mustBe `contains`.`Ex 4 expected`
  }

  override protected def beforeAll(): Unit = {
    awaitIO(ctx.io(entity.delete).run)
    awaitIO(ctx.io(insertEntries).run)
    ()
  }

}
