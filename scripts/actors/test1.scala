import actors.avro._

import java.io._

import scala.actors.remote.JavaSerializer
import scala.reflect.Manifest

import org.apache.avro.{ io, specific, Schema }
import io._
import specific._

import com.googlecode.avro.marker._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

case class SimpleMessage(var message: String) extends AvroRecord

object Test {
  def main(args: Array[String]) {
    Debug.level = 3
    A.start
    B.start
  }
}

object A extends Actor {
  def act() {
    alive(9100, new MultiClassSpecificAvroSerializer(RemoteActor.classLoader))
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
        }
    }
  }
}

object B extends Actor {
  def act() {
    println("Actor B started...")
    val aActor = select(Node("127.0.0.1", 9100), 'actorA, new MultiClassSpecificAvroSerializer(RemoteActor.classLoader))
    aActor ! SimpleMessage("Hello, world")
    react {
      case SimpleMessage(m) =>
        println(m)
        val future = aActor !! SimpleMessage("Sync send")
        println("received: " + future())
    }
  }
}
