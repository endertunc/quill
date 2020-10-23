package io.getquill.async.effect.postgres

import io.getquill.context.sql.CaseClassQuerySpec
import org.scalatest.Matchers._

class CaseClassQueryAsyncSpec extends CaseClassQuerySpec {

  val context = testContext
  import testContext.{ io => _, _ }

  override def beforeAll(): Unit =
    awaitIO {
      val acts =
        for {
          _ <- testContext.io(query[Contact].delete)
          _ <- testContext.io(query[Address].delete)
          _ <- testContext.io(liftQuery(peopleEntries).foreach(e => peopleInsert(e)))
          _ <- testContext.io(liftQuery(addressEntries).foreach(e => addressInsert(e)))
        } yield {}
      acts.runTrans
    }

  "Example 1 - Single Case Class Mapping" in {
    awaitIO(testContext.io(`Ex 1 CaseClass Record Output`).run) should contain theSameElementsAs `Ex 1 CaseClass Record Output expected result`
  }
  "Example 1A - Single Case Class Mapping" in {
    awaitIO(testContext.io(`Ex 1A CaseClass Record Output`).run) should contain theSameElementsAs `Ex 1 CaseClass Record Output expected result`
  }
  "Example 1B - Single Case Class Mapping" in {
    awaitIO(testContext.io(`Ex 1B CaseClass Record Output`).run) should contain theSameElementsAs `Ex 1 CaseClass Record Output expected result`
  }

  "Example 2 - Single Record Mapped Join" in {
    awaitIO(testContext.io(`Ex 2 Single-Record Join`).run) should contain theSameElementsAs `Ex 2 Single-Record Join expected result`
  }

  "Example 3 - Inline Record as Filter" in {
    awaitIO(testContext.io(`Ex 3 Inline Record Usage`).run) should contain theSameElementsAs `Ex 3 Inline Record Usage exepected result`
  }
}
