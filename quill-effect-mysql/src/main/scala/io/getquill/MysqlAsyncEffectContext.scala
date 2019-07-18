package io.getquill

import cats.effect._
import com.github.mauricio.async.db.{ QueryResult => DBQueryResult }
import com.github.mauricio.async.db.mysql.MySQLConnection
import com.github.mauricio.async.db.mysql.MySQLQueryResult
import com.github.mauricio.async.db.general.ArrayRowData
import com.typesafe.config._
import io.getquill.effect._
import io.getquill.async.effect.AsyncContext
import io.getquill.context.async.UUIDStringEncoding
import io.getquill.util.Messages.fail
import io.getquill.util.LoadConfig
import scala.language.higherKinds

class MysqlAsyncEffectContext[F[_]: ConcurrentEffect: Timer: ContextShift, N <: NamingStrategy](
  naming: N,
  pool:   Pool[F, MySQLConnection]
) extends AsyncContext[F, MySQLDialect, N, MySQLConnection](
  MySQLDialect,
  naming,
  pool
) with UUIDStringEncoding {

  def this(naming: N, config: MysqlAsyncEffectContextConfig[F]) = {
    this(naming, config.pool)
  }

  def this(naming: N, config: Config) = {
    this(naming, MysqlAsyncEffectContextConfig[F](config))
  }

  def this(naming: N, configPrefix: String) = {
    this(naming, LoadConfig(configPrefix))
  }

  override protected def extractActionResult[O](returningAction: ReturnAction, returningExtractor: Extractor[O])(result: DBQueryResult): O = {
    result match {
      case r: MySQLQueryResult =>
        returningExtractor(new ArrayRowData(0, Map.empty, Array(r.lastInsertId)))
      case _ =>
        fail("This is a bug. Cannot extract returning value.")
    }
  }
}
