package io.getquill

import cats.effect._
import com.github.mauricio.async.db.{ QueryResult => DBQueryResult }
import com.github.mauricio.async.db.postgresql.PostgreSQLConnection
import io.getquill.ReturnAction.{ ReturnColumns, ReturnNothing, ReturnRecord }
import io.getquill.effect._
import io.getquill.context.async.{
  ArrayDecoders,
  ArrayEncoders,
  UUIDObjectEncoding
}
import io.getquill.async.effect.AsyncContext
import io.getquill.context.sql.idiom.SqlIdiom
import io.getquill.util.Messages.fail
import io.getquill.util.LoadConfig
import scala.language.higherKinds
import com.typesafe.config._

class PostgresAsyncContext[F[_]: ConcurrentEffect: Timer: ContextShift, D <: SqlIdiom, N <: NamingStrategy](
  idiom:  D,
  naming: N,
  pool:   Pool[F, PostgreSQLConnection]
) extends AsyncContext[F, D, N, PostgreSQLConnection](idiom, naming, pool)
  with ArrayEncoders
  with ArrayDecoders
  with UUIDObjectEncoding {

  def this(idiom: D, naming: N, config: PostgresqlAsyncEffectContextConfig[F]) = {
    this(idiom, naming, config.pool)
  }

  def this(idiom: D, naming: N, config: Config) = {
    this(idiom, naming, PostgresqlAsyncEffectContextConfig[F](config))
  }

  def this(idiom: D, naming: N, configPrefix: String) = {
    this(idiom, naming, LoadConfig(configPrefix))
  }

  override protected def extractActionResult[O](
    returningAction:    ReturnAction,
    returningExtractor: Extractor[O]
  )(result: DBQueryResult): O = {
    result.rows match {
      case Some(r) if r.nonEmpty =>
        returningExtractor(r.head)
      case _ =>
        fail("This is a bug. Cannot extract returning value.")
    }
  }

  override protected def expandAction(
    sql:             String,
    returningAction: ReturnAction
  ): String =
    returningAction match {
      // The Postgres dialect will create SQL that has a 'RETURNING' clause so we don't have to add one.
      case ReturnRecord           => s"$sql"
      // The Postgres dialect will not actually use these below variants but in case we decide to plug
      // in some other dialect into this context...
      case ReturnColumns(columns) => s"$sql RETURNING ${columns.mkString(", ")}"
      case ReturnNothing          => s"$sql"
    }
}
