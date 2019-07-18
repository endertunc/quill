package io.getquill.context.async

import java.util.UUID
import io.getquill.context.Context

trait UUIDObjectEncoding {
  this: Context[_, _] with Encoders with Decoders =>

  implicit val uuidEncoder: Encoder[UUID] = encoder[UUID](SqlTypes.UUID)

  implicit val uuidDecoder: Decoder[UUID] =
    AsyncDecoder(SqlTypes.UUID)(
      (index: Index, row: ResultRow) => row(index) match {
        case value: UUID => value
      }
    )
}
