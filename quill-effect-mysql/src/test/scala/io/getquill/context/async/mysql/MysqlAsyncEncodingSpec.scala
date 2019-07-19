package io.getquill.async.effect.mysql

import java.time.{ LocalDate, LocalDateTime }

import io.getquill.context.sql.EncodingSpec
import org.joda.time.{ DateTime => JodaDateTime, LocalDate => JodaLocalDate, LocalDateTime => JodaLocalDateTime }

import java.util.Date

class MysqlAsyncEncodingSpec extends EncodingSpec {

  val context = testContext
  import testContext.{io => _, _}

  "encodes and decodes types" in {
    val r =
      for {
        _ <- testContext.io(delete)
        _ <- testContext.io(liftQuery(insertValues).foreach(e => insert(e)))
        result <- testContext.io(query[EncodingTestEntity])
      } yield result

    verify(awaitIO(r.run.map(_.toList)))
  }

  "decode numeric types correctly" - {
    "decode byte to" - {
      "short" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v3: Short)
        val v3List = awaitIO(testContext.io(query[EncodingTestEntity]).run)
        v3List.map(_.v3) must contain theSameElementsAs List(1: Byte, 0: Byte)
      }
      "int" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v3: Int)
        val v3List = awaitIO(testContext.io(query[EncodingTestEntity]).run)
        v3List.map(_.v3) must contain theSameElementsAs List(1, 0)
      }
      "long" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v3: Long)
        val v3List = awaitIO(testContext.io(query[EncodingTestEntity]).run)
        v3List.map(_.v3) must contain theSameElementsAs List(1L, 0L)
      }
    }
    "decode short to" - {
      "int" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v5: Int)
        val v5List = awaitIO(testContext.io(query[EncodingTestEntity]).run)
        v5List.map(_.v5) must contain theSameElementsAs List(23, 0)
      }
      "long" in {
        prepareEncodingTestEntity()
        case class EncodingTestEntity(v5: Long)
        val v5List = awaitIO(testContext.io(query[EncodingTestEntity]).run)
        v5List.map(_.v5) must contain theSameElementsAs List(23L, 0L)
      }
    }
    "decode int to long" in {
      case class EncodingTestEntity(v6: Long)
      val v6List = awaitIO(testContext.io(query[EncodingTestEntity]).run)
      v6List.map(_.v6) must contain theSameElementsAs List(33L, 0L)
    }

    "decode and encode any numeric as boolean" in {
      case class EncodingTestEntity(v3: Boolean, v4: Boolean, v6: Boolean, v7: Boolean)
      awaitIO(testContext.io(query[EncodingTestEntity]).run)
      ()
    }
  }

  "decode date types" in {
    case class DateEncodingTestEntity(v1: Date, v2: Date, v3: Date)
    val entity = DateEncodingTestEntity(new Date, new Date, new Date)
    val r = for {
      _ <- testContext.io(query[DateEncodingTestEntity].delete)
      _ <- testContext.io(query[DateEncodingTestEntity].insert(lift(entity)))
      result <- testContext.io(query[DateEncodingTestEntity])
    } yield result
    awaitIO(r.run)
    ()
  }

  "decode joda DateTime and Date types" in {
    case class DateEncodingTestEntity(v1: LocalDate, v2: JodaDateTime, v3: JodaDateTime)
    val entity = DateEncodingTestEntity(LocalDate.now, JodaDateTime.now, JodaDateTime.now)
    val r = for {
      _ <- testContext.io(query[DateEncodingTestEntity].delete)
      _ <- testContext.io(query[DateEncodingTestEntity].insert(lift(entity)))
      result <- testContext.io(query[DateEncodingTestEntity])
    } yield result
    awaitIO(r.run) must contain(entity)
  }

  "decode joda LocalDate and LocalDateTime types" in {
    case class DateEncodingTestEntity(v1: JodaLocalDate, v2: JodaLocalDateTime)
    val entity = DateEncodingTestEntity(JodaLocalDate.now, JodaLocalDateTime.now)
    val r = for {
      _ <- testContext.io(query[DateEncodingTestEntity].delete)
      _ <- testContext.io(query[DateEncodingTestEntity].insert(lift(entity)))
      result <- testContext.run(query[DateEncodingTestEntity])
    } yield result
    awaitIO(r.run)
  }

  "decode LocalDate and LocalDateTime types" in {
    case class DateEncodingTestEntity(v1: LocalDate, v2: LocalDateTime, v3: LocalDateTime)
    //since localdatetime is converted to joda which doesn't store nanos need to zero the nano part
    val entity = DateEncodingTestEntity(LocalDate.now(), LocalDateTime.now.withNano(0), LocalDateTime.now.withNano(0))
    val r = for {
      _ <- testContext.io(query[DateEncodingTestEntity].delete)
      _ <- testContext.io(query[DateEncodingTestEntity].insert(lift(entity)))
      result <- testContext.io(query[DateEncodingTestEntity])
    } yield result
    awaitIO(r.run) must contain(entity)
  }

  "fails if the column has the wrong type" - {
    "numeric" in {
      awaitIO(testContext.io(liftQuery(insertValues).foreach(e => insert(e))).run)
      case class EncodingTestEntity(v1: Int)
      val e = intercept[IllegalStateException] {
        awaitIO(testContext.io(query[EncodingTestEntity]).run)
      }
    }
    "non-numeric" in {
      awaitIO(testContext.io(liftQuery(insertValues).foreach(e => insert(e))).run)
      case class EncodingTestEntity(v1: Date)
      val e = intercept[IllegalStateException] {
        awaitIO(testContext.io(query[EncodingTestEntity]).run)
      }
    }
  }

  "encodes sets" in {
    val q = quote {
      (set: Query[Int]) =>
        query[EncodingTestEntity].filter(t => set.contains(t.v6))
    }
    val fut =
      for {
        _ <- testContext.run(query[EncodingTestEntity].delete)
        _ <- testContext.run(liftQuery(insertValues).foreach(e => query[EncodingTestEntity].insert(e)))
        r <- testContext.run(q(liftQuery(insertValues.map(_.v6))))
      } yield {
        r
      }
    verify(awaitIO(fut.run.map(_.toList)))
  }

  "encodes custom type inside singleton object" in {
    object Singleton {
      def apply()(implicit c: TestContext) = {
        import c.{io => _, _}
        for {
          _ <- c.io(query[EncodingTestEntity].delete)
          result <- c.io(liftQuery(insertValues).foreach(e => query[EncodingTestEntity].insert(e)))
        } yield result
      }
    }

    implicit val c = testContext
    awaitIO(Singleton().run)
  }

  private def prepareEncodingTestEntity() = {
    val prepare = for {
      _ <- testContext.io(delete)
      _ <- testContext.io(liftQuery(insertValues).foreach(e => insert(e)))
    } yield {}
    awaitIO(prepare.run)
  }
}
