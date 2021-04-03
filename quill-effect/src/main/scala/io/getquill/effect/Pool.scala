package io.getquill.effect

import cats.effect._
import cats.syntax.all._
import scala.language.higherKinds
import scala.concurrent.duration._

trait Pool[F[_], C] {
  def withConnection[B](f: C => F[B]): F[B]
  def close(): F[Unit]
}

object Pool {
  object WaitTimeout extends Exception("WaitTimeout")
  def apply[F[_], C](config: PoolConfig[F, C])(implicit F: Async[F]): F[Pool[F, C]] = {

    val healings: F[Vector[SelfHealing[F, C]]] = {
      Vector.fill(config.poolSize)(SelfHealing[F, C](config.factory(), config.release, config.check, config.reconnectInterval.toMillis)).sequence
    }
    for {
      hs <- healings
      q <- Queue.of(hs)
    } yield new AsyncPool(config, q, hs)
  }
}

private class AsyncPool[F[_], C](
  config:  PoolConfig[F, C],
  conns:   Queue[F, SelfHealing[F, C]],
  holders: Vector[SelfHealing[F, C]]
)(implicit val F: Async[F]) extends Pool[F, C] {

  def close(): F[Unit] = {
    holders.traverse(_.release).void
  }

  def withConnection[B](f: C => F[B]): F[B] = {
    val take: F[SelfHealing[F, C]] = conns.timedDequeue1(config.waitTimeout).flatMap {
      case Some(c) => F.pure(c)
      case None    => F.raiseError(Pool.WaitTimeout)
    }
    def conn = Resource.make(take)(conns.enqueue1)
    conn.use(c => c.get.use(f))
  }
}

case class PoolConfig[F[_], C](
  poolSize:          Int,
  reconnectInterval: FiniteDuration,
  waitTimeout:       FiniteDuration,
  factory:           () => F[C],
  check:             C => F[Boolean],
  release:           C => F[Unit]
)
