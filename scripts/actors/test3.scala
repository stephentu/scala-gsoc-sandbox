import actors.protobuf._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import gsoc_scala.Simple
import Simple._

object Test {
  def main(args: Array[String]) {
    Debug.level = 3
    A.start
    B.start
  }
}

object SimpleMessageHelper {

  def newSimpleMessage(id: Int, msg: String): ProtobufSimpleMessage = {
    ProtobufSimpleMessage
      .newBuilder
      .setId(id)
      .setText(msg)
      .build
  }

}

object A extends Actor {
  def act() {
    alive(9100, new ProtobufSerializer(RemoteActor.classLoader))
    register('actorA, self)
    println("Actor A started...")
    react {
      case m: ProtobufSimpleMessage => 
        println("received0: " + m)
        react {
          case m: ProtobufSimpleMessage =>
            println("received1: " + m)
        }
    }
  }
}

object B extends Actor {
  import SimpleMessageHelper._
  def act() {
    println("Actor B started...")
    val aActor = select(Node("127.0.0.1", 9100), 'actorA, new ProtobufSerializer(RemoteActor.classLoader))
    aActor ! newSimpleMessage(10, "HELLO") 
    aActor ! newSimpleMessage(1000, "WORLD")
  }
}
