package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object SenderReceiver {
  
  class One(implicit cfg: Configuration[Proxy]) extends Actor {
    override def act() {
      val two = select(Node(null, 9100), 'two)
      two ! "Message 1"
      receive {
        case "Message 1" => 
          println("Got resp back")
      }
      two ! "Message 2"
      receive {
        case m @ "Message 2" => 
          println("Got resp back")
          sender ! m
      }
    }
  }

  class Two(ctrl: Actor)(implicit cfg: Configuration[Proxy]) extends Actor {
    override def act() {
      alive(9100)
      register('two, self)
      ctrl ! Start
      loop {
        react {
          case Stop =>
            println("Received stop from one")
            exit()
          case m @ "Message 1" => 
            println(m + " received")
            sender.receiver ! m
          case m @ "Message 2" =>
            println(m + " received")
            sender.receiver !? m match {
              case Some(resp) =>
                println("Got ack")
              case None =>
                println("ERROR")
            }
        }
      }
    }
  }


  def main(args: Array[String]) {
    Debug.level = 3
    import TestHelper._
    implicit val cfg = makeConfig(args)
    val one = new One
    val ctrl = actor {
      react {
        case Start => one.start()
      }
    }
    val two = new Two(ctrl)
    two.start()
  }
}

