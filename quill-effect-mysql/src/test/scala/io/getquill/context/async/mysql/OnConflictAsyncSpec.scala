package io.getquill.async.effect.mysql

import io.getquill.context.sql.OnConflictSpec

class OnConflictAsyncSpec extends OnConflictSpec {
  val ctx = testContext
  import ctx.{ io => _, _ }

  override protected def beforeAll(): Unit = {
    awaitIO(ctx.io(qr1.delete).run)
    ()
  }

  "INSERT IGNORE" in {
    import `onConflictIgnore`._
    awaitIO(ctx.io(testQuery1).run) mustEqual res1
    awaitIO(ctx.io(testQuery2).run) mustEqual res2
    awaitIO(ctx.io(testQuery3).run) mustEqual res3
  }

  "ON DUPLICATE KEY UPDATE i=i " in {
    import `onConflictIgnore(_.i)`._
    awaitIO(ctx.io(testQuery1).run) mustEqual res1
    awaitIO(ctx.io(testQuery2).run) mustEqual res2
    awaitIO(ctx.io(testQuery3).run) mustEqual res3
  }

  "ON DUPLICATE KEY UPDATE ..." in {
    import `onConflictUpdate((t, e) => ...)`._
    awaitIO(ctx.io(testQuery(e1)).run) mustEqual res1
    awaitIO(ctx.io(testQuery(e2)).run) mustEqual res2 + 1
    awaitIO(ctx.io(testQuery(e3)).run) mustEqual res3 + 1
    awaitIO(ctx.io(testQuery4).run) mustEqual res4
  }
}
