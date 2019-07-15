package io.getquill.effect

import cats.effect._
import cats._
import com.twitter.util._
import io.getquill.context.Context
import scala.language.higherKinds

trait TwitterFutureEffect[F[_], C] extends Effect[Future, C] { this: Context[_, _] =>

  protected val futureMonadError = new MonadError[Future, Throwable] with StackSafeMonad[Future] {
    def pure[A](a: A): Future[A] = Future.value(a)
    def flatMap[A, B](fa: Future[A])(f: A => Future[B]): Future[B] = fa.flatMap(f)
    def handleErrorWith[A](fa: Future[A])(f: Throwable => Future[A]) = fa.rescue {
      case e: Throwable => f(e)
    }
    def raiseError[A](e: Throwable) = Future.exception(e)
  }

  protected implicit val EffectMonad = {
    futureMonadError
  }

  protected val F: Async[F]

  protected def performEffect[A](f: C => Future[A], transactional: Boolean): Future[A]

  implicit class DBIOSyntax[B](dbio: DBIO[C, Future, B]) {
    private def runEffect(transactional: Boolean): F[B] = F.async { k =>
      performEffect(dbio.f, transactional).liftToTry.foreach {
        case Return(r) => k(Right(r))
        case Throw(e)  => k(Left(e))
      }
      ()
    }

    def run = runEffect(false)
    def runTrans = runEffect(true)
  }
}
