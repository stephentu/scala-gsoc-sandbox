import remote_actors.protobuf._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import gsoc_scala.Simple
import Simple._

object Test3 {
  def main(args: Array[String]) {
    Debug.level = 3
    Test3_A.start()
    Test3_B.start()
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

object Test3_A extends Actor {
  def act() {
    alive(9100)
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

object Test3_B extends Actor {
  import SimpleMessageHelper._
  def act() {
    println("Actor B started...")
    val aActor = select(Node("127.0.0.1", 9100), 'actorA, new ProtobufSerializer)
    aActor ! newSimpleMessage(10, "HELLO") 
    aActor ! newSimpleMessage(1000, "WORLD")
  }
}
