package remote_actors.windowtest

import remote_actors.perftest._
import TestUtils._

import java.io._

import scala.actors.remote.AsyncSend

object Simple {

  def main(args: Array[String]) {
    for (i <- 1 to 5) {
      val serializer = 
        if (containsOpt(args, "--enhanced")) {
          println("Using enhanced")
          new remote_actors.javaserializer.EnhancedJavaSerializer
        } else {
          println("Using regular")
          new scala.actors.remote.JavaSerializer
        }

      val Bytes = (0 to 128).map(_.toByte).toArray

      val start1 = System.currentTimeMillis
      for (j <- 1 to 1000000) {
        val msg = WindowMessage(j, Bytes)
        val baos = new ByteArrayOutputStream(1024)
        serializer.writeAsyncSend(baos, "sendername", "receivername", msg)
        serializer.read(baos.toByteArray) match {
          case AsyncSend(_, _, _) =>
          case _ =>
            error("Expected async send")
        }

      }
      val end1 = System.currentTimeMillis

      println("Run %d took %d ms".format(i, (end1 - start1)))
    }
  }

}
