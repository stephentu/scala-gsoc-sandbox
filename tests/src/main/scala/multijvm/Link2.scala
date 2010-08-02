package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import java.util.concurrent.CountDownLatch

object Link2 {
  def main(args: Array[String]) {
    Debug.level = 3
    val latch = new CountDownLatch(1)
    val a = new Actor {
      trapExit = true
      override def act() {
        val l = select(Node(null, 9100), 'linkToMe)
        link(l)
        latch.countDown()
        react { case e => }
      }
    }
    a.start()
    latch.await()
    val l = remoteActorAt(Node(null, 9100), 'linkToMe)
    l.send("STOP", null)
    println("Waiting for exit from " + a)
  }
}
