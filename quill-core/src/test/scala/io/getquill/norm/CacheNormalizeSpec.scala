package io.getquill.norm

import io.getquill.{ NormalizeCaching, Spec }

class CachedNormalizeSpec extends Spec {

  val cached = NormalizeCaching(Normalize.apply)
  val gen = new QueryGenerator(1)

  "normalize ast" - {
    "consists with non-cached `Normalize`" in {
      for (i <- (3 to 15)) {
        for (j <- (0 until 30)) {
          val query = gen(i)
          val r = Normalize(query)
          val cr = cached.apply(query)
          r mustEqual (cr)
        }
      }
    }
    "normalize faster for complex query" in {
      for (i <- (10 to 15)) {
        val samples = 10
        val iteration = 100
        val (sumNoCache, sumCached) = (0 until samples).foldLeft((0L, 0L)) {
          case ((sum0, sum1), _) =>
            val query = gen(i)
            val (cost0, cost1) = (0 until iteration).foldLeft((0L, 0L)) {
              case ((t0, t1), _) =>
                val si0 = System.currentTimeMillis()
                val ri0 = Normalize(query)
                val ei0 = System.currentTimeMillis()
                val ri1 = cached.apply(query)
                val ei1 = System.currentTimeMillis()
                ri0 mustEqual (ri1)
                (t0 + (ei0 - si0), t1 + (ei1 - ei0))
            }
            (sum0 + cost0, sum1 + cost1)
        }

        val rate = (sumCached * 1.0 / sumNoCache)
        println(s"Level ${i}: ${1 / rate} times faster")
        rate must be < (1.0)
      }

    }
  }
}
