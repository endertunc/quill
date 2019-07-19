package io.getquill.context.async

import cats.effect._
import scala.concurrent.ExecutionContext

package object postgres {

implicit val timer = IO.timer(ExecutionContext.global)
  implicit val appCS = IO.contextShift(ExecutionContext.global)
  object testContext extends TestContext
}
