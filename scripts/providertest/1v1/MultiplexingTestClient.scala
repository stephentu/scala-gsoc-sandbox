import java.net._
import java.io._
import java.util.concurrent.CountDownLatch

import scala.concurrent.SyncVar
import scala.actors.remote._

class RunWorker(conn: ByteConnection, workerId: Int, numMsgs: Int) extends Thread("RunWorker-" + workerId) {
  val syncvar = new SyncVar[Boolean]
  override def run() {
    (1 to numMsgs).foreach(i => { conn.send(msg) })
    syncvar.set(true)
  }
  val msg = new Array[Byte](1024)
  override def toString = "<RunWorker: workerId - " + workerId + ">"
}

object MultiplexingTestClient {
  def main(args: Array[String]) {
    def containsOpt(opt: String) = args.contains(opt)
    def parseOpt(opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size).toInt
    val numMsgs = parseOpt("--nummsgs=") 
    val numWorkers= parseOpt("--numworkers=") 
    val numRuns = parseOpt("--numruns=") 
    val useNio = containsOpt("--nio")

    val receive = (conn: Connection, msg: Array[Byte]) => { latch.countDown() }

    val provider = 
      if (useNio) {  
        println("Using NIO")
        new NonBlockingServiceProvider 
      } else {
        println("Using Blocking IO")
        new BlockingServiceProvider
      } 
    val conn = provider.connect(Node("127.0.0.1", 9000), receive)

    (1 to numRuns).foreach(runId => {
      latch = new CountDownLatch(numWorkers * numMsgs)
      val thisRun = new MPlexRun(runId, conn, numWorkers, numMsgs)
      val time = thisRun.doRun()
      println("Run " + runId + " results: " + time + " ms")
    })
  }
  var latch: CountDownLatch = _
  class MPlexRun(runId: Int, conn: ByteConnection, numWorkers: Int, numMsgs: Int) {
    def doRun(): Long = {
      val startTime = System.currentTimeMillis
      val workers = (1 to numWorkers).map(workerId => new RunWorker(conn, workerId, numMsgs))
      workers.foreach(_.start())
      workers.foreach(_.syncvar.get)
      latch.await()
      val endTime = System.currentTimeMillis
      endTime - startTime
    }
  }
}
