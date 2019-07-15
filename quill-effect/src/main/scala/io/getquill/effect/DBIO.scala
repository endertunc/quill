package io.getquill.effect
import cats._
import scala.language.higherKinds

class DBIO[C, M[_], A](val f: C => M[A])(implicit M: MonadError[M, Throwable]) {
  @inline def flatMap[B](ff: A => DBIO[C, M, B]): DBIO[C, M, B] = DBIO { c =>
    M.flatMap(f(c)) { a =>
      ff(a).f(c)
    }
  }

  @inline def map[B](ff: A => B): DBIO[C, M, B] = DBIO { c =>
    M.map(f(c))(ff)
  }
}

object DBIO {

  def apply[C, M[_], A](f: C => M[A])(implicit M: MonadError[M, Throwable]) = new DBIO[C, M, A](f)
  def dbioMonadError[C, M[_]](implicit M: MonadError[M, Throwable]) = new MonadError[({ type TM[A] = DBIO[C, M, A] })#TM, Throwable] with StackSafeMonad[({ type TM[A] = DBIO[C, M, A] })#TM] {

    type TM[A] = DBIO[C, M, A]

    @inline def pure[A](a: A) = DBIO[C, M, A] { c =>
      M.pure(a)
    }

    @inline def handleErrorWith[A](fa: TM[A])(f: Throwable => TM[A]): TM[A] = DBIO[C, M, A] { c =>
      M.handleErrorWith(fa.f(c)) { e =>
        f(e).f(c)
      }
    }

    @inline def raiseError[A](e: Throwable): TM[A] = DBIO[C, M, A] { c =>
      M.raiseError[A](e)
    }

    @inline def flatMap[A, B](fa: TM[A])(ff: A => TM[B]): TM[B] = fa.flatMap(ff)
  }
}
