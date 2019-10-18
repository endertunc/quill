package io.getquill.context.jdbc

import io.getquill._
import io.getquill.context.sql._

package object postgres {

  object testContext extends PostgresJdbcContext(Literal, "testPostgresDB") with TestEntities with TestEncoders with TestDecoders with ResultAggregating

}
