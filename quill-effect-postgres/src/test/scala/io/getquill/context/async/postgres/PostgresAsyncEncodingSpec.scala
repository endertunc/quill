package io.getquill.context.async.postgres

import java.time.{ LocalDate, LocalDateTime, ZonedDateTime }

import io.getquill.context.sql.EncodingSpec
import org.joda.time.{ DateTime => JodaDateTime, LocalDate => JodaLocalDate, LocalDateTime => JodaLocalDateTime }
import java.util.Date
import java.util.UUID

class PostgresAsyncEncodingSpec extends EncodingSpec {

  val context = testContext
  import testContext.{ io => _, _ }

  "encodes and decodes types" in {
    val r =
      for {
        _ <- testContext.io(delete)
        _ <- testContext.io(liftQuery(insertValues).foreach(e => insert(e)))
        result <- testContext.io(query[EncodingTestEntity])
      } yield result

    verify(awaitIO(r.run.map(_.toList)))
  }

  "encodes and decodes uuids" in {
    case class EncodingUUIDTestEntity(v1: UUID)
    val testUUID = UUID.fromString("e5240c08-6ee7-474a-b5e4-91f79c48338f")

    //delete old values
    val q0 = quote(query[EncodingUUIDTestEntity].delete)
    val rez0 = awaitIO(testContext.io(q0).run)

    //insert new uuid
    val rez1 = awaitIO(testContext.io(query[EncodingUUIDTestEntity].insert(lift(EncodingUUIDTestEntity(testUUID)))).run)

    //verify you can get the uuid back from the db
    val q2 = quote(query[EncodingUUIDTestEntity].map(p => p.v1))
    val rez2 = awaitIO(testContext.io(q2).run)

    rez2 mustEqual List(testUUID)
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
        _ <- testContext.io(query[EncodingTestEntity].delete)
        _ <- testContext.io(liftQuery(insertValues).foreach(e => query[EncodingTestEntity].insert(e)))
        r <- testContext.io(q(liftQuery(insertValues.map(_.v6))))
      } yield {
        r
      }
    verify(awaitIO(fut.run.map(_.toList)))
  }

  "returning UUID" in {
    val success = for {
      uuid <- awaitIO(testContext.io(insertBarCode(lift(barCodeEntry))).run)
      barCode <- awaitIO(testContext.io(findBarCodeByUuid(uuid)).run).headOption
    } yield {
      verifyBarcode(barCode)
    }
    success must not be empty
  }

  "decodes joda DateTime, LocalDate and LocalDateTime types" in {
    case class DateEncodingTestEntity(v1: JodaLocalDate, v2: JodaLocalDateTime, v3: JodaDateTime)
    val entity = DateEncodingTestEntity(JodaLocalDate.now, JodaLocalDateTime.now, JodaDateTime.now)
    val r = for {
      _ <- testContext.io(query[DateEncodingTestEntity].delete)
      _ <- testContext.io(query[DateEncodingTestEntity].insert(lift(entity)))
      result <- testContext.io(query[DateEncodingTestEntity])
    } yield result
    awaitIO(r.run) mustBe Seq(entity)
  }

  "decodes ZonedDateTime, LocalDate and LocalDateTime types" in {
    case class DateEncodingTestEntity(v1: LocalDate, v2: LocalDateTime, v3: ZonedDateTime)
    val entity = DateEncodingTestEntity(LocalDate.now, LocalDateTime.now, ZonedDateTime.now)
    val r = for {
      _ <- testContext.io(query[DateEncodingTestEntity].delete)
      _ <- testContext.io(query[DateEncodingTestEntity].insert(lift(entity)))
      result <- testContext.io(query[DateEncodingTestEntity])
    } yield result
    awaitIO(r.run) mustBe Seq(entity)
  }

  "encodes custom type inside singleton object" in {
    object Singleton {
      def apply()(implicit c: TestContext) = {
        import c.{ io => _, _ }
        for {
          _ <- c.io(query[EncodingTestEntity].delete)
          result <- c.io(liftQuery(insertValues).foreach(e => query[EncodingTestEntity].insert(e)))
        } yield result
      }
    }

    implicit val c = testContext
    awaitIO(Singleton().run)
  }
}
