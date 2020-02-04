package io.getquill.effect

import cats.effect._
import cats.effect.concurrent._
import cats.effect.syntax.all._
import cats.instances.vector._
import cats.syntax.all._
import java.util.concurrent.atomic._
import org.slf4j._
import scala.language.higherKinds
import scala.concurrent.duration._

trait Pool[F[_], C] {
  def withConnection[B](f: C => F[B]): F[B]
  def close(): F[Unit]
}

object Pool {
  object WaitTimeout extends Exception
  def apply[F[_], C](config: PoolConfig[F, C])(implicit F: ConcurrentEffect[F], T: Timer[F]): F[Pool[F, C]] = {

    val conns: Vector[Option[Conn[C]]] = Vector.fill(config.poolSize)(None)

    val healings: F[Vector[SelfHealing[F, C]]] = {
      conns.traverse { conn =>
        Ref[F].of(conn).map { ref =>
          new SelfHealing[F, C](config, ref)
        }
      }
    }
    for {
      hs <- healings
      q <- Queue(hs)
    } yield new AsyncPool(config, q, hs)
  }
}

private class AsyncPool[F[_]: Timer: Sync, C](
  config:  PoolConfig[F, C],
  conns:   Queue[F, SelfHealing[F, C]],
  holders: Vector[SelfHealing[F, C]]
)(implicit F: Bracket[F, Throwable]) extends Pool[F, C] {

  def close(): F[Unit] = {
    holders.traverse(_.release).void
  }

  def withConnection[B](f: C => F[B]): F[B] = {
    conns.timedDequeue1(config.waitTimeout).flatMap {
      case None => F.raiseError(Pool.WaitTimeout)
      case Some(c) =>
        F.guarantee {
          F.bracketCase(c.get)(conn => f(conn.c)) {
            case (conn, ExitCase.Error(e)) =>
              Sync[F].delay {
                conn.failures.incrementAndGet
              }.void
            case (conn, ExitCase.Completed) =>
              Sync[F].delay(conn.failures.set(0))
            case (conn, ExitCase.Canceled) =>
              F.unit
          }
        }(conns.enqueue1(c))
    }
  }
}

case class PoolConfig[F[_], C](
  poolSize:             Int,
  reconnectInterval:    FiniteDuration,
  waitTimeout:          FiniteDuration,
  minReconnectFailures: Int,
  factory:              () => F[C],
  isDead:               C => Boolean,
  release:              C => F[Unit],
  connect:              C => F[Unit]
)

private final class SelfHealing[F[_], C](
  config: PoolConfig[F, C],
  ref:    Ref[F, Option[Conn[C]]]
)(implicit F: Concurrent[F], T: Timer[F]) {

  private val logger = LoggerFactory.getLogger(classOf[SelfHealing[F, C]])

  def get = healed

  def release = ref.get.flatMap {
    case Some(c) => tryRelease(c).void
    case None    => F.unit
  }

  private def tryRelease(c: Conn[C]): F[Unit] = {
    val release = Either.catchNonFatal(config.release(c.c)).liftTo[F].flatten.handleError { e =>
      logger.warn(s"[SelfHealing-tryRelease] Error release connection", e)
    }
    F.race(release, T.sleep(3.seconds)).void
  }

  private def replace(old: Conn[C]): F[Conn[C]] = {
    tryRelease(old) *> config.factory().flatMap { c =>
      config.connect(c).as(Conn(c))
    }
  }

  private def sick(c: Conn[C]) = {
    val liveTime = (System.currentTimeMillis - c.created)
    (config.isDead(c.c) || c.failures.get >= config.minReconnectFailures) && liveTime >= config.reconnectInterval.toMillis
  }

  private def healed: F[Conn[C]] = {
    ref.access.flatMap {
      case (Some(c), setter) if sick(c) =>
        replace(c).flatMap { nc =>
          setter(Some(nc)).ifM(F.pure(nc), tryRelease(nc) >> healed)
        }
      case (Some(c), _) => F.pure(c)
      case (None, setter) =>
        config.factory().flatTap(c => config.connect(c)).map(Conn(_)).flatMap { nc =>
          setter(Some(nc)).ifM(F.pure(nc), tryRelease(nc) >> healed)
        }

    }
  }
}

private final case class Conn[C](
  created:  Long,
  failures: AtomicInteger,
  c:        C
)

private object Conn {
  def apply[C](c: C) = new Conn(System.currentTimeMillis, new AtomicInteger(0), c)
}

private final case class State[F[_], A](
  queue: Vector[A],
  deq:   Vector[Deferred[F, A]]
)

private object Queue {
  def apply[F[_]: ConcurrentEffect, A](init: Vector[A]) = {
    Ref[F].of(State[F, A](queue = init, deq = Vector.empty)).map { state =>
      new Queue[F, A](state)
    }
  }
}

private final class Queue[F[_], A](ref: Ref[F, State[F, A]])(implicit F: ConcurrentEffect[F]) {

  def enqueue1(a: A): F[Unit] = {
    ref.modify { s =>
      if (s.deq.isEmpty) {
        (s.copy(queue = s.queue :+ a), None)
      } else {
        (s.copy(deq = s.deq.tail), Some(s.deq.head))
      }
    }.flatMap {
      case Some(h) =>
        h.complete(a).runAsync(_ => IO.unit).to[F]
      case None =>
        F.unit
    }
  }

  def timedDequeue1(duration: FiniteDuration)(implicit timer: Timer[F]): F[Option[A]] = {
    cancellableDequeue1().flatMap {
      case (Right(v), _) => F.pure(Some(v))
      case (Left(defer), cancel) =>
        val timeout = timer.sleep(duration)
        F.race(timeout, defer.get).flatMap {
          case Right(v) => F.pure(Some(v))
          case Left(_)  => cancel.as(None)
        }
    }
  }

  private def cancellableDequeue1(): F[(Either[Deferred[F, A], A], F[Unit])] = {
    Deferred[F, A].flatMap { defer =>
      ref.modify { s =>
        if (s.queue.isEmpty)
          (s.copy(deq = s.deq :+ defer), None)
        else
          (s.copy(queue = s.queue.drop(1)), Some(s.queue.take(1).head))
      }.map {
        case Some(h) =>
          (Right(h), F.unit)
        case None =>
          (Left(defer), ref.modify { s =>
            (s.copy(deq = s.deq.filterNot(_ == defer)), {})
          })
      }
    }
  }
}
