package remote_actors
package echotest

import remote_actors.perftest._
import TestUtils._

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import java.util.concurrent._
import java.util.concurrent.atomic._

import java.io._
import java.util.{Timer => JTimer, TimerTask}

import scala.collection.mutable.ArrayBuffer

import org.apache.commons.math.stat.descriptive.DescriptiveStatistics


object Client {
  val ExpId = System.currentTimeMillis
  val ExpParent = new File("/scratch/sltu")
  val ExpDir = new File(ExpParent, "results_echotest_" + ExpId)
  val MessageSizes = Array(0, 64, 128, 512, 1024, 2048, 4096, 8192)
  val Messages = MessageSizes.map(size => newMessage(size))
  val ActualMsgSizes = Messages.map(message => javaSerializationMessageSize(Message(message, System.nanoTime)))
  def main(args: Array[String]) {
    val host = parseOptStringDefault(args,"--servername=", "r10")
    val port = parseOptIntDefault(args,"--serverport=", 9000)
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    val numActorsList = parseOptListDefault(args,"--numactors=", List("1", "10", "100", "1000", "10000")).map(_.toInt)
    val runTime = parseOptIntDefault(args,"--runtime=", 5) // 5 minutes per run instance
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)

    ExpDir.mkdirs()
    
    val README_FILE = new File(Client.ExpDir, "README")
    README_FILE.createNewFile()
    val README = new PrintStream(new FileOutputStream(README_FILE))

    List(README, System.out).foreach(out => {
        out.println("---------------------------------------------------------------------")
        out.println("Connecting to host " + host + " port " + port + " using mode " + mode)
        out.println("NumActorsList = " + numActorsList)
        out.println("RunTime = " + runTime)
        out.println("NumRuns = " + numRuns)
        out.println("MessageSizes = " + MessageSizes)
        out.println("---------------------------------------------------------------------")
        out.println()
    })

    README.flush(); README.close()



    (1 to numRuns).foreach(runNum => {
      println("---------------------------------------------------------------------")
      println("Starting run set " + runNum)
      println("---------------------------------------------------------------------")
      numActorsList.foreach(numActors => {
        println("Starting run with " + numActors + " actors")
        val run = new Run(runNum, host, port, mode, numActors, runTime)
        run.execute() // blocks until run finished
        println("Run " + runNum + " with " + numActors + " actors has terminated")
        println("---------------------------------------------------------------------")
        println()
      })
    })

  }
}

case object STOP

class Run(runId: Int, host: String, port: Int, mode: ServiceMode.Value, numActors: Int, runTime: Int) {

  implicit object cfg extends Configuration with HasJavaSerializer {
    override val aliveMode  = mode
    override val selectMode = mode
  }

  class RunActor(id: Int, writer: PrintWriter, messageSize: Int, actualMsgSize: Long, message: Array[Byte], messageCallback: () => Unit, finishCallback: () => Unit, errorCallback: Exception => Unit) extends Actor {
    override def exceptionHandler: PartialFunction[Exception, Unit] = {
      case e: Exception => errorCallback(e)
    }
    override def act() {
      val server = select(Node(host, port), 'server) // use java serialization
      var i = 0
      val roundTripTimes = new ArrayBuffer[Long](1024) // stored in NS
      val timer = new Timer
      timer.start()
      loop {
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
    val writers = (1 to numActors).map(id => {
      val writer = new PrintWriter(new FileOutputStream(new File(Client.ExpDir, List("run", runId, "numactors", numActors, "actor", id).mkString("_") + ".xml")))
      writer.println("<actor>")
      val xml = 
        <metadata>
          <expid>{Client.ExpId}</expid>
          <runid>{runId}</runid>
          <numactors>{numActors}</numactors>
          <actorid>{id}</actorid>
          <runtime>{runTime}</runtime>
        </metadata>
      writer.println(xml.toString) 
      writer
    }).toArray
    val resultWriter = new PrintWriter(new FileOutputStream(new File(Client.ExpDir, List("run", runId, "numactors", numActors).mkString("_") + ".xml")))
    resultWriter.println("<experiment>")
    val xml = 
      <metadata>
        <expid>{Client.ExpId}</expid>
        <runid>{runId}</runid>
        <numactors>{numActors}</numactors>
        <runtime>{runTime}</runtime>
      </metadata>
    resultWriter.println(xml.toString)
    val jtimer = new JTimer
    (0 until Client.MessageSizes.length).foreach(idx => {
      val msgSize = Client.MessageSizes(idx)
      val message = Client.Messages(idx)
      val actualMsgSize = Client.ActualMsgSizes(idx)

      println("Testing message (payload) size: " + msgSize + " bytes")
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
      val actors = (1 to numActors).map(id => {
        val writer = writers(id - 1)
        val actor = new RunActor(id, writer, msgSize, actualMsgSize, message, msgCallback, success, error)
        writer.println("<messagesizeexperiment>")
        val xmls = (<payloadsize unit="bytes">{msgSize}</payloadsize><actualsize unit="bytes">{actualMsgSize}</actualsize>)
        xmls.foreach { writer.println(_) }
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
        

      writers.foreach(_.println("</messagesizeexperiment>"))

      val elaspedTime = timer.end()
      val elaspedTimeInSeconds = nanoToSeconds(elaspedTime)

      val totalMsgs = numMessages.get
      val totalBytesTransmitted = totalMsgs * actualMsgSize
      val bytesPerSecond = totalBytesTransmitted / elaspedTimeInSeconds
      val msgsPerSecond = totalMsgs / elaspedTimeInSeconds

      
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
              <nummessages>{totalMsgs}</nummessages>
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
      resultWriter.flush()

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
