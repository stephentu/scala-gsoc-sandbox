import java.net._
import java.io._

import scala.concurrent.SyncVar

class Run(runId: Int, conns: Seq[(InputStream, OutputStream)], numMsgsPerConn: Int) {
  class RunWorker(workerId: Int, inp: InputStream, out: OutputStream) extends Thread("RunWorker-" + workerId) {
    val dataout = new DataOutputStream(out)
    val datain  = new DataInputStream(inp)
    val syncvar = new SyncVar[Boolean]
    override def run() {
      (1 to numMsgsPerConn).foreach(i => { 
        // write message
        val msg = mkMessage(i)
        dataout.writeInt(msg.size)
        dataout.write(msg) 
        dataout.flush()

        // read message
        val size = datain.readInt
        val buf = new Array[Byte](size)
        datain.readFully(buf)
        
        val _msg = new String(msg)
        val _msg0 = new String(buf)
        assert( _msg == _msg0 )
      })
      syncvar.set(true)
      //println(this + ": DONE")
    }
    def mkMessage(msgId: Int): Array[Byte] = {
      ("<RunId: " + runId + ", workerId: " + workerId + ", msgId: " + msgId + ">").getBytes
    }
    override def toString = "<RunWorker: runId - " + runId + " workerId - " + workerId + ">"
  }
  def doRun(): Long = {
    val startTime = System.currentTimeMillis
    val workers = conns.zipWithIndex.map { t => 
      val (inp, out) = t._1
      val idx = t._2
      new RunWorker(idx, inp, out) 
    }
    workers.foreach(_.start())
    workers.foreach(_.syncvar.get)
    val endTime = System.currentTimeMillis
    endTime - startTime
  }
}

object SimpleTestClient {
  def main(args: Array[String]) {
    def containsOpt(opt: String) = args.contains(opt)
    def parseOpt(opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size).toInt
    val numConnections = parseOpt("--numconns=") 
    val numMsgsPerConn = parseOpt("--nummsgsperconn=") 
    val numRuns = parseOpt("--numruns=") 

    val sockets = (1 to numConnections).map(i => new Socket("127.0.0.1", 9000))
    val conns = sockets.map(sock => (sock.getInputStream, sock.getOutputStream))

    (1 to numRuns).foreach(runId => {
      val thisRun = new Run(runId, conns, numMsgsPerConn)
      val time = thisRun.doRun()
      println("Run " + runId + " results: " + time + " ms")
    })
  }
}
