package io.getquill.async.effect.mysql

import cats.effect.IO
import io.getquill.{ Literal, TestEntities, MysqlAsyncEffectContext }
import io.getquill.context.sql.{ TestDecoders, TestEncoders }

class TestContext extends MysqlAsyncEffectContext[IO, Literal](Literal, "testMysqlDB") with TestEntities with TestEncoders with TestDecoders {
  def awaitIO[A](f: IO[A]) = f.unsafeRunSync()
}
