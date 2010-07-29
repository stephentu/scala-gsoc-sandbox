package remote_actors
package nodetest

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

case object StopAnnouncer

case class Results(runId: Int, msgsSent: Int, msgsRecv: Int, numTimeouts: Int)

object Log {
    var debug = false
    def debug(s: => String) {
        if (debug) println(s)
    }
}

object Node {

  val LocalHostName = InetAddress.getLocalHost.getCanonicalHostName
  val DefaultNodes = (10 to 15).map(i => "r" + i).toList
  def main(args: Array[String]) {
    val level = parseOptIntDefault(args, "--debug=", 1) 
    Debug.level = level
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    val numActors = parseOptIntDefault(args,"--numactors=", 1000)
    val ports = parseOptList(args,"--ports=").map(_.toInt)
    val runTime = parseOptIntDefault(args,"--runtime=", 1) // 10 minutes per run instance
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val nodes = parseOptListDefault(args, "--nodes=", DefaultNodes)
    val delayTime = parseOptIntDefault(args, "--delaytime=", 10000) // wait 10 seconds before starting

    val ExpRootDir = 
        if (containsOpt(args, "--expdir=")) Some(new File(parseOptString(args, "--expdir=")))
        else None
      
    val expname = parseOptStringDefault(args, "--expname=", "results_nodetest")

    val ExpId = System.currentTimeMillis
    val ExpDir = ExpRootDir match {
      case Some(rootDir) =>
        new File(rootDir, List(expname, ExpId).mkString("_"))
      case None =>
        new File(List(expname, ExpId).mkString("_"))
    }

    ExpDir.mkdirs()

    println("---------------------------------------------------------------------")
    println("Localhost name: " + LocalHostName)
    println("Ports: " + ports)
    println("Mode (connect and listen) " + mode)
    println("NumActors= " + numActors)
    println("RunTime = " + runTime)
    println("NumRuns = " + numRuns)
    println("Nodes to RR = " + nodes)
    println("---------------------------------------------------------------------")
    println()

    

    val run = new Run(delayTime, ExpDir, ports, mode, nodes.flatMap(h => ports.map(p => scala.actors.remote.Node(h, p).canonicalForm)).toArray, numActors, runTime)
    run.execute(numRuns)


  }

  case object STOP
  case object START
  case object COLLECT

  class Run(delayTime: Int, ExpDir: File, ports: List[Int], mode: ServiceMode.Value, nodes: Array[Node], numActors: Int, runTime: Int) {
    val ExpName = ExpDir.getName

    val messageSize = 16 
    val message = newMessage(messageSize)
    val actualMsgSize = javaSerializationMessageSize(NodeMessage(message, 
                                                                 System.nanoTime, 
                                                                 FromActor(LocalHostName, Symbol("actor" + numActors)),
                                                                 ToActor("TEST", Symbol("actor" + numActors)),
                                                                 0, 
                                                                 0, 
                                                                 false))

    implicit object cfg extends Configuration[DefaultProxyImpl] with HasJavaSerializer {
      override def aliveMode  = mode
      override def selectMode = mode
    }

    class RunActor(id: Int, writer: PrintWriter) extends Actor {
      override def exceptionHandler: PartialFunction[Exception, Unit] = {
        case e: Exception => 
          System.err.println(this + ": Caught exception: " + e.getMessage)
          e.printStackTrace()
          System.exit(1)
      }
      val random = new scala.util.Random
      def nextNode() = {
        val idx = random.nextInt(nodes.length)
        nodes(idx)
      }
      def nextSymbol() = {
        Symbol("actor" + random.nextInt(numActors))
      }
      val ThisSymbol = Symbol("actor" + id)

      var msgsSent = 0
      var msgsRecv = 0
      var runId = 0
      var numTimeouts = 0
      val timeoutNodes = new ArrayBuffer[ToActor]

      override def toString = "<RunActor " + ThisSymbol + ">"

      var lastMsg: NodeMessage = _

      private def sendNextMsg() {
        val nextN = nextNode()
        val nextSym = nextSymbol()
        val server = select(nextN, nextSym)
        val msg = NodeMessage(message, 
                              System.nanoTime, 
                              FromActor(LocalHostName, ThisSymbol),
                              ToActor(nextN.address, nextSym), 
                              runId, 
                              msgsSent, 
                              false)
        lastMsg = msg
        Log.debug(this + ": Sending message: " + msg + " to actor: " + nextSym + " with proxy: " + server)
        server ! msg  
        msgsSent += 1
      }

      var timer: Timer = _
      val roundTripTimes: ArrayBuffer[Long] = new ArrayBuffer[Long] 
      var started = false

      override def act() {
        ports.foreach(port => alive(port))
        register(ThisSymbol, self)
        //println("actor " + id + " alive and registered on port " + port)
        loop {
          reactWithin(60000) {
            case START if (!started) =>
              timer = new Timer
              roundTripTimes.clear() // stored in NS
              runId += 1
              msgsSent = 0
              msgsRecv = 0
              numTimeouts = 0
              timeoutNodes.clear()
              timer.start()
              Log.debug(this + ": starting run " + runId)
              started = true
              sendNextMsg()
            case TIMEOUT if (started) =>
              numTimeouts += 1
              timeoutNodes += lastMsg.toActor
              Log.debug(this + ": TIMEOUT when started")
              sendNextMsg()
            case TIMEOUT if (!started) =>
              Log.debug(this + ": TIMEOUT, but not started")
            case m @ NodeMessage(recvMessage, 
                                 _, 
                                 FromActor(LocalHostName, ThisSymbol),
                                 _, 
                                 recvRunId, 
                                 recvI, 
                                 true) if (recvRunId == runId && recvI >= msgsRecv && started) =>
              if ((msgsRecv % 100) == 0 && !java.util.Arrays.equals(message, recvMessage))
                System.err.println("WARNING: ARRAYS DO NOT MATCH") // validate every 100 messages
              Log.debug(this + ": received resp to message " + recvI + " in run " + runId)
              roundTripTimes += m.timeElasped
              msgsRecv += 1
              sendNextMsg()
            case m @ NodeMessage(a0, a1, a2, ta @ ToActor(LocalHostName, ThisSymbol), a4, a5, false) => // always do the echo-ing
              val respMsg = NodeMessage(a0, a1, a2, ta, a4, a5, true)
              //Debug.info(this + ": echoing message: " + m + " with resp " + respMsg + " to sender: " + sender)
              sender ! respMsg 
            case m @ NodeMessage(_, _, _, _, _, _, true) if (started) =>
              // we've received a message which was echoed back to us, but we
              // did not send it out this round
              // most likely comes from previous rounds
              Log.debug(this + ": expecting: recvHostName: " + LocalHostName + ", runId: " + runId + ", recvId: " + id + ", recvI: " + msgsRecv)
              Log.debug("BUG: " + m)
            case STOP if (started) =>
              //println("actor " + id + " received STOP")
              // time is up
              val totalTime = timer.end()
              val stats = new DescriptiveStatistics
              roundTripTimes.foreach(t => stats.addValue(nanoToMilliseconds(t)))
              val avgRTL = //roundTripTimes.map(nanoToMilliseconds(_)).foldLeft(0.0)(_+_) / i 
                  stats.getMean
              val timeInSec = nanoToSeconds(totalTime)
              val xml = 
                <execution>
                  <runId>{runId}</runId>
                  <latency unit="milliseconds">
                    <mean>{avgRTL}</mean>
                    <median>{stats.getPercentile(50.0)}</median>
                    <variance>{stats.getVariance}</variance>
                    <min>{stats.getMin}</min>
                    <max>{stats.getMax}</max>
                    <percentile value="0.1">{stats.getPercentile(0.1)}</percentile>
                    <percentile value="1.0">{stats.getPercentile(1.0)}</percentile>
                    <percentile value="5.0">{stats.getPercentile(5.0)}</percentile>
                    <percentile value="95.0">{stats.getPercentile(95.0)}</percentile>
                    <percentile value="99.0">{stats.getPercentile(99.0)}</percentile>
                    <percentile value="99.9">{stats.getPercentile(99.9)}</percentile>
                  </latency>
                  <totaltime unit="milliseconds">{nanoToMilliseconds(totalTime)}</totaltime>
                  <totalmessages>{stats.getN}</totalmessages>
                  <throughput>
                    <result unit="bytespersecond">{(actualMsgSize * stats.getN)/timeInSec}</result>
                    <result unit="messagespersecond">{stats.getN / timeInSec}</result>
                  </throughput>
                  <timeouts>
                    {timeoutNodes.map(t => <node>{t.machine}</node>)}
                  </timeouts>
                </execution>
              writer.println(xml.toString)
              writer.flush()
              lastMsg = null
              started = false
              sender ! Results(runId, msgsSent, msgsRecv, numTimeouts)               
            case e => 
              Log.debug(this + ": RECEIVED UNKNOWN MESSAGE: " + e + " from proxy: " + sender)
          }
        }
      }
    }

    def execute(numRuns: Int) {
      println("numActors = " + numActors)
      val writers = (0 until numActors).map(id => {
        val writer = new PrintWriter(new FileOutputStream(new File(ExpDir, List("actor", id).mkString("_") + ".xml")))
        writer.println("<actor>")
        val xml = 
          <metadata>
            <expid>{ExpName}</expid>
            <numactors>{numActors}</numactors>
            <actorid>{id}</actorid>
            <runtime>{runTime}</runtime>
            <numruns>{numRuns}</numruns>
            <numnodes>{nodes.length}</numnodes>
            <messagepayloadsize units="bytes">{messageSize}</messagepayloadsize>
            <messagesize units="bytes">{actualMsgSize}</messagesize>
          </metadata>
        writer.println(xml.toString) 
        writer
      }).toArray
      val resultWriter = new PrintWriter(new FileOutputStream(new File(ExpDir, "experiment_results.xml")))
      resultWriter.println("<experiment>")
      val xml = 
        <metadata>
          <expid>{ExpName}</expid>
          <numactors>{numActors}</numactors>
          <runtime>{runTime}</runtime>
          <numruns>{numRuns}</numruns>
          <numnodes>{nodes.length}</numnodes>
          <messagepayloadsize units="bytes">{messageSize}</messagepayloadsize>
          <messagesize units="bytes">{actualMsgSize}</messagesize>
        </metadata>
      resultWriter.println(xml.toString)

      val nodeWriter = new PrintWriter(new FileOutputStream(new File(ExpDir, "nodes.xml")))
      val nodeXml =
        <nodedata>
          <numnodes>{nodes.length}</numnodes>
          <nodes>{nodes.map(node => <node>{node.address + ":" + node.port}</node>)}</nodes>
        </nodedata>
      nodeWriter.println(nodeXml.toString)
      nodeWriter.flush(); nodeWriter.close()

      val actors = (0 until numActors).map(id => {
          val writer = writers(id)
          val actor = new RunActor(id, writer)
          actor.start()
          actor
        }).toList

      // do the delay, before starting, to wait for all nodes to come up
      Thread.sleep(delayTime)

      (1 to numRuns).foreach(runId => {
        println("RUNID: " + runId)
        doRun(runId, actors, writers, resultWriter)
      })

      writers.foreach(w => { w.println("</actor>"); w.flush() })
      resultWriter.println("</experiment>"); resultWriter.flush()

      writers.foreach(_.close())
      resultWriter.close()
    }

    def doRun(runId: Int, actors: List[RunActor], writers: Array[PrintWriter], resultWriter: PrintWriter) {

      val jtimer = new JTimer

      var successes = 0
      var numMessagesSent = 0
      var numMessagesRecv = 0
      var numTimeouts = 0



      var numInactiveActors = 0 // actors which recv 0 messages
      val latch = new CountDownLatch(numActors)

      val timer = new Timer
      timer.start()

      actor {

        actors.foreach(_ ! START)
        
        val thisActor = Actor.self
        // start timer task
        jtimer.schedule(new TimerTask {
            override def run() = { thisActor ! COLLECT }
          }, runTime * 60 * 1000) // runTime in min, schedule wants ms
        loop {
          react {
            case COLLECT => actors.foreach(_ ! STOP)
            case Results(thisRunId, msgsSent, msgsRecv, thisNumTimeouts) if (runId == thisRunId) =>
              successes += 1
              
              numMessagesSent += msgsSent
              numMessagesRecv += msgsRecv
              numTimeouts += thisNumTimeouts 

              if (numMessagesRecv == 0) numInactiveActors += 1

              latch.countDown()

          }
        }
      }

      println("Awaiting on latch for " + (runTime * 60 * 2) + " seconds")
      latch.await(runTime * 60 * 2, TimeUnit.SECONDS) // wait twice as long for the actors to actually shut down
      println("Woke up from latch")

      println("Number of inactive actors: " + numInactiveActors)
      println("Num timeouts: " + numTimeouts)
        
      val elaspedTime = timer.end()
      val elaspedTimeInSeconds = nanoToSeconds(elaspedTime)

      val totalMsgsSent = numMessagesSent
      val totalMsgsRecv = numMessagesRecv
      assert( totalMsgsSent >= totalMsgsRecv )
      val successPercentage = totalMsgsRecv.toDouble / totalMsgsSent.toDouble
      val totalBytesSent = totalMsgsSent * actualMsgSize
      val totalBytesAcked = totalMsgsRecv * actualMsgSize
      val bytesSentPerSecond = totalBytesSent / elaspedTimeInSeconds
      val bytesAckedPerSecond = totalBytesAcked / elaspedTimeInSeconds
      val msgsSentPerSecond = totalMsgsSent / elaspedTimeInSeconds
      val msgsAckedPerSecond = totalMsgsRecv / elaspedTimeInSeconds

      val innerXml =             
        <results>
          <totaltime unit="seconds">{nanoToSeconds(elaspedTime)}</totaltime>
          <numInactiveActors>{numInactiveActors}</numInactiveActors>
          <numMessagesSent>{totalMsgsSent}</numMessagesSent>
          <numMessagesRecv>{totalMsgsRecv}</numMessagesRecv>
          <successPercentage>{successPercentage}</successPercentage>
          <totalBytesSent>{totalBytesSent}</totalBytesSent>
          <totalBytesAcked>{totalBytesAcked}</totalBytesAcked>
          <bytesSentPerSecond>{bytesSentPerSecond}</bytesSentPerSecond>
          <bytesAckedPerSecond>{bytesAckedPerSecond}</bytesAckedPerSecond>
          <msgsSentPerSecond>{msgsSentPerSecond}</msgsSentPerSecond>
          <msgsAckedPerSecond>{msgsAckedPerSecond}</msgsAckedPerSecond>
        </results>

      val xmlEnd = 
        <messagesizeexperiment>
          <payloadsize unit="bytes">{messageSize}</payloadsize>
          <actualsize unit="bytes">{actualMsgSize}</actualsize>
          <runId>{runId}</runId>
          {innerXml}
        </messagesizeexperiment>
      resultWriter.println(xmlEnd.toString)
      resultWriter.flush()


    }


  }


}

