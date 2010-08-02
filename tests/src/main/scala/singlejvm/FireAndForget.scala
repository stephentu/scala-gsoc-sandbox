package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

object FireAndForget {

  class One(implicit cfg: Configuration) extends Actor {
    override def act() {
      val two = select(Node(null, 9100), 'two)
      (1 to 5) foreach { i => two ! i }
      loop {
        react {
          case i =>
            println("Got back: " + i)
            if (i == 5) {
              two ! Stop
              exit()
            }
        }
      }
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
