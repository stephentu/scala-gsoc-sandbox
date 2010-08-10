package remote_actors
package avro_vs_java_test

import remote_actors.perftest._
import TestUtils._

import remote_actors.avro._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Client {
  val ByteArray = (0 to 8192).map(_.toByte).toArray
  def main(args: Array[String]) {
    implicit val config = 
      if (containsOpt(args, "--avro")) {
        println("Using AVRO")
        //new HasSingleClassAvroSerializer[TestMessage] with HasNonBlockingMode
        new HasMultiClassAvroSerializer with HasNonBlockingMode with HasAvroMessageCreator
      } else {
        println("Using JAVA")
        new DefaultNonBlockingConfiguration
      }
    val numMsgs = parseOptIntDefault(args, "--nummsgs=", 10000)
    println("numMsgs: " + numMsgs)
    actor {
      val echo = select(Node(9100), 'echo)
      var runsLeft = 5
      var i = 1
      loopWhile(runsLeft > 0) {
        println("Starting run: " + i)
        val startTime = System.currentTimeMillis
        (1 to numMsgs).foreach(i => echo !? TestMessage0(Inner1("HELLO, WORLD"), Inner2(102341L)))
        val endTime = System.currentTimeMillis
        val elasped = endTime - startTime
        println("Time elasped (ms): " + elasped)
        runsLeft -= 1
        i += 1
      }
    }
  }
}
