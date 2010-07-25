package localhost.test

import scala.actors.remote._

object TestHelper {
  def makeConfig(args: Array[String]) = {
    import ParseOpt._
    val mode = if (containsOpt(args, "--nio")) {
      println("NonBlocking")
      ServiceMode.NonBlocking
    } else {
      println("Blocking")
      ServiceMode.Blocking
    }
    new DefaultConfiguration {
      override def aliveMode = mode
      override def selectMode = mode
    }
  }
}
