import scala.actors._
import scala.actors.remote._

object Client {
  var cnt = 0
  val monitor = new Object
  def main(args: Array[String]) {
    Debug.level = 3
    val provider = new NonBlockingServiceProvider
    val conn = provider.connect(Node("127.0.0.1", 9000), receive _)
    (1 to 100).foreach(i => { val msg = "Hello world " + i; conn.send(msg.getBytes) })
    monitor.synchronized {
      monitor.wait
    }
  }
  def receive(conn: Connection, bytes: Array[Byte]) {
    val message = new String(bytes)
    println("Got reply: " + message)
  }
}
