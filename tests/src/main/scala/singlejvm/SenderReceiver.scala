package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

object SenderReceiver {
  
  class One(implicit cfg: Configuration) extends Actor {
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
          sender.send(m, remoteActorFor(self))
      }
      receive {
        case m @ "Message 3" =>
          println(m + "received")
          sender ! m
      }
      two ! Stop
      println("DONE")
    }
  }

  class Two(ctrl: Actor)(implicit cfg: Configuration) extends Actor {
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
              case "Message 2" => println("Got ack")
              case _ => println("ERROR")
            }
            val ftch = sender.receiver !! "Message 3"
            ftch respond { msg =>
              msg match {
                case "Message 3" => println("Got ack")
                case _ => println("ERROR")
              }
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

