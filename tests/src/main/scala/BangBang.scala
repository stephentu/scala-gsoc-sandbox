package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object BangBang {
  
  class One(implicit cfg: Configuration[Proxy]) extends Actor {
    override def act() {
      val two = select(Node(null, 9100), 'two)
      (1 to 5) foreach { i =>
        val ftch = two !! i
        val m = ftch()
        println("Got m: " + m)
        if (m == 5) two ! Stop
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
          case i => println("Received: " + i)
            sender ! i
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
