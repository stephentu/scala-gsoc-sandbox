package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

object Link {
  class One(implicit cfg: Configuration) extends Actor {
    trapExit = true
    override def act() {
      val two = select(Node(null, 9100), 'two)
      link(two)
      two ! Stop
      react {
        case e: Exit =>
          println("Got exit")
          exit()
      }
    }
  }

  class Two(ctrl: Actor)(implicit cfg: Configuration) extends Actor {
    override def act() {
      alive(9100)
      register('two, self)
      ctrl ! Start
      react {
        case Stop =>
          println("Received stop from one")
          exit()
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
