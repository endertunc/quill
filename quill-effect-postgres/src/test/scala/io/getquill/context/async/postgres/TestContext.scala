package io.getquill.context.async.postgres

import cats.effect.IO
import io.getquill.context.sql.{ TestDecoders, TestEncoders }
import io.getquill.{ Literal, PostgresAsyncEffectContext, TestEntities, PostgresDialect }

class TestContext extends PostgresAsyncEffectContext[IO, PostgresDialect, Literal](PostgresDialect, Literal, "testPostgresDB")
  with TestEntities
  with TestEncoders
    with TestDecoders {
  def awaitIO[A](f: IO[A]) = f.unsafeRunSync()
}
