import scala.actors._
import scala.actors.remote._

object Server {

  var cnt = 0

  val monitor = new Object

  def newConnection(listener: Listener, conn: Connection) {
    Debug.info("New connection received: " + conn)
  }

  def receive(conn: Connection, bytes: Array[Byte]) {
    Debug.info("Received " + bytes.size + " bytes from " + conn)
    val message = new String(bytes) 
    Debug.info("message: " + message)
    cnt += 1
    conn.send(bytes)
    //if (cnt >= 100) monitor.synchronized { monitor.notifyAll }
  }
  
  def main(args: Array[String]) {
    def containsOpt(opt: String) = args.contains(opt)
    def parseOpt(opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size).toInt
    val useNio = containsOpt("--nio")
    val debugLevel = parseOpt("--debug=")
    Debug.level = debugLevel 
    val provider = 
      if (useNio) {
        println("Using non blocking IO")
        new NonBlockingServiceProvider 
      } else {
        println("Using blocking IO")
        new BlockingServiceProvider
      }
    val listener = provider.listen(9000, newConnection _, receive _)
    monitor.synchronized {
      monitor.wait
    }
    println("Done")
  }

}

