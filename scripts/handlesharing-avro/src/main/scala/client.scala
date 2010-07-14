import scala.actors._
import Actor._
import remote._
import RemoteActor._

import remote_actors.avro._
import com.googlecode.avro.marker._

case class Message(var msg: String) extends AvroRecord

object Test3 {
  def main(args: Array[String]) {
    actor {
      val server = select(Node("127.0.0.1", 8000), 'server, new MultiClassSpecificAvroSerializer)
      var i = 0
      while (i < 5) {
        server !? NextServerRequest() match {
          case UseConn(handle) =>
            handle ! Message("Hello world: " + i)
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
