package remote_actors
package echotest

import remote_actors.perftest._
import TestUtils._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import java.util.concurrent._
import java.util.concurrent.atomic._

import java.io._

case class Message(bytes: Array[Byte], timeCreated: Long) {
  def timeElasped = System.nanoTime - timeCreated
}

object Client {
  val ExpId = System.currentTimeMillis
  val ExpDir = new File("results_" + ExpId)
  def main(args: Array[String]) {
    val host = parseOptStringDefault(args,"--servername=", "r10")
    val port = parseOptIntDefault(args,"--serverport=", 9000)
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    val numActorsList = parseOptListDefault(args,"--numactors=", List("1", "10", "100", "1000", "10000")).map(_.toInt)
    val numMsgsPerActor = parseOptIntDefault(args,"--nummsgsperactor=", 5000)
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val timeout = parseOptIntDefault(args,"--runtimeout=", 60) // 60 minutes

    println("---------------------------------------------------------------------")
    println("Connecting to host " + host + " port " + port + " using mode " + mode)
    println("NumActorsList = " + numActorsList)
    println("NumMsgsPerActor = " + numMsgsPerActor)
    println("NumRuns = " + numRuns)
    println("---------------------------------------------------------------------")
    println()

    ExpDir.mkdirs()

    (1 to numRuns).foreach(runNum => {
      println("---------------------------------------------------------------------")
      println("Starting run set " + runNum)
      println("---------------------------------------------------------------------")
      numActorsList.foreach(numActors => {
        println("Starting run with " + numActors + " actors")
        val run = new Run(runNum, host, port, mode, numActors, numMsgsPerActor, timeout)
        run.execute() // blocks until run finished
        println("Run " + runNum + " with " + numActors + " actors has terminated")
        println("---------------------------------------------------------------------")
        println()
      })
    })

  }
}

case object STOP

class Run(runId: Int, host: String, port: Int, mode: ServiceMode.Value, numActors: Int, numMsgsPerActor: Int, timeout: Int) {

  class RunActor(id: Int, writer: PrintWriter, messageSize: Int, finishCallback: () => Unit, errorCallback: Exception => Unit) extends Actor {
    override def exceptionHandler: PartialFunction[Exception, Unit] = {
      case e: Exception => errorCallback(e)
    }
    override def act() {
      val server = select(Node(host, port), 'server, serviceMode = mode) // use java serialization
      var i = 0
      val message = newMessage(messageSize)
      val roundTripTimes = new Array[Long](numMsgsPerActor) // in nanoseconds
      val timer = new Timer
      timer.start()
      loopWhile(i <= numMsgsPerActor) {
        if (i < numMsgsPerActor) {
          server ! Message(message, System.nanoTime)
          react {
            case m @ Message(retVal, _) => 
              roundTripTimes(i) = m.timeElasped
              if ((i % 100) == 0 && !java.util.Arrays.equals(message, retVal))
                System.err.println("WARNING: ARRAYS DO NOT MATCH") // validate every 100 messages
              i += 1
            case STOP =>
              println("I TIMED OUT")
              exit()
          }
        } else {
          val totalTime = timer.end()
          val avgRTL = roundTripTimes.map(nanoToMilliseconds(_)).foldLeft(0.0)(_+_) / numMsgsPerActor 
          val actualMsgSize = javaSerializationMessageSize(Message(message, System.nanoTime))

          //printingActor ! { () => {
          //    // terminated. 
          //    println("Run set " + runId + " with " + numActors + " actors for actor "  + id + " successfully terminated")
          //    println("Message payload size: " + messageSize + " (bytes)")
          //    println("Actual message size: " + actualMsgSize + " (bytes)")
          //    println("Total time: " + nanoToSeconds(totalTime) + " (seconds)")
          //    println("Average round trip latency: " + avgRTL + " (ms)")
          //    println()
          //    // TODO: variance
          //  }
          //}

          //writer.println("# Results for run set " + runId + " with " + numActors + " for actor " + id)
          //writer.println("# Latencies for the i-th message (ms)")
          //writer.println(roundTripTimes.map(nanoToMilliseconds(_)).mkString(","))
          //writer.println("# Message Payload Size (bytes) | Actual Message Size (bytes) | Total Time (sec) | PerActorThroughput (Bytes/sec) | PerActorThroughput (Msgs/sec)")
          val timeInSec = nanoToSeconds(totalTime)
          //writer.println(List(messageSize, actualMsgSize, timeInSec, (actualMsgSize * numMsgsPerActor)/timeInSec, numMsgsPerActor / timeInSec).mkString(",")) 
          //writer.println()

          val xml = 
            <execution>
              <messages>
                {roundTripTimes.map(ns => <message><latency unit="milliseconds">{nanoToMilliseconds(ns)}</latency></message>)}
              </messages>

              <totaltime unit="milliseconds">{nanoToMilliseconds(totalTime)}</totaltime>
              <throughput>
                <result unit="bytespersecond">{(actualMsgSize * numMsgsPerActor)/timeInSec}</result>
                <result unit="messagespersecond">{numMsgsPerActor / timeInSec}</result>
              </throughput>
            </execution>
          writer.println(xml.toString)

          finishCallback()
          exit()
        }
      }
    }
  }

  val printingActor = actor {
    loop {
      react {
        case e: Function0[_] => e()
      }
    }
  }

  val MessageSizes = Array(0, 64, 128, 512, 1024, 2048, 4096, 8192, 65536)

  def execute() {
    val writers = (1 to numActors).map(id => {
      val writer = new PrintWriter(new FileOutputStream(new File("results_" + Client.ExpId, List("run", runId, "numactors", numActors, "actor", id).mkString("_") + ".xml")))
      writer.println("<actor>")
      val xml = 
        <metadata>
          <expid>{Client.ExpId}</expid>
          <runid>{runId}</runid>
          <numactors>{numActors}</numactors>
          <actorid>{id}</actorid>
        </metadata>
      writer.println(xml.toString) 
      writer
    }).toArray
    val resultWriter = new PrintWriter(new FileOutputStream(new File("results_" + Client.ExpId, List("run", runId, "numactors", numActors).mkString("_") + ".xml")))
    resultWriter.println("<experiment>")
    val xml = 
      <metadata>
        <runid>{runId}</runid>
        <numactors>{numActors}</numactors>
      </metadata>
    resultWriter.println(xml.toString)
    MessageSizes.foreach(msgSize => {
      println("Testing message (payload) size: " + msgSize + " bytes")
      val successes = new AtomicInteger
      val failures = new AtomicInteger
      val latch = new CountDownLatch(numActors)

      val success = () => {
        successes.getAndIncrement()
        latch.countDown()
      }

      val error = (e: Exception) => {
        failures.getAndIncrement()
        latch.countDown()
      }

      val message = newMessage(msgSize)
      val actualMsgSize = javaSerializationMessageSize(Message(message, System.nanoTime))

      val timer = new Timer
      timer.start()
      val actors = (1 to numActors).map(id => {
        val writer = writers(id - 1)
        val actor = new RunActor(id, writer, msgSize, success, error)
        writer.println("<messagesizeexperiment>")
        val xmls = (<payloadsize unit="bytes">{msgSize}</payloadsize><actualsize unit="bytes">{actualMsgSize}</actualsize>)
        xmls.foreach { writer.println(_) }
        actor
      })

      actors.foreach(_.start())
      latch.await(timeout * 60, TimeUnit.SECONDS) // timeout in minutes
      // send STOP to all the actors, to reap the timeouts
      actors.foreach(_ ! STOP)

      writers.foreach(_.println("</messagesizeexperiment>"))

      val elaspedTime = timer.end()
      val elaspedTimeInSeconds = nanoToSeconds(elaspedTime)


      val totalBytesTransmitted = numActors * numMsgsPerActor * actualMsgSize
      val bytesPerSecond = totalBytesTransmitted / elaspedTimeInSeconds
      val msgsPerSecond = (numActors * numMsgsPerActor) / elaspedTimeInSeconds

      
      //resultWriter.println("Run set " + runId + " with " + numActors + " actors with message size: " + msgSize)
      //resultWriter.println("Num successes: " + successes.get)
      //resultWriter.println("Num failures: " + failures.get)
      val innerXml = if (successes.get != numActors) {
            //resultWriter.println("There were errors. no results reported")
            <error/>
          } else {
            //resultWriter.println("Total time (sec): " + nanoToSeconds(elaspedTime))
            //resultWriter.println("Bytes/Sec: " + bytesPerSecond)
            //resultWriter.println("Msgs/Sec: " + msgsPerSecond)
            <success>
              <totaltime unit="seconds">{nanoToSeconds(elaspedTime)}</totaltime>
              <throughput unit="bytespersecond">{bytesPerSecond}</throughput>
              <throughput unit="messagespersecond">{msgsPerSecond}</throughput>
            </success>
          }

      val xml = 
        <messagesizeexperiment>
          <payloadsize unit="bytes">{msgSize}</payloadsize>
          <actualsize unit="bytes">{actualMsgSize}</actualsize>
          {innerXml}
        </messagesizeexperiment>
      resultWriter.println(xml.toString)

      //printingActor ! { () => {
      //    println("Run set " + runId + " with " + numActors + " actors with message size: " + msgSize)
      //    println("Num successes: " + successes.get)
      //    println("Num failures: " + failures.get)
      //    if (successes.get != numActors) {
      //      println("There were errors. no results reported")
      //    } else {
      //      println("Total time (sec): " + nanoToSeconds(elaspedTime))
      //      println("Bytes/Sec: " + bytesPerSecond)
      //      println("Msgs/Sec: " + msgsPerSecond)
      //    }
      //  }
      //}
      
    })
    writers.foreach(w => { w.println("</actor>"); w.flush(); w.close() })
    resultWriter.println("</experiment>"); resultWriter.flush(); resultWriter.close()
  }
}
