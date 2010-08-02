package localhost

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

object ForwardTest1 {
  def main(args: Array[String]) {
    Debug.level = 3
    actor {
      val f2 = select(Node("127.0.0.1", 9000), 'testing2)
      f2 ! "Hello, world"
      react {
        case e => 
          println(this +"Sender is: " + sender)
          println(this +"Received: " + e)
      }
    }
  }
  override def toString = "<ForwardTest1>"
}
object ForwardTest2 {
  def main(args: Array[String]) {
    Debug.level = 3
    actor {
      alive(9000)
      register('testing2, self)
      react {
        case e =>
          println(this +"Sender is: " + sender)
          println(this +"Received: " + e)
          val f3 = select(Node("127.0.0.1", 9001), 'testing3)
          f3 forward e
      }
    }
  }
  override def toString = "<ForwardTest2>"
}
object ForwardTest3 {
  def main(args: Array[String]) {
    Debug.level = 3
    actor {
      alive(9001)
      register('testing3, self)
      react {
        case e =>
          println(this +"Sender is: " + sender)
          println(this +"Received: " + e)
          sender ! "GOT YOUR MESSAGE"
      }
    }
  }
  override def toString = "<ForwardTest3>"
}
