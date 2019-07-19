package io.getquill.async.effect

import java.nio.charset.Charset

import cats.syntax.all._
import cats.effect._
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.Connection
import com.github.mauricio.async.db.SSLConfiguration
import com.github.mauricio.async.db.util.AbstractURIParser
import com.typesafe.config.Config
import io.getquill.effect._
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util._
import scala.language.higherKinds

abstract class AsyncContextConfig[F[_]: Timer, C <: Connection](
  config:            Config,
  connectionFactory: Configuration => C,
  uriParser:         AbstractURIParser
)(implicit F: ConcurrentEffect[F], CS: ContextShift[F]) {

  def url = Try(config.getString("url")).toOption
  def user = Try(config.getString("user")).toOption
  def password = Try(config.getString("password")).toOption
  def database = Try(config.getString("database")).toOption
  def port = Try(config.getInt("port")).toOption
  def host = Try(config.getString("host")).toOption
  def sslProps = Map(
    "sslmode" -> Try(config.getString("sslmode")).toOption,
    "sslrootcert" -> Try(config.getString("sslrootcert")).toOption
  ).collect { case (key, Some(value)) => key -> value }
  def charset = Try(Charset.forName(config.getString("charset"))).toOption
  def maximumMessageSize = Try(config.getInt("maximumMessageSize")).toOption
  def connectTimeout = Try(Duration(config.getString("connectTimeout"))).toOption
  def testTimeout = Try(Duration(config.getString("testTimeout"))).toOption
  def queryTimeout = Try(Duration(config.getString("queryTimeout"))).toOption

  def configuration = {
    var c =
      url match {
        case Some(url) => uriParser.parseOrDie(url)
        case _         => uriParser.DEFAULT
      }
    user.foreach(p => c = c.copy(username = p))
    if (password.nonEmpty) {
      c = c.copy(password = password)
    }
    if (database.nonEmpty) {
      c = c.copy(database = database)
    }
    port.foreach(p => c = c.copy(port = p))
    host.foreach(p => c = c.copy(host = p))
    c = c.copy(ssl = SSLConfiguration(sslProps))
    charset.foreach(p => c = c.copy(charset = p))
    maximumMessageSize.foreach(p => c = c.copy(maximumMessageSize = p))
    connectTimeout.foreach(p => c = c.copy(connectTimeout = p))
    testTimeout.foreach(p => c = c.copy(testTimeout = p))
    c = c.copy(queryTimeout = queryTimeout)
    c
  }

  def poolMaxObjects = Try(config.getInt("poolMaxObjects")).getOrElse(32)
  def poolValidationInterval = Try(config.getLong("poolValidationInterval")).getOrElse(100000L)
  def poolReconnectInterval = Try(Duration(config.getString("poolReconnectInterval"))).getOrElse(5.seconds)
  def poolReconnectFailures = Try(config.getInt("poolReconnectFailures")).getOrElse(20)

  private def fromFuture[A](f: => Future[A]): F[A] = {
    def toF: F[A] = {
      val strictF = f
      strictF.value match {
        case Some(result) =>
          result match {
            case Success(a) => F.pure(a)
            case Failure(e) => F.raiseError(e)
          }
        case _ =>
          F.async { cb =>
            strictF.onComplete(r => cb(r match {
              case Success(a) => Right(a)
              case Failure(e) => Left(e)
            }))(io.getquill.effect.trampoline)
          }
      }
    }
    F.guarantee(F.defer(toF))(CS.shift)
  }

  def poolConfiguration =
    PoolConfig[F, C](
      poolSize = poolMaxObjects,
      reconnectInterval = poolReconnectInterval.asInstanceOf[FiniteDuration],
      minReconnectFailures = poolReconnectFailures,
      waitTimeout = queryTimeout.getOrElse(5.seconds).asInstanceOf[FiniteDuration],
      factory = () => F.pure(connectionFactory(configuration)),
      isDead = c => !c.isConnected,
      connect = c => {
        fromFuture(c.connect).void
      },
      release = c => {
        if (c.isConnected) {
          fromFuture(c.disconnect).void
        } else {
          F.unit
        }
      }
    )

  def pool = F.toIO(Pool[F, C](poolConfiguration)).unsafeRunSync()
}
