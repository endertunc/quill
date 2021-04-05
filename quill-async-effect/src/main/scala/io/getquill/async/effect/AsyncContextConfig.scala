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
import scala.concurrent.duration._
import scala.util._
import scala.language.higherKinds

abstract class AsyncContextConfig[F[_], C <: Connection](
  config:            Config,
  connectionFactory: Configuration => C,
  uriParser:         AbstractURIParser
)(implicit F: Async[F]) {

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

  def poolConfiguration =
    PoolConfig[F, C](
      poolSize = poolMaxObjects,
      reconnectInterval = poolReconnectInterval.asInstanceOf[FiniteDuration],
      waitTimeout = queryTimeout.getOrElse(5.seconds).asInstanceOf[FiniteDuration],
      factory = () => {
        F.delay(connectionFactory(configuration)).flatMap { c =>
          F.fromFuture(F.delay(c.connect)).map(_.asInstanceOf[C])
        }
      },
      check = c => {
        F.fromFuture(F.delay(c.sendQuery("SELECT 1")))
      }.as(true),
      release = c => {
        if (c.isConnected) {
          F.fromFuture(F.delay(c.disconnect)).void
        } else {
          F.unit
        }
      }
    )

  def pool = Pool[F, C](poolConfiguration)
}
