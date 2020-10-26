package io.getquill.async.effect.postgres

import java.util.concurrent.ConcurrentLinkedQueue

import scala.jdk.CollectionConverters._
import scala.math.BigDecimal.int2bigDecimal

import io.getquill.context.sql.QueryResultTypeSpec

class QueryResultTypePostgresAsyncSpec extends QueryResultTypeSpec {

  val context = testContext
  import testContext.{ io => _, _ }

  val insertedProducts = new ConcurrentLinkedQueue[Product]

  override def beforeAll(): Unit = {
    awaitIO(testContext.io(deleteAll).run)
    val ids = awaitIO(testContext.io(liftQuery(productEntries).foreach(e => productInsert(e))).run)
    val inserted = (ids zip productEntries).map {
      case (id, prod) => prod.copy(id = id)
    }
    insertedProducts.addAll(inserted.asJava)
    ()
  }

  def products = insertedProducts.asScala.toList

  "return list" - {
    "select" in {
      awaitIO(testContext.io(selectAll).run) must contain theSameElementsAs (products)
    }
    "map" in {
      awaitIO(testContext.io(map).run) must contain theSameElementsAs (products.map(_.id))
    }
    "filter" in {
      awaitIO(testContext.io(filter).run) must contain theSameElementsAs (products)
    }
    "withFilter" in {
      awaitIO(testContext.io(withFilter).run) must contain theSameElementsAs (products)
    }
    "sortBy" in {
      awaitIO(testContext.io(sortBy).run) must contain theSameElementsInOrderAs (products)
    }
    "take" in {
      awaitIO(testContext.io(take).run) must contain theSameElementsAs (products)
    }
    "drop" in {
      awaitIO(testContext.io(drop).run) must contain theSameElementsAs (products.drop(1))
    }
    "++" in {
      awaitIO(testContext.io(`++`).run) must contain theSameElementsAs (products ++ products)
    }
    "unionAll" in {
      awaitIO(testContext.io(unionAll).run) must contain theSameElementsAs (products ++ products)
    }
    "union" in {
      awaitIO(testContext.io(union).run) must contain theSameElementsAs (products)
    }
    "join" in {
      awaitIO(testContext.io(join).run) must contain theSameElementsAs (products zip products)
    }
    "distinct" in {
      awaitIO(testContext.io(distinct).run) must contain theSameElementsAs (products.map(_.id).distinct)
    }
  }

  "return single result" - {
    "min" - {
      "some" in {
        awaitIO(testContext.io(minExists).run) mustEqual Some(products.map(_.sku).min)
      }
      "none" in {
        awaitIO(testContext.io(minNonExists).run) mustBe None
      }
    }
    "max" - {
      "some" in {
        awaitIO(testContext.io(maxExists).run) mustBe Some(products.map(_.sku).max)
      }
      "none" in {
        awaitIO(testContext.io(maxNonExists).run) mustBe None
      }
    }
    "avg" - {
      "some" in {
        awaitIO(testContext.io(avgExists).run) mustBe Some(BigDecimal(products.map(_.sku).sum) / products.size)
      }
      "none" in {
        awaitIO(testContext.io(avgNonExists).run) mustBe None
      }
    }
    "size" in {
      awaitIO(testContext.io(productSize).run) mustEqual products.size
    }
    "parametrized size" in {
      awaitIO(testContext.io(parametrizedSize(lift(10000))).run) mustEqual 0
    }
    "nonEmpty" in {
      awaitIO(testContext.io(nonEmpty).run) mustEqual true
    }
    "isEmpty" in {
      awaitIO(testContext.io(isEmpty).run) mustEqual false
    }
  }
}
