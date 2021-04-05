package io.getquill

import cats.effect._
import com.github.mauricio.async.db.Configuration
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.mysql.util.URLParser
import com.typesafe.config.Config
import io.getquill.async.effect.AsyncContextConfig
import scala.language.higherKinds

case class MysqlAsyncEffectContextConfig[F[_]: Async](config: Config, factory: Configuration => MySQLConnection = { c => new MySQLConnection(c) })
  extends AsyncContextConfig[F, MySQLConnection](config, factory, URLParser)
