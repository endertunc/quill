package io.getquill

import java.io.File
import cats.effect.IO
import com.github.mauricio.async.db.SSLConfiguration
import com.github.mauricio.async.db.SSLConfiguration.Mode
import com.typesafe.config.{ ConfigFactory, ConfigValueFactory }
import scala.concurrent.ExecutionContext

class PostgresAsyncEffectContextConfigSpec extends Spec {

  implicit val timer = IO.timer(ExecutionContext.global)
  implicit val appCS = IO.contextShift(ExecutionContext.global)

  "parses ssl config" in {
    val config = ConfigFactory.empty()
      .withValue("user", ConfigValueFactory.fromAnyRef("user"))
      .withValue("port", ConfigValueFactory.fromAnyRef(5432))
      .withValue("host", ConfigValueFactory.fromAnyRef("host"))
      .withValue("sslmode", ConfigValueFactory.fromAnyRef("require"))
      .withValue("sslrootcert", ConfigValueFactory.fromAnyRef("./file.crt"))
    val context = new PostgresAsyncEffectContextConfig[IO](config)
    context.configuration.ssl mustEqual SSLConfiguration(Mode.Require, Some(new File("./file.crt")))
  }
}
