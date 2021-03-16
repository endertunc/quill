package io.getquill.effect

import cats.effect.concurrent._
import cats.effect._
import cats.syntax.all._
import scala.language.higherKinds
import scala.concurrent.duration._

trait Queue[F[_], A] {
  def timedDequeue1(duration: FiniteDuration, timer: Timer[F]): F[Option[A]]
  def enqueue1(a: A): F[Unit]
}

final case class State[F[_], A](
  queue: Vector[A],
  deq:   Vector[TryableDeferred[F, A]]
)

object Queue {

  def empty[F[_]: ConcurrentEffect, A] = of[F, A](Vector.empty)

  def of[F[_]: ConcurrentEffect, A](as: Vector[A]): F[Queue[F, A]] = {
    Ref.of[F, State[F, A]](State(as, Vector.empty)).map { ref =>
      new DefaultQueue(ref)
    }
  }

  def unsafe[F[_]: ConcurrentEffect, A](as: Vector[A]): Queue[F, A] = {
    new DefaultQueue[F, A](Ref.unsafe(State(as, Vector.empty)))
  }

  private class DefaultQueue[F[_], A](ref: Ref[F, State[F, A]])(implicit F: ConcurrentEffect[F]) extends Queue[F, A] {

    def enqueue1(a: A): F[Unit] = {
      ref.modify { s =>
        if (s.deq.isEmpty) {
          (s.copy(queue = s.queue :+ a), None)
        } else {
          (s.copy(deq = s.deq.tail), Some(s.deq.head))
        }
      }.flatMap {
        case Some(h) =>
          F.runAsync(h.complete(a))(_ => IO.unit).to[F]
        case None =>
          F.unit
      }
    }

    def timedDequeue1(duration: FiniteDuration, timer: Timer[F]): F[Option[A]] = {
      cancellableDequeue1().flatMap {
        case (Right(v), _) => F.pure(Some(v))
        case (Left(defer), cancel) =>
          val timeout = timer.sleep(duration)
          F.race(timeout, defer.get).flatMap {
            case Right(v) => F.pure(Some(v))
            case Left(_)  => cancel *> defer.tryGet
          }
      }
    }

    private def cancellableDequeue1(): F[(Either[TryableDeferred[F, A], A], F[Boolean])] = {
      Deferred.tryable[F, A].flatMap { defer =>
        ref.modify { s =>
          if (s.queue.isEmpty)
            (s.copy(deq = s.deq :+ defer), None)
          else
            (s.copy(queue = s.queue.drop(1)), Some(s.queue.take(1).head))
        }.map {
          case Some(h) =>
            (Right(h), F.pure(true))
          case None =>
            (Left(defer), ref.modify { s =>
              val exists = s.deq.exists(_ == defer)
              (s.copy(deq = s.deq.filterNot(_ == defer)), exists)
            })
        }
      }
    }

    private def fork[C](fa: F[C]) = {
      F.runAsync(fa)(_ => IO.unit).to[F]
    }
  }
}
