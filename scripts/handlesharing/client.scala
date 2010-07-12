import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Test3 {
  def main(args: Array[String]) {
    actor {
      val server = select(Node("127.0.0.1", 8000), 'server)
      var i = 0
      while (i < 5) {
        server !? NextServerRequest() match {
          case UseConn(handle) =>
            handle ! ("Hello world: " + i)
            receive {
              case e => println("Received: " + e)
            }
            i += 1
        }
      }
      server ! Stop()
      releaseResourcesInActor()
    }
  }
}
