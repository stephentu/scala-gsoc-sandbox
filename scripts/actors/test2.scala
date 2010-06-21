import actors.avro._

import com.googlecode.avro.marker._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Test {
  def main(args: Array[String]) {
    Debug.level = 3
    A.start
    B.start
  }
}

object A extends Actor {
  case class SimpleMessage(var num: Double, var str: Option[String]) extends AvroRecord
  def act() {
    alive(9100, new SingleClassSpecificAvroSerializer[SimpleMessage](RemoteActor.classLoader), serviceFactory = NioServiceFactory)
    register('actorA, self)
    println("Actor A started...")
    react {
      case m @ SimpleMessage(_, _) => 
        println("received0: " + m)
        react {
          case m @ SimpleMessage(_, _) =>
            println("received1: " + m)
        }
    }
  }
}

object B extends Actor {
  case class SimpleMessage(var num: Int, var str: String, var extra: String) extends AvroRecord
  def act() {
    println("Actor B started...")
    val aActor = select(Node("127.0.0.1", 9100), 'actorA, new SingleClassSpecificAvroSerializer[SimpleMessage](RemoteActor.classLoader), serviceFactory = NioServiceFactory)
    aActor ! SimpleMessage(10, "HELLO", "IGNORE")
    aActor ! SimpleMessage(1000, "WORLD", "ME")
  }
}
