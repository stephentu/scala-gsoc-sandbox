package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class EchoActor extends Actor {
  override def act() {
    println("EchoActor alive")
    alive(9100)
    register('echoactor, self)
    loop {
      react {
        case "STOP" =>
          println("Stopping")
          sender ! "ACK"
          exit()
        case e =>
          println("Got: " + e)
          sender ! e
      }
    }
  }
}
class EchoActorNoAlive extends Actor {
  override def act() {
    println("EchoActorNoAlive alive")
    loop {
      react {
        case "STOP" =>
          println("Stopping")
          sender ! "ACK"
          exit()
        case e =>
          println("Got: " + e)
          sender ! e
      }
    }
  }
}


class RemoteStartTest extends AssertionsForJUnit {
  class RemoteStarter(implicit cfg: Configuration[Proxy]) extends JUnitActor {
    override def act() {
      defaultActor {
        remoteStart[EchoActor]("localhost") 
        val e = select(Node(null, 9100), 'echoactor)
        e ! "Hello"
        receive { case m => 
          assert(m === "Hello")
        }
        val ftch = e !! "STOP" 
        assert(ftch() === "ACK")
      }
    }
  }
  class RemoteStartAndListener(implicit cfg: Configuration[Proxy]) extends JUnitActor {
    override def act() {
      defaultActor {
        val e = remoteStartAndListen[EchoActorNoAlive, Proxy]("localhost", 9100, 'echoactor) 
        e ! "Hello"
        receive { case m => 
          assert(m === "Hello")
        }
        val ftch = e !! "STOP" 
        assert(ftch() === "ACK")
      }
    }
  }
  @Before def initialize() {
    setExplicitTermination(true)
  }
  private def withController(f: => Unit) {
    startController()
    Thread.sleep(1000)
    f
    stopController()
  }
  @Test def remoteStartTest() {
    withResources {
      println("RemoteStart Test")
      withController {
        Debug.level = 3
        implicit val cfg = new DefaultConfiguration
        startActors(List(new RemoteStarter))
      }
    }
  }
  @Test def remoteStartAndListenTest() {
    withResources {
      println("RemoteStartAndListen Test")
      withController {
        implicit val cfg = new DefaultConfiguration
        val actor = new RemoteStartAndListener
        startActors(List(actor))
      }
    }
  }
}
