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

object Node {
  val ExpId = System.currentTimeMillis
  val ExpDir = new File("results_" + ExpId)
  val LocalHostName = InetAddress.getLocalHost.getHostName
  val DefaultNodes = (10 to 15).map(i => "r" + i).toList
  def main(args: Array[String]) {
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    val numActors = parseOptIntDefault(args,"--numactors=", 1000)
    val port = parseOptIntDefault(args,"--listenport=", 16873)
    val runTime = parseOptIntDefault(args,"--runtime=", 1) // 10 minutes per run instance
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val nodes = parseOptListDefault(args, "--nodes=", DefaultNodes)

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
      alive(port)
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
          select(scala.actors.remote.Node(hostname, port), 'announcer)
          continue = false
        } catch {
          case e: ConnectException =>
            println("Waiting for node: " + hostname)
            Thread.sleep(1000) // 1 second
        }
      }
    })
    
    ExpDir.mkdirs()

    (1 to numRuns).foreach(runNum => {
      println("---------------------------------------------------------------------")
      println("Starting run " + runNum)
      println("---------------------------------------------------------------------")
      val run = new Run(runNum, port, mode, nodes.map(h => scala.actors.remote.Node(h, port)).toArray, numActors, runTime)
      run.execute()
      println("Run " + runNum + " has terminated")
      println("---------------------------------------------------------------------")
    })


  }

  case object STOP

  class Run(runId: Int, port: Int, mode: ServiceMode.Value, nodes: Array[Node], numActors: Int, runTime: Int) {

    val messageSize = 4096
    val message = newMessage(messageSize)
    val actualMsgSize = javaSerializationMessageSize(Message(message, System.nanoTime))

    class RunActor(id: Int, writer: PrintWriter, messageCallback: () => Unit, finishCallback: () => Unit, errorCallback: Exception => Unit) extends Actor {
      override def exceptionHandler: PartialFunction[Exception, Unit] = {
        case e: Exception => errorCallback(e)
      }
      val random = new scala.util.Random
      def nextNode() = {
        val idx = random.nextInt(nodes.length)
        nodes(idx)
      }
      def nextSymbol() = {
        Symbol("actor" + random.nextInt(numActors))
      }
      override def act() {
        alive(port, serviceMode = mode)
        register(Symbol("actor" + id), self)
        var i = 0
        val roundTripTimes = new ArrayBuffer[Long](1024) // stored in NS
        val timer = new Timer
        timer.start()
        loop {
          val server = select(nextNode(), nextSymbol(), serviceMode = mode) // use java serialization
          server ! Message(message, System.nanoTime)
          react {
            case m @ Message(retVal, _) => 
              roundTripTimes += m.timeElasped
              if ((i % 100) == 0 && !java.util.Arrays.equals(message, retVal))
                System.err.println("WARNING: ARRAYS DO NOT MATCH") // validate every 100 messages
              i += 1
              messageCallback()
            case STOP =>
              // time is up
              val totalTime = timer.end()
              val stats = new DescriptiveStatistics
              roundTripTimes.foreach(t => stats.addValue(nanoToMilliseconds(t)))
              val avgRTL = //roundTripTimes.map(nanoToMilliseconds(_)).foldLeft(0.0)(_+_) / i 
                  stats.getMean
              val timeInSec = nanoToSeconds(totalTime)
              val xml = 
                <execution>
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
              finishCallback()
              exit()
          }
        }
      }
    }


    def execute() {
      val writers = (0 until numActors).map(id => {
        val writer = new PrintWriter(new FileOutputStream(new File("results_" + ExpId, List("run", runId, "numactors", numActors, "actor", id).mkString("_") + ".xml")))
        writer.println("<actor>")
        val xml = 
          <metadata>
            <expid>{ExpId}</expid>
            <runid>{runId}</runid>
            <numactors>{numActors}</numactors>
            <actorid>{id}</actorid>
            <runtime>{runTime}</runtime>
            <nodes>{nodes.map(node => <node>{node.address}</node>)}</nodes>
            <messagepayloadsize units="bytes">{messageSize}</messagepayloadsize>
            <messagesize units="bytes">{actualMsgSize}</messagesize>
          </metadata>
        writer.println(xml.toString) 
        writer
      }).toArray
      val resultWriter = new PrintWriter(new FileOutputStream(new File("results_" + ExpId, List("run", runId, "numactors", numActors).mkString("_") + ".xml")))
      resultWriter.println("<experiment>")
      val xml = 
        <metadata>
          <expid>{ExpId}</expid>
          <runid>{runId}</runid>
          <numactors>{numActors}</numactors>
          <runtime>{runTime}</runtime>
          <nodes>{nodes.map(node => <node>{node.address}</node>)}</nodes>
          <messagepayloadsize units="bytes">{messageSize}</messagepayloadsize>
          <messagesize units="bytes">{actualMsgSize}</messagesize>
        </metadata>
      resultWriter.println(xml.toString)
      val jtimer = new JTimer

      val successes = new AtomicInteger
      val failures = new AtomicInteger
      val numMessages = new AtomicInteger
      val latch = new CountDownLatch(numActors)

      val success = () => {
        successes.getAndIncrement()
        latch.countDown()
      }

      val error = (e: Exception) => {
        failures.getAndIncrement()
        latch.countDown()
      }

      val msgCallback = () => { numMessages.getAndIncrement(); () }

      val timer = new Timer
      timer.start()
      val actors = (0 until numActors).map(id => {
        val writer = writers(id)
        val actor = new RunActor(id, writer, msgCallback, success, error)
        actor
      })

      actors.foreach(_.start())
      // start timer task
      jtimer.schedule(new TimerTask {
          override def run() = {
            // send STOP to all the actors; the time is up
            actors.foreach(_ ! STOP)
          }
        }, runTime * 60 * 1000) // runTime in min, schedule wants ms

      latch.await(runTime * 60 * 2, TimeUnit.SECONDS) // wait twice as long for the actors to actually shut down
        
      val elaspedTime = timer.end()
      val elaspedTimeInSeconds = nanoToSeconds(elaspedTime)

      val totalMsgs = numMessages.get
      val totalBytesTransmitted = totalMsgs * actualMsgSize
      val bytesPerSecond = totalBytesTransmitted / elaspedTimeInSeconds
      val msgsPerSecond = totalMsgs / elaspedTimeInSeconds

      val innerXml = if (successes.get != numActors) {
            <error/>
          } else {
            <success>
              <totaltime unit="seconds">{nanoToSeconds(elaspedTime)}</totaltime>
              <nummessages>{totalMsgs}</nummessages>
              <throughput unit="bytespersecond">{bytesPerSecond}</throughput>
              <throughput unit="messagespersecond">{msgsPerSecond}</throughput>
            </success>
          }

      val xmlEnd = 
        <messagesizeexperiment>
          <payloadsize unit="bytes">{messageSize}</payloadsize>
          <actualsize unit="bytes">{actualMsgSize}</actualsize>
          {innerXml}
        </messagesizeexperiment>
      resultWriter.println(xmlEnd.toString)
      resultWriter.flush()

      writers.foreach(w => { w.println("</actor>"); w.flush(); w.close() })
      resultWriter.println("</experiment>"); resultWriter.flush(); resultWriter.close()
    }


  }


}

