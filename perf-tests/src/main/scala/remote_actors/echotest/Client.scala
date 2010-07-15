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

case class Message(bytes: Array[Byte], timeCreated: Long) {
  def timeElasped = System.nanoTime - timeCreated
}

object Client {
  def main(args: Array[String]) {
    val host = parseOptStringDefault(args,"--servername=", "r10")
    val port = parseOptIntDefault(args,"--serverport=", 9000)
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    val numActors = parseOptIntDefault(args,"--numactors=", 10000)
    val numMsgsPerActor = parseOptIntDefault(args,"--nummsgsperactor=", 1000)
    val numRuns = parseOptIntDefault(args,"--numruns=", 5)
    val timeout = parseOptIntDefault(args,"--runtimeout=", 10) // 10 minutes

    println("---------------------------------------------------------------------")
    println("Connecting to host " + host + " port " + port + " using mode " + mode)
    println("NumActors = " + numActors)
    println("NumMsgsPerActor = " + numMsgsPerActor)
    println("NumRuns = " + numRuns)
    println("---------------------------------------------------------------------")
    println()

    (1 to numRuns).foreach(runNum => {
      println("---------------------------------------------------------------------")
      println("Starting run " + runNum)
      val run = new Run(host, port, mode, numActors, numMsgsPerActor, timeout)
      run.execute() // blocks until run finished
      println("Run " + runNum + " has terminated")
      println("---------------------------------------------------------------------")
      println()
    })

  }
}

case object STOP

class Run(host: String, port: Int, mode: ServiceMode.Value, numActors: Int, numMsgsPerActor: Int, timeout: Int) {
  class RunActor(id: Int, messageSize: Int, finishCallback: () => Unit, errorCallback: Exception => Unit) extends Actor {
    override def exceptionHandler: PartialFunction[Exception, Unit] = {
      case e: Exception => errorCallback(e)
    }
    override def act() {
      val server = select(Node(host, port), 'server, serviceMode = mode) // use java serialization
      var i = 0
      val message = newMessage(messageSize)
      val roundTripTimes = new Array[Long](numMsgsPerActor)
      val timer = new Timer
      timer.start()
      loopWhile(i <= numMsgsPerActor) {
        if (i < numMsgsPerActor) {
          server ! Message(message, System.nanoTime)
          react {
            case m: Message => 
              roundTripTimes(i) = m.timeElasped
              i += 1
            case STOP =>
              println("I TIMED OUT")
              exit()
          }
        } else {
          val totalTime = timer.end()

          printingActor ! { () => {
              // terminated. 
              println("Run " + id + " successfully terminated")
              println("Message payload size: " + messageSize + " (bytes)")
              println("Actual message size: " + javaSerializationMessageSize(Message(message, System.nanoTime)) + " (bytes)")
              val avgRTL = roundTripTimes.foldLeft(0L)(_+_) / numMsgsPerActor 
              println("Total time: " + nanoToSeconds(totalTime) + " (seconds)")
              println("Average round trip latency: " + nanoToMilliseconds(avgRTL) + " (ms)")
              println()
              // TODO: variance
            }
          }

          finishCallback()
          exit()
        }
      }
    }
  }

  val printingActor = actor {
    loop {
      react {
        case e: Function0[Unit] => e()
      }
    }
  }

  val MessageSizes = Array(0, 16, 512, 1024)

  def execute() {
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

      val actors = (1 to numActors).map(id => new RunActor(id, msgSize, success, error))
      actors.foreach(_.start())
      latch.await(timeout * 60, TimeUnit.SECONDS) // timeout in minutes
      // send STOP to all the actors, to reap the timeouts
      actors.foreach(_ ! STOP)

      printingActor ! { () => {
        println("Num successes: " + successes.get)
        println("Num failures: " + failures.get)
        }
      }
      
    })
  }
}
