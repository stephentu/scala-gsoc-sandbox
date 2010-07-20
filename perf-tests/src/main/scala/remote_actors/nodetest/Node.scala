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

object Node {

  val LocalHostName = InetAddress.getLocalHost.getCanonicalHostName
  val DefaultNodes = (10 to 15).map(i => "r" + i).toList
  def main(args: Array[String]) {
    val level = parseOptIntDefault(args, "--debug=", 1) 
    Debug.level = level
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    val numActors = parseOptIntDefault(args,"--numactors=", 1000)
    val port = parseOptIntDefault(args,"--listenport=", 16873)
    val runTime = parseOptIntDefault(args,"--runtime=", 1) // 10 minutes per run instance
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val nodes = parseOptListDefault(args, "--nodes=", DefaultNodes)

    val expname = parseOptStringDefault(args, "--expname=", "results_nodetest")

    val ExpId = System.currentTimeMillis
    val ExpDir = new File(expname + "_" + ExpId)

    println("---------------------------------------------------------------------")
    println("Localhost name: " + LocalHostName)
    println("Listen port is " + port)
    println("Mode (connect and listen) " + mode)
    println("NumActors= " + numActors)
    println("RunTime = " + runTime)
    println("NumRuns = " + numRuns)
    println("Nodes to RR = " + nodes)
    println("---------------------------------------------------------------------")
    println()

    val announcer = actor {
      alive(port, mode)
      register('announcer, self)
      loop { 
        react { 
          case StopAnnouncer =>
            println("Announcer going down")
            exit()
          case e => sender ! e 
        } 
      }
    }

    println("Blocking until all nodes become available")
    nodes.foreach(hostname => {
      var continue = true
      while (continue) {
        try {
          select(scala.actors.remote.Node(hostname, port), 'announcer) // make blocking conn here
          continue = false
        } catch {
          case e: ConnectException =>
            println("Waiting for node: " + hostname)
            Thread.sleep(1000) // 1 second
        }
      }
    })
    
    ExpDir.mkdirs()

    val run = new Run(ExpDir, port, mode, nodes.map(h => scala.actors.remote.Node(h, port).canonicalForm).toArray, numActors, runTime)
    run.execute(numRuns)


  }

  case object STOP
  case object START
  case object COLLECT

  class Run(ExpDir: File, port: Int, mode: ServiceMode.Value, nodes: Array[Node], numActors: Int, runTime: Int) {
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

    class RunActor(id: Int, writer: PrintWriter) extends Actor {
      override def exceptionHandler: PartialFunction[Exception, Unit] = {
        case e: Exception => e.printStackTrace()
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

      override def toString = "<RunActor " + ThisSymbol + ">"

      private def sendNextMsg() {
        val nextN = nextNode()
        val nextSym = nextSymbol()
        val server = select(nextN, nextSym, serviceMode = mode) // use java serialization
        val msg = NodeMessage(message, 
                              System.nanoTime, 
                              FromActor(LocalHostName, ThisSymbol),
                              ToActor(nextN.address, nextSym), 
                              runId, 
                              msgsSent, 
                              false)
        Debug.info(this + ": Sending message: " + msg + " to actor: " + nextSym + " with proxy: " + server)
        server ! msg  
        msgsSent += 1
      }

      var timer: Timer = _
      var roundTripTimes: ArrayBuffer[Long] = _
      var started = false

      override def act() {
        alive(port, serviceMode = mode)
        register(ThisSymbol, self)
        //println("actor " + id + " alive and registered on port " + port)
        loop {
          reactWithin(5000) {
            case START if (!started) =>
              timer = new Timer
              roundTripTimes = new ArrayBuffer[Long](1024) // stored in NS
              runId += 1
              msgsSent = 0
              msgsRecv = 0
              numTimeouts = 0
              timer.start()
              started = true
              sendNextMsg()
            case TIMEOUT if (started) =>
              numTimeouts += 1
              sendNextMsg()
            case m @ NodeMessage(recvMessage, 
                                 _, 
                                 FromActor(LocalHostName, ThisSymbol),
                                 _, 
                                 recvRunId, 
                                 recvI, 
                                 true) if (recvRunId == runId && recvI >= msgsRecv && started) =>
              if ((msgsRecv % 100) == 0 && !java.util.Arrays.equals(message, recvMessage))
                System.err.println("WARNING: ARRAYS DO NOT MATCH") // validate every 100 messages
              Debug.info(this + ": received resp to message " + recvI)
              roundTripTimes += m.timeElasped
              msgsRecv += 1
              sendNextMsg()
            case m @ NodeMessage(a0, a1, a2, ta @ ToActor(LocalHostName, ThisSymbol), a4, a5, false) => // always do the echo-ing
              val respMsg = NodeMessage(a0, a1, a2, ta, a4, a5, true)
              Debug.info(this + ": echoing message: " + m + " with resp " + respMsg + " to sender: " + sender)
              sender ! respMsg 
            case m @ NodeMessage(_, _, _, _, _, _, true) if (started) =>
              // we've received a message which was echoed back to us, but we
              // did not send it out this round
              // most likely comes from previous rounds
              //System.err.println(this + ": expecting: recvHostName: " + LocalHostName + ", runId: " + runId + ", recvId: " + id + ", recvI: " + msgsRecv)
              //System.err.println("BUG: " + m)
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
                </execution>
              writer.println(xml.toString)
              writer.flush()
              started = false
              sender ! Results(runId, msgsSent, msgsRecv, numTimeouts)               
            case e => 
              //System.err.println(this + ": RECEIVED UNKNOWN MESSAGE: " + e + " from proxy: " + sender)
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
            <nodes>{nodes.map(node => <node>{node.address}</node>)}</nodes>
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
          <nodes>{nodes.map(node => <node>{node.address}</node>)}</nodes>
          <messagepayloadsize units="bytes">{messageSize}</messagepayloadsize>
          <messagesize units="bytes">{actualMsgSize}</messagesize>
        </metadata>
      resultWriter.println(xml.toString)

      val actors = (0 until numActors).map(id => {
          val writer = writers(id)
          val actor = new RunActor(id, writer)
          actor.start()
          actor
        }).toList

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

