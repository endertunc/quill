package io.getquill.context.jdbc

import io.getquill._
import io.getquill.context.sql._

package object oracle {

  object testContext extends OracleJdbcContext(Literal, "testOracleDB") with TestEntities with TestEncoders with TestDecoders with ResultAggregating

}
