package io.getquill.async.effect

import com.github.mauricio.async.db.Connection
import com.github.mauricio.async.db.{ QueryResult => DBQueryResult }
import com.github.mauricio.async.db.RowData

import cats.effect._
import cats.syntax.all._
import cats.instances.list._
import com.github.mauricio.async.db.{ QueryResult => DBQueryResult, _ }
import com.github.mauricio.async.db.RowData

import io.getquill.effect._
import io.getquill.context.async.{ Encoders, Decoders }
import io.getquill.context.sql.SqlContext
import io.getquill.context.sql.idiom.SqlIdiom
import io.getquill.NamingStrategy
import io.getquill.util.ContextLogger
import io.getquill.context.{ Context, TranslateContext }
import io.getquill.context.sql.SqlContext
import io.getquill.context.sql.idiom.SqlIdiom
import io.getquill.{ NamingStrategy, ReturnAction }
import io.getquill.util.ContextLogger
import io.getquill.context.{ Context, TranslateContext }

import scala.language.higherKinds
import scala.util.{ Try, Success, Failure }
import scala.concurrent.Future

abstract class AsyncContext[F[_], D <: SqlIdiom, N <: NamingStrategy, C <: Connection](val idiom: D, val naming: N, pool: Pool[F, C])(implicit _F: ConcurrentEffect[F], CS: ContextShift[F])
  extends Context[D, N]
  with TranslateContext
  with SqlContext[D, N]
  with Decoders
  with Encoders
  with ScalaFutureEffect[F, C] {

  protected val F = _F
  private val logger = ContextLogger(classOf[AsyncContext[F, _, _, _]])

  override type PrepareRow = List[Any]
  override type ResultRow = RowData

  override type RunQueryResult[T] = Seq[T]
  override type RunQuerySingleResult[T] = T
  override type RunActionResult = Long
  override type RunActionReturningResult[T] = T
  override type RunBatchActionResult = Seq[Long]
  override type RunBatchActionReturningResult[T] = Seq[T]

  private def fromFuture[A](f: => Future[A]): F[A] = {
    def toF: F[A] = {
      val strict = f
      strict.value match {
        case Some(result) =>
          result match {
            case Success(a) => F.pure(a)
            case Failure(e) => F.raiseError(e)
          }
        case _ =>
          F.async { cb =>
            strict.onComplete(r => cb(r match {
              case Success(a) => Right(a)
              case Failure(e) => Left(e)
            }))(_root_.io.getquill.effect.trampoline)
          }
      }
    }
    F.guarantee(F.defer(toF))(CS.shift)
  }

  override def close = {
    F.toIO(pool.close()).unsafeRunSync()
  }

  protected def expandAction(sql: String, returningAction: ReturnAction) = sql

  protected def performEffect[A](f: C => Future[A], transactional: Boolean) = {
    pool.withConnection { c => fromFuture(f(c)) }
  }

  protected def extractActionResult[O](returningAction: ReturnAction, extractor: Extractor[O])(result: DBQueryResult): O

  def probe(sql: String) =
    Try {
      F.toIO(executeQuery(sql).run).unsafeRunSync()
    }

  def executeQuery[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[Seq[T]] = DBIO { c =>
    val (params, values) = prepare(Nil)
    logger.logQuery(sql, params)
    c.sendPreparedStatement(sql, values).map {
      _.rows match {
        case Some(rows) => rows.map(extractor)
        case None       => Nil
      }
    }
  }

  def executeQuerySingle[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T] = identityExtractor): Result[T] =
    executeQuery(sql, prepare, extractor).map(r => handleSingleResult(r.toList))

  def executeAction[T](sql: String, prepare: Prepare = identityPrepare): Result[Long] = DBIO { c =>
    val (params, values) = prepare(Nil)
    logger.logQuery(sql, params)
    c.sendPreparedStatement(sql, values).map(_.rowsAffected)
  }

  def executeActionReturning[T](sql: String, prepare: Prepare = identityPrepare, extractor: Extractor[T], returningColumn: ReturnAction): Result[T] = DBIO { c =>
    val expanded = expandAction(sql, returningColumn)
    val (params, values) = prepare(Nil)
    logger.logQuery(sql, params)
    c.sendPreparedStatement(expanded, values).map(extractActionResult(returningColumn, extractor))
  }

  def executeBatchAction(groups: List[BatchGroup]): Result[Seq[Long]] = {
    groups.traverse {
      case BatchGroup(sql, prepare) =>
        prepare.traverse { p =>
          executeAction(sql, p)
        }
    }.map(_.flatten)
  }

  def executeBatchActionReturning[T](groups: List[BatchGroupReturning], extractor: Extractor[T]): Result[Seq[T]] = {
    groups.traverse {
      case BatchGroupReturning(sql, column, prepare) =>
        prepare.traverse { p =>
          executeActionReturning(sql, p, extractor, column)
        }
    }.map(_.flatten)
  }

  override private[getquill] def prepareParams(statement: String, prepare: Prepare): Seq[String] = {
    prepare(Nil)._2.map(param => prepareParam(param))
  }
}
