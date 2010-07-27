package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object BangQuestion {
  
  class One(implicit cfg: Configuration[Proxy]) extends Actor {
    override def act() {
      val two = select(Node(null, 9100), 'two)
      (1 to 5) foreach { i =>
        two !? i match {
          case 5 => 
            println("One sending stop to two")
            two ! Stop
          case x =>
            println("One received response to: " + x)
        }
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
            println("Two received stop from one")
            val three = select(Node(null, 9100), 'three)  
            three ! Stop
            println("Two sent stop to three and exiting")
            exit()
          case i => 
            println("Two received: " + i)
            val origSender = sender
            val three = select(Node(null, 9100), 'three)  
            println("Two relaying message to three")
            three !? i match {
              case x => 
                println("Two received resp from three: " + x)
            }
            receive {
              case x => 
                println("Two received message from three: " + x)
                sender ! x
            }
            println("Two relaying message back to one: " + i)
            origSender ! i
        }
      }
    }
  }

  class Three(ctrl: Actor)(implicit cfg: Configuration[Proxy]) extends Actor {
    override def act() {
      alive(9100)
      register('three, self)
      ctrl ! Start
      loop {
        react {
          case Stop =>
            println("Three received stop from two")
            println("LOGIC DONE")
            exit()
          case e =>
            println("Three received message from two: " + e)
            sender ! e
            println("Three sending message to two")
            sender.receiver !? "HI" match {
              case x => 
                println("Three received echo from two: " + x)
            }
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
          val twoCtrl = actor {
            react {
              case Start =>
                val one = new One
                one.start()
            }
          }
          val two = new Two(twoCtrl)
          two.start()
      }
    }
    val three = new Three(ctrl)
    three.start()
  }
}
