package io.getquill.async.effect

import cats.effect._
import scala.concurrent.ExecutionContext

package object mysql {
  implicit val AppIOTimer: Timer[IO] = IO.timer(ExecutionContext.global)
  implicit val AppIOContextShift: ContextShift[IO] = IO.contextShift(ExecutionContext.global)

  object testContext extends TestContext
}
