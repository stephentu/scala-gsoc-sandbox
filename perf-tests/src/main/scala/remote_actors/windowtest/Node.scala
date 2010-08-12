package remote_actors
package windowtest

import remote_actors.perftest._
import TestUtils._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import java.net._

import com.googlecode.avro.marker._

import java.util.concurrent._

case class WindowMessage(var i: Int, var b: Array[Byte]) 
  extends AvroRecord


object Node {
  val Bytes = (0 to 100).map(_.toByte).toArray
  val LocalHostName = InetAddress.getLocalHost.getCanonicalHostName
  def main(args: Array[String]) {
    val level = parseOptIntDefault(args, "--debug=", 1) 
    Debug.level = level
    val windowSize = parseOptIntDefault(args, "--windowsize=", 1024)
    val runTime = parseOptIntDefault(args,"--runtime=", 5) // 10 minutes per run instance
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val nodes = parseOptList(args, "--nodes=")
    val delayTime = parseOptIntDefault(args, "--delaytime=", 10000) // wait 10 seconds before starting

    implicit val config = 
      if (containsOpt(args, "--nio")) 
        new DefaultNonBlockingConfiguration {
          override def newSerializer() = {
            if (containsOpt(args, "--enhanced"))
              new remote_actors.javaserializer.EnhancedJavaSerializer
            else
              new JavaSerializer
          }
        }
      else new DefaultConfiguration {
        override def newSerializer() = {
          if (containsOpt(args, "--enhanced"))
            new remote_actors.javaserializer.EnhancedJavaSerializer
          else
            new JavaSerializer
        }
      }


    remoteActor(16780, 'server) {
      loop {
        react {
          case e => sender ! e
        }
      }
    }

    println("---------------------------------------------------------------------")
    println("Localhost name: " + LocalHostName)
    println("Mode (connect and listen) " + config)
    println("RunTime = " + runTime)
    println("NumRuns = " + numRuns)
    println("WindowSize = " + windowSize)
    println("Nodes to RR = " + nodes)
    println("---------------------------------------------------------------------")
    println()

    Thread.sleep(delayTime)

    (1 to numRuns).foreach(runNum => {
      val latch = new CountDownLatch(1)
      val a = new ClientActor(runNum, nodes.map(h => scala.actors.remote.Node(h, 16780).canonicalForm).toArray, runTime, numRuns, windowSize, latch)
      a.start()
      Thread.sleep(runTime * 60 * 1000)
      a ! STOP_RUNNING
      latch.await()
    })
  }

  case object STOP_RUNNING

  class ClientActor(runId: Int, nodes: Array[Node], runTime: Int, numRuns: Int, windowSize: Int, latch: CountDownLatch)(implicit cfg: Configuration) extends Actor {
    val random = new scala.util.Random
    def nextNode() = {
      val idx = random.nextInt(nodes.length)
      nodes(idx)
    }
    def sendNextMessage() {
      val nextN = nextNode()
      val server = select(nextN, 'server) 
      server ! WindowMessage(runId, Bytes)
      msgsSent += 1
    }
    var msgsSent = 0
    var msgsRecv = 0

    override def act() {
      println("Starting run " + runId)
      val startTime = System.currentTimeMillis
      (1 to windowSize).foreach(i => sendNextMessage())
      loop {
        react {
          case WindowMessage(id, _) if (id == runId) =>
            msgsRecv += 1
            sendNextMessage()
          case STOP_RUNNING =>
            println("Run " + runId + " completed")
            val endTime = System.currentTimeMillis
            val elasped = endTime - startTime
            println("Elasped time (ms): " + elasped)
            println("Num msgs recv: " + msgsRecv)
            println("Num msgs sent: " + msgsSent)
            latch.countDown()
            exit()
        }
      }
    }
  }

}
