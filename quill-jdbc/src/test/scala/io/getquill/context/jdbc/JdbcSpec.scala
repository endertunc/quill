package io.getquill.context.jdbc

import org.scalactic._
import org.scalatest.enablers.{ Aggregating, Sequencing }

trait ResultAggregating {
  this: JdbcContext[_, _] =>

  implicit def aggregatingNatureOfResultList[E](implicit equality: Equality[E]): Aggregating[Result[List[E]]] = {
    Aggregating.aggregatingNatureOfGenTraversable[E, List]
  }
  implicit def sequencingNatureOfResultList[E](implicit equality: Equality[E]): Sequencing[Result[List[E]]] = {
    Sequencing.sequencingNatureOfGenSeq[E, List]
  }
}
