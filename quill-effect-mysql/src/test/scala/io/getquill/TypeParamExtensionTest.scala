package io.getquill

import io.getquill.context.Context
import scala.language.higherKinds

// Testing we are passing type params explicitly into AsyncContext, otherwise
// this file will fail to compile

trait BaseExtensions {
  val context: Context[MySQLDialect, _]
}

trait AsyncExtensions[F[_]] extends BaseExtensions {
  override val context: MysqlAsyncEffectContext[F, _]
}
