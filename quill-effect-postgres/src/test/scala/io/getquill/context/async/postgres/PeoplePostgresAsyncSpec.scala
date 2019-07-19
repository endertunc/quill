package io.getquill.context.async.postgres

import io.getquill.context.sql.PeopleSpec

class PeoplePostgresAsyncSpec extends PeopleSpec {

  val context = testContext
  import testContext.{io => _, _}

  override def beforeAll =
    awaitIO {
      val acts =
        for {
          _ <- testContext.io(query[Couple].delete)
          _ <- testContext.io(query[Person].filter(_.age > 0).delete)
          _ <- testContext.io(liftQuery(peopleEntries).foreach(e => peopleInsert(e)))
          _ <- testContext.io(liftQuery(couplesEntries).foreach(e => couplesInsert(e)))
        } yield {}
      acts.runTrans
    }

  "Example 1 - differences" in {
    awaitIO(testContext.io(`Ex 1 differences`).run) mustEqual `Ex 1 expected result`
  }

  "Example 2 - range simple" in {
    awaitIO(testContext.io(`Ex 2 rangeSimple`(lift(`Ex 2 param 1`), lift(`Ex 2 param 2`))).run) mustEqual `Ex 2 expected result`
  }

  "Examples 3 - satisfies" in {
    awaitIO(testContext.io(`Ex 3 satisfies`).run) mustEqual `Ex 3 expected result`
  }

  "Examples 4 - satisfies" in {
    awaitIO(testContext.io(`Ex 4 satisfies`).run) mustEqual `Ex 4 expected result`
  }

  "Example 5 - compose" in {
    awaitIO(testContext.io(`Ex 5 compose`(lift(`Ex 5 param 1`), lift(`Ex 5 param 2`))).run) mustEqual `Ex 5 expected result`
  }

  "Example 6 - predicate 0" in {
    awaitIO(testContext.io(satisfies(eval(`Ex 6 predicate`))).run) mustEqual `Ex 6 expected result`
  }

  "Example 7 - predicate 1" in {
    awaitIO(testContext.io(satisfies(eval(`Ex 7 predicate`))).run) mustEqual `Ex 7 expected result`
  }

  "Example 8 - contains empty" in {
    awaitIO(testContext.io(`Ex 8 and 9 contains`(liftQuery(`Ex 8 param`))).run) mustEqual `Ex 8 expected result`
  }

  "Example 9 - contains non empty" in {
    awaitIO(testContext.io(`Ex 8 and 9 contains`(liftQuery(`Ex 9 param`))).run) mustEqual `Ex 9 expected result`
  }
}
