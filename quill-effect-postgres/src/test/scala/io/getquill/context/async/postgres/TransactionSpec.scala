package io.getquill.async.effect.postgres

import cats.syntax.all._
import io.getquill.context.sql.PeopleSpec
import io.getquill.effect._

class TransactionSpec extends PeopleSpec {
  val context = testContext
  import testContext.{ io => _, _ }

  override def beforeAll =
    awaitIO {
      val acts = for {
        _ <- testContext.run(query[Couple].delete)
        _ <- testContext.run(query[Person].delete)
        _ <- testContext.run(liftQuery(peopleEntries).foreach(e => peopleInsert(e)))
        _ <- testContext.run(liftQuery(couplesEntries).foreach(e => couplesInsert(e)))
      } yield {}
      acts.runTrans
    }

  val alex = quote {
    query[Person]
      .filter(_.name == "Alex")
  }

  val bert = quote {
    query[Person]
      .filter(_.name == "Bert")
  }

  "FinaglePostgresContext" - {
    "support transaction" in {
      val a1 = testContext.io {
        alex.update(_.age -> 61)

      }.ensure(new Exception("update Alex failed"))(_ > 0)
      val a2 = testContext.io {
        bert.update(_.age -> 61)
      }.ensure(new Exception("update Bert failed"))(_ > 0)

      val r = awaitIO((a1 *> a2 *> DBIO.raiseError(new Exception("app error"))).runTrans.attempt)
      r must be(Symbol("Left"))
      awaitIO(testContext.io(alex).run).map(_.age) must equal(Vector(60))
      awaitIO(testContext.io(bert).run).map(_.age) must equal(Vector(55))
    }
  }

}
