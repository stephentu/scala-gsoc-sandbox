package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class BangQuestionLoopTest extends AssertionsForJUnit {

  class Listener(implicit cfg: Configuration[Proxy]) extends JUnitActor {
    override def act() {
      alive(9100)
      register('listener, self)
      signalAlive()
      var numStops = 0
      loop { 
        react {
          case "STOP" =>
            println("Got stop from: " + sender)
            numStops += 1
            if (numStops == 3) {
              sender.receiver.send("ACK", null)
              signalDone()
              exit()
            }
          case e =>
            println("Got e:" + e)
            reply(e)
        }
      }
    }
  }

  class Sender(implicit cfg: Configuration[Proxy]) extends JUnitActor {
    override def act() {
      defaultActor {
        val l = select(Node(9100), 'listener)
        (1 to 50).foreach(i => {
          val resp = l !? ((hashCode, i))
          assert(resp === (hashCode, i))
        })
        l ! "STOP"
      }
    }
  }

  @Before def initialize() {
    setExplicitTermination(true)
  }

  @Test def bangQuestionLoopTest() {
    withResources {
      println("BangQuestionLoop test")
      implicit val cfg = new DefaultConfiguration
      startActors(List(new Listener, new Sender, new Sender, new Sender))
    }
  }

  @Test def bangQuestionLoopNioTest() {
    withResources {
      println("BangQuestionLoop nio test")
      implicit val cfg = new DefaultNonBlockingConfiguration
      startActors(List(new Listener, new Sender, new Sender, new Sender))
    }
  }
}
