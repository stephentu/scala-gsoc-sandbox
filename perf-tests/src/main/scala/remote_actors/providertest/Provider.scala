package remote_actors
package providertest

import remote_actors.perftest._
import TestUtils._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import java.util.concurrent._
import java.util.concurrent.atomic._

import java.io._
import java.net._
import java.util.{Timer => JTimer, TimerTask}

import scala.collection.mutable.ArrayBuffer

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics

import scala.concurrent.SyncVar

object ProviderTest {
  val Port = 16780
  val LocalHostName = InetAddress.getLocalHost.getCanonicalHostName
  def main(args: Array[String]) {

    val provider = 
      if (containsOpt(args, "--nio")) 
        new NonBlockingServiceProvider 
      else 
        new BlockingServiceProvider 

    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val runTime = parseOptIntDefault(args,"--runtime=", 1) // 10 minutes per run instance
    val nodes = parseOptList(args, "--nodes=")
    val delayTime = parseOptIntDefault(args, "--delaytime=", 10000) // wait 10 seconds before starting

    val expname = parseOptStringDefault(args, "--expname=", "results_nodetest")

    val ExpId = System.currentTimeMillis
    val ExpDir = new File(List(expname, ExpId).mkString("_"))

    ExpDir.mkdirs()

    val connCallback = (l: Listener, c: ByteConnection) => {

    }

    val recvCallback = (conn: ByteConnection, msg: Array[Byte]) => {
      conn.send(msg)
    }

    val syncVar = new SyncVar[Boolean]
    val recvCallback0 = (conn: ByteConnection, msg: Array[Byte]) => {
      syncVar.set(true)
    }

    // listen on the port
    provider.listen(Port, connCallback, recvCallback)

    // do the delay, before starting, to wait for all nodes to come up
    Thread.sleep(delayTime)

    val random = new scala.util.Random

    def nextNode() = {
      val idx = random.nextInt(nodes.length)
      nodes(idx)
    }

    val connections = new ConcurrentHashMap[String, ByteConnection]
    def connect(node: String) = {
      val testConn = connections.get(node)
      if (testConn ne null)
        testConn
      else {
        synchronized {
          val testConn0 = connections.get(node)
          if (testConn0 ne null)
            testConn0
          else {
            val conn = provider.connect(Node(node, Port), recvCallback0)
            connections.put(node, conn)
            conn beforeTerminate { _ =>
              connections.remove(node)
            }
            conn
          }
        }
      }
    }

    val Message = new Array[Byte](512)

    (1 to numRuns).foreach(runId => {
      println("RUNID: " + runId)
      val startTime = System.currentTimeMillis  
      val endTime = startTime + (runTime * 60 * 1000)
      var numSent = 0
      var numRecv = 0
      while (System.currentTimeMillis < endTime) {
        val conn = connect(nextNode())
        syncVar.unset()
        conn.send(Message)
        numSent += 1
        syncVar.get
        numRecv += 1
      }
      println("RUNID: " + runId + " DONE")
      val elasped = System.currentTimeMillis - startTime
      println("Elasped (ms): " + elasped)
      println("Num Msgs Sent: " + numSent)
      println("Num Msgs Recv: " + numRecv)
    })
  }
}
