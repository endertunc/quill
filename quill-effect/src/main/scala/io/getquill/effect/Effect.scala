package io.getquill.effect

import cats._
import cats.syntax.all._
import io.getquill.context._
import io.getquill.util.ContextLogger
import scala.language.experimental.macros
import scala.language.higherKinds

trait Effect[E[_], C] { this: Context[_, _] =>
  type Result[A] = DBIO[C, E, A]

  private val logger: ContextLogger = ContextLogger(this.getClass)

  override def logContextError[A](info: String, r: Result[A]) = {
    r.handleErrorWith { e: Throwable =>
      logger.logError(info, e)
      DBIO.raiseError(e)
    }
  }

  protected implicit val EffectMonad: MonadError[E, Throwable]
  implicit lazy val DBIOMonadError: MonadError[Result, Throwable] = DBIO.dbioMonadError[C, E](EffectMonad)

  implicit class DBIOSyntax(val i: DBIO.type) {
    def pure[A](a: A): DBIO[C, E, A] = DBIOMonadError.pure(a)
    def raiseError[A](e: Throwable): DBIO[C, E, A] = DBIOMonadError.raiseError(e)
  }

  def io[T](quoted: Quoted[T]): Result[RunQuerySingleResult[T]] = macro QueryMacro.runQuerySingle[T]
  def io[T](quoted: Quoted[Query[T]]): Result[RunQueryResult[T]] = macro QueryMacro.runQuery[T]
  def io(quoted: Quoted[Action[_]]): Result[RunActionResult] = macro ActionMacro.runAction
  def io[T](quoted: Quoted[ActionReturning[_, T]]): Result[RunActionReturningResult[T]] = macro ActionMacro.runActionReturning[T]
  def io(quoted: Quoted[BatchAction[Action[_]]]): Result[RunBatchActionResult] = macro ActionMacro.runBatchAction
  def io[T](quoted: Quoted[BatchAction[ActionReturning[_, T]]]): Result[RunBatchActionReturningResult[T]] = macro ActionMacro.runBatchActionReturning[T]

}
