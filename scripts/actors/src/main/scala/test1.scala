import remote_actors.avro._

import java.io._

import scala.reflect.Manifest

import org.apache.avro.{ io, specific, Schema }
import io._
import specific._

import com.googlecode.avro.marker._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import java.util.concurrent.CountDownLatch

case class SimpleMessage(var message: String) extends AvroRecord

object Test1 {
  val latch = new CountDownLatch(2)
  def main(args: Array[String]) {
    Debug.level = 3
    Test1_A.start
    Test1_B.start
    latch.await()
    println("Test1 shutting down")
    releaseResources()
  }
}

object Test1_A extends Actor {
  def act() {
    alive(9100)
    register('actorA, self)
    println("Actor A started...")
    react {
      case SimpleMessage(m) => 
        println("received: " + m)
        sender ! SimpleMessage("Right back at you!")
        react {
          case SimpleMessage(m) =>
            println("received: " + m)
            sender ! SimpleMessage("My reply")
            Test1.latch.countDown()
        }
    }
  }
}

object Test1_B extends Actor {
  def act() {
    println("Actor B started...")
    val aActor = select(Node("127.0.0.1", 9100), 'actorA, new MultiClassSpecificAvroSerializer)
    aActor ! SimpleMessage("Hello, world")
    react {
      case SimpleMessage(m) =>
        println(m)
        val future = aActor !! SimpleMessage("Sync send")
        println("received: " + future())
        Test1.latch.countDown()
    }
  }
}
