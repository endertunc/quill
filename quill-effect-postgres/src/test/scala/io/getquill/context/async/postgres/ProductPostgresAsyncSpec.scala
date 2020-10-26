package io.getquill.async.effect.postgres

import cats.syntax.all._
import cats.instances.list._
import io.getquill.context.sql.ProductSpec
import io.getquill.context.sql.Id

class ProductPostgresAsyncSpec extends ProductSpec {

  val context = testContext
  import testContext.{ io => _, _ }

  override def beforeAll(): Unit = {
    awaitIO(testContext.io(quote(query[Product].delete)).run)
    ()
  }

  "Product" - {
    "Insert multiple products" in {
      val inserted = awaitIO(productEntries.traverse(product => testContext.io(productInsert(lift(product)))).run)
      val product = awaitIO(testContext.io(productById(lift(inserted(2)))).run).head
      product.description mustEqual productEntries(2).description
      product.id mustEqual inserted(2)
    }
    "Single insert product" in {
      val inserted = awaitIO(testContext.io(productSingleInsert).run)
      val product = awaitIO(testContext.io(productById(lift(inserted))).run).head
      product.description mustEqual "Window"
      product.id mustEqual inserted
    }

    "Single insert with inlined free variable" in {
      val prd = Product(0L, "test1", 1L)
      val inserted = awaitIO {
        testContext.io {
          product.insert(_.sku -> lift(prd.sku), _.description -> lift(prd.description)).returning(_.id)
        }.run
      }
      val returnedProduct = awaitIO(testContext.io(productById(lift(inserted))).run).head
      returnedProduct.description mustEqual "test1"
      returnedProduct.sku mustEqual 1L
      returnedProduct.id mustEqual inserted
    }

    "Single insert with free variable and explicit quotation" in {
      val prd = Product(0L, "test2", 2L)
      val q1 = quote {
        product.insert(_.sku -> lift(prd.sku), _.description -> lift(prd.description)).returning(_.id)
      }
      val inserted = awaitIO(testContext.io(q1).run)
      val returnedProduct = awaitIO(testContext.io(productById(lift(inserted))).run).head
      returnedProduct.description mustEqual "test2"
      returnedProduct.sku mustEqual 2L
      returnedProduct.id mustEqual inserted
    }

    "Single product insert with a method quotation" in {
      val prd = Product(0L, "test3", 3L)
      val inserted = awaitIO(testContext.io(productInsert(lift(prd))).run)
      val returnedProduct = awaitIO(testContext.io(productById(lift(inserted))).run).head
      returnedProduct.description mustEqual "test3"
      returnedProduct.sku mustEqual 3L
      returnedProduct.id mustEqual inserted
    }

    "Single insert with value class" in {
      case class Product(id: Id, description: String, sku: Long)
      val prd = Product(Id(0L), "test2", 2L)
      val q1 = quote {
        query[Product].insert(_.sku -> lift(prd.sku), _.description -> lift(prd.description)).returning(_.id)
      }
      awaitIO(testContext.io(q1).run) mustBe a[Id]
    }

    "supports casts from string to number" - {
      "toInt" in {
        case class Product(id: Long, description: String, sku: Int)
        val queried = awaitIO {
          testContext.io {
            query[Product].filter(_.sku == lift("1004").toInt)
          }.run
        }.head
        queried.sku mustEqual 1004L
      }
      "toLong" in {
        val queried = awaitIO {
          testContext.io {
            query[Product].filter(_.sku == lift("1004").toLong)
          }.run
        }.head
        queried.sku mustEqual 1004L
      }
    }
  }
}
