package io.getquill.context.sql.encoding

import io.getquill.Spec
import io.getquill.context.sql.{ testContext => ctx }

class ArrayEncodingSpec extends Spec {
  import ctx._

  case class Raw()
  case class Decor(raw: Raw)

  object impl {
    implicit def encodeRaw[Col <: Seq[Raw]]: Encoder[Col] = encoder[Col]
    implicit def decodeRaw[Col <: Seq[Raw]]: Decoder[Col] = decoderUnsafe[Col]
    implicit val encodeDecor: MappedEncoding[Decor, Raw] = MappedEncoding(_.raw)
    implicit val decodeDecor: MappedEncoding[Raw, Decor] = MappedEncoding(Decor.apply)
  }

  "Provide array support with MappingEncoding" - {
    "encoders" in {
      import impl.{ encodeRaw, encodeDecor }
      arrayMappedEncoder[Decor, Raw, Vector]
    }
    "decoders" in {
      import impl.{ decodeRaw, decodeDecor }
      val d1 = implicitly[Decoder[Vector[Decor]]]
      println(d1.getClass)
    }
  }

}
