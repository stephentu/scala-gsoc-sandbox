package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

object BangQuestionTimeout {
  class One(implicit cfg: Configuration) extends Actor {
    override def act() {
      val two = select(Node(null, 9100), 'two)
      two !? (5000, "Hello, world") match {
        case Some(resp) => println("Got resp: " + resp)
        case None => println("ERROR: TIMEOUT")
      }
      two !? (1000, "NOREPLY") match {
        case Some(resp) => println("ERROR: Why did you reply?")
        case None => println("Good, no reply received")
      }
      two ! Stop
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
            println("One sent stop to two and exiting")
            exit()
          case "NOREPLY" =>
            println("Got NOREPLY, so not doing anything")
          case msg =>
            println("Got msg <" + msg + "> from one, echoing")
            sender ! msg
        }
      }
    }
  }
  def main(args: Array[String]) {
    Debug.level = 3
    import TestHelper._
    implicit val cfg = makeConfig(args)
    val ctrl = actor {
      react {
        case Start => 
          val one = new One
          one.start()
      }
    }
    val two = new Two(ctrl)
    two.start()
  }
}
