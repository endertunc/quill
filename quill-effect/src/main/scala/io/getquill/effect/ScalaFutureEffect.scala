package io.getquill.effect

import cats.effect.Async
import cats.MonadError
import cats.instances.future._
import io.getquill.context.Context
import scala.concurrent.Future
import scala.language.higherKinds

trait ScalaFutureEffect[F[_], C] extends Effect[Future, C] { this: Context[_, _] =>

  protected implicit val ec = trampoline

  protected implicit val EffectMonad: MonadError[Future, Throwable] = {
    implicitly[MonadError[Future, Throwable]]
  }

  protected val F: Async[F]

  protected def performEffect[A](f: C => Future[A], transactional: Boolean): F[A]

  implicit class DBIOMonadSyntax1[A](dbio: DBIO[C, Future, A]) {
    private def run(transactional: Boolean): F[A] = performEffect(dbio.f, transactional)

    def run: F[A] = run(false)
    def runTrans: F[A] = run(true)
  }
}
