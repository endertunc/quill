package io.getquill.async.effect.postgres

import io.getquill.context.sql.OnConflictSpec

class OnConflictAsyncSpec extends OnConflictSpec {
  val ctx = testContext
  import ctx.{ io => _, _ }

  override protected def beforeAll(): Unit = {
    awaitIO(ctx.io(qr1.delete).run)
    ()
  }

  "ON CONFLICT DO NOTHING" in {
    import `onConflictIgnore`._
    awaitIO(ctx.io(testQuery1).run) mustEqual res1
    awaitIO(ctx.io(testQuery2).run) mustEqual res2
    awaitIO(ctx.io(testQuery3).run) mustEqual res3
  }

  "ON CONFLICT (i) DO NOTHING" in {
    import `onConflictIgnore(_.i)`._
    awaitIO(ctx.io(testQuery1).run) mustEqual res1
    awaitIO(ctx.io(testQuery2).run) mustEqual res2
    awaitIO(ctx.io(testQuery3).run) mustEqual res3
  }

  "ON CONFLICT (i) DO UPDATE ..." in {
    import `onConflictUpdate(_.i)((t, e) => ...)`._
    awaitIO(ctx.io(testQuery(e1)).run) mustEqual res1
    awaitIO(ctx.io(testQuery(e2)).run) mustEqual res2
    awaitIO(ctx.io(testQuery(e3)).run) mustEqual res3
    awaitIO(ctx.io(testQuery4).run) mustEqual res4
  }
}
