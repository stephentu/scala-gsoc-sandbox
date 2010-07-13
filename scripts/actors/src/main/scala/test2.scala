import remote_actors.avro._

import com.googlecode.avro.marker._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import java.util.concurrent.CountDownLatch

object Test2 {
  val latch = new CountDownLatch(2)
  def main(args: Array[String]) {
    Debug.level = 3
    Test2_A.start
    Test2_B.start
    latch.await()
    println("SHUTTING DOWN")
    releaseResources()
  }
}

case class SimpleMessage2(var num: Int, var str: String, var extra: String) extends AvroRecord
class SimpleMessage2AvroSerializer extends SingleClassSpecificAvroSerializer[SimpleMessage2] {
  override def uniqueId = 209835932L
}

object Test2_A extends Actor {
  def act() {
    alive(9100, ServiceMode.NonBlocking)
    register('actorA, self)
    println("Actor A started...")
    react {
      case m @ SimpleMessage2(_, _, _) => 
        println("received0: " + m)
        react {
          case m @ SimpleMessage2(_, _, _) =>
            println("received1: " + m)
            Test2.latch.countDown()
        }
    }
  }
}



object Test2_B extends Actor {
  def act() {
    println("Actor B started...")
    val aActor = select(Node("127.0.0.1", 9100), 'actorA, new SimpleMessage2AvroSerializer, ServiceMode.NonBlocking)
    aActor ! SimpleMessage2(10, "HELLO", "IGNORE")
    aActor ! SimpleMessage2(1000, "WORLD", "ME")
    Test2.latch.countDown()
  }
}
