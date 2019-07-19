package io.getquill.async.effect.mysql

import io.getquill.context.sql.DepartmentsSpec

class DepartmentsMysqlAsyncSpec extends DepartmentsSpec {

  val context = testContext
  import testContext.{io => _, _}

  override def beforeAll =
    awaitIO {
      val acts = for {
        _ <- testContext.run(query[Department].delete)
        _ <- testContext.run(query[Employee].delete)
        _ <- testContext.run(query[Task].delete)

        _ <- testContext.run(liftQuery(departmentEntries).foreach(e => departmentInsert(e)))
        _ <- testContext.run(liftQuery(employeeEntries).foreach(e => employeeInsert(e)))
        _ <- testContext.run(liftQuery(taskEntries).foreach(e => taskInsert(e)))
      } yield {}
      acts.runTrans
    }

  "Example 8 - nested naive" in {
    awaitIO(testContext.io(`Example 8 expertise naive`(lift(`Example 8 param`))).run) mustEqual `Example 8 expected result`
  }

  "Example 9 - nested db" in {
    awaitIO(testContext.io(`Example 9 expertise`(lift(`Example 9 param`))).run) mustEqual `Example 9 expected result`
  }
}
