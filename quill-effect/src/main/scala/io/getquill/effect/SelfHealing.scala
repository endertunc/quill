package io.getquill.effect

import scala.language.higherKinds
import cats.syntax.all._
import cats.effect._
import cats.effect.syntax.all._
import cats.effect.concurrent._
import scala.concurrent.duration._
import org.slf4j.LoggerFactory

trait SelfHealing[F[_], A] {
  def release: F[Unit]
  def get: Resource[F, A]
}

object SelfHealing {

  def apply[F[_]: ConcurrentEffect: Timer, A](
    create:           F[A],
    release:          A => F[Unit],
    check:            A => F[Boolean],
    minCheckInterval: Long
  ): F[SelfHealing[F, A]] = {

    for {
      initConDefer <- Deferred.tryable[F, Either[Throwable, A]]
      _ <- create.attempt.flatMap(initConDefer.complete)
      connRef <- Ref.of[F, TryableDeferred[F, Either[Throwable, A]]](initConDefer)
      activeRef <- Ref.of[F, Long](System.currentTimeMillis)
    } yield new SelfHealingImpl(
      create,
      release,
      check,
      connRef,
      activeRef,
      minCheckInterval
    )
  }

  private class SelfHealingImpl[F[_]: Concurrent: Timer, A](
    create:           F[A],
    release:          A => F[Unit],
    check:            A => F[Boolean],
    ref:              Ref[F, TryableDeferred[F, Either[Throwable, A]]],
    lastSuccess:      Ref[F, Long], // last success op performed with this item
    minCheckInterval: Long
  ) extends SelfHealing[F, A] {

    private final val logger = LoggerFactory.getLogger(classOf[SelfHealingImpl[F, A]])

    def release = ref.get.flatMap(tryRelease).void

    def get: Resource[F, A] = {
      val acquire = for {
        d <- ref.get
        stOrE <- d.get
        la <- lastSuccess.get
        i <- healIfSick(la, d, stOrE).rethrow
      } yield i
      Resource.makeCase(acquire) {
        case (_, ExitCase.Completed) =>
          setActiveNow.void
        case _ =>
          ().pure[F]
      }
    }

    private def newState = {

      create.attempt.flatTap { r =>
        Sync[F].delay(logger.info(s"[SelfHealing-newState] create new state: ${r}"))
      } <* setActiveNow
    }

    private def setActiveNow = {
      now.flatMap(n => lastSuccess.tryUpdate(_ => n))
    }

    @inline
    private def now = Sync[F].delay(System.currentTimeMillis)

    private def tryWithIn[X](fx: => F[X], seconds: Int): F[Either[Unit, X]] = {
      Concurrent[F].race(Timer[F].sleep(seconds.seconds), fx)
    }

    private def healIfSick(
      la:    Long,
      defer: TryableDeferred[F, Either[Throwable, A]],
      eOrA:  Either[Throwable, A]
    ) = {
      eOrA match {
        case Left(e) =>
          now.flatMap { n =>
            if ((n - la) > minCheckInterval) healSick(defer) else eOrA.pure[F]
          }
        case Right(a) =>
          now.flatMap { n =>
            if ((n - la) > minCheckInterval) {
              tryCheck(a).flatMap {
                case Right(true) =>
                  eOrA.pure[F]
                case r =>
                  logger.info(s"[SelfHealing-healIfSick] Check ${n} : ${r}, start healing...")
                  healSick(defer)
              }
            } else eOrA.pure[F]
          }
      }
    }

    private def tryRelease(a: Deferred[F, Either[Throwable, A]]) = {
      tryWithIn(
        a.get.flatMap {
          case Right(v) =>
            Sync[F].delay {
              logger.info(s"[SelfHealing-tryRelease] Start releasing resource ${v}")
            } *> release(v)
          case Left(_) =>
            ().pure[F]
        }, 3
      ).flatTap { r =>
          Sync[F].delay {
            logger.info(s"[SelfHealing-tryRelease] Releasing result: ${r}")
          }
        }
    }

    private def tryCheck(a: A) = {
      val doCheck = setActiveNow.flatMap {
        case true  => Either.catchNonFatal(check(a)).liftTo[F].flatten.handleError(_ => false)
        case false => true.pure[F] // checked in another process, just ignore
      }
      tryWithIn(doCheck, 1)
    }

    private def healSick(
      oldDefer: TryableDeferred[F, Either[Throwable, A]]
    ): F[Either[Throwable, A]] = {
      Deferred.tryable[F, Either[Throwable, A]].flatMap { defer =>
        ref.access.flatMap {
          case (old, setter) =>
            val updated = if (old == oldDefer) {
              setter(defer)
            } else false.pure[F]

            updated.flatMap {
              case true =>
                (tryRelease(old).attempt *> newState).uncancelable.flatTap { r =>
                  logger.info(s"[SelfHealing-healSick] Replacing ${old} with ${r}")
                  defer.complete(r)
                } <* setActiveNow
              case false =>
                ref.get.flatMap(_.get)
            }
        }
      }
    }
  }
}
