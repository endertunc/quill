package io.getquill.context.async

import java.util.UUID
import io.getquill.context.Context

trait UUIDStringEncoding {
  this: Context[_, _] with Encoders with Decoders =>

  implicit val uuidEncoder: Encoder[UUID] = encoder[UUID]((v: UUID) => v.toString, SqlTypes.UUID)

  implicit val uuidDecoder: Decoder[UUID] =
    AsyncDecoder(SqlTypes.UUID)(
      (index: Index, row: ResultRow) => row(index) match {
        case value: String => UUID.fromString(value)
      }
    )
}
