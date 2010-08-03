package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import remote_actors.protobuf._
import examples.Example._

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class ProtobufTest extends AssertionsForJUnit {

  @Before def initialize() {
    setExplicitShutdown(true)
  }

  class A1(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      alive(9100)
      register('a1, self)
      signalAlive()
      react { 
        case e =>
          println("Got e: " + e)
          sender.receiver ! e
          signalDone()
      }
    }
  }

  class A2(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      defaultActor {
        val a = select(Node(null, 9100), 'a1)
        a.send(PBufExample.newBuilder.setI(10).setS("HI").build, remoteActorFor(self))
        receive { case e =>
          println("Got the proper response back")
        }
      }
    }
  }

  class A3(implicit cfg: Configuration) extends JUnitActor {
    trapExit = true
    override def act() {
      defaultActor {
        val a = select(Node(null, 9100), 'a1)
        link(a)
        a.send(PBufExample.newBuilder.setI(10).setS("HI").build, remoteActorFor(self))
        receive { 
          case e: Exit => println("GOT EXIT")
        }
      }
    }
  }

  @Test def bangTest() {
    withResources {
      println("Protobuf Bang test")
      implicit val cfg = new HasProtobufSerializer with HasBlockingMode
      startActors(List(new A1, new A2))
    }
  }

  @Test def linkTest() {
    withResources {
      println("Protobuf Link test (NIO)")
      implicit val cfg = new HasProtobufSerializer with HasNonBlockingMode
      startActors(List(new A1, new A3))
    }
  }

}
