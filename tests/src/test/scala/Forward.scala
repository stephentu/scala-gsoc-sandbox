package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class ForwardTest extends AssertionsForJUnit {

  class M1(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      alive(9001)
      register('testing3, self)
      defaultActor {
        receive { case e =>
          println(this +"Sender is: " + sender)
          println(this +"Received: " + e)
          sender ! "GOT YOUR MESSAGE"
        }
      }
    }
  }
  class M2(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      alive(9000)
      register('testing2, self)
      defaultActor {
        receive { case e =>
          println(this +"Sender is: " + sender)
          println(this +"Received: " + e)
          val f3 = select(Node("127.0.0.1", 9001), 'testing3)
          f3 forward e
          println(this +"Forwarded!")
        }
      }
    }
  }
  class M3(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      defaultActor {
        val f2 = select(Node("127.0.0.1", 9000), 'testing2)
        f2 ! "Hello, world"
        receive { case e => 
          println(this +"Sender is: " + sender)
          println(this +"Received: " + e)
        }
      }
    }
  }
  class M4(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      defaultActor {
        val f2 = select(Node("127.0.0.1", 9000), 'testing2)
        val retn = f2 !? "Hello, world"       
        assert(retn === "GOT YOUR MESSAGE")
      }
    }
  }
  @Before def initialize() {
    setExplicitShutdown(true)
  }
  @Test def forwardTest() {
    withResources {
      println("Forward Test")
      implicit val cfg = new DefaultConfiguration
      startActors(List(new M1, new M2, new M3))
    }
  }
  @Test def forwardNioTest() {
    withResources {
      println("Forward NIO Test")
      implicit val cfg = new DefaultNonBlockingConfiguration
      startActors(List(new M1, new M2, new M3))
    }
  }
  @Test def forwardBangQuestionTest() {
    withResources {
      println("Forward BangQuestion Test")
      implicit val cfg = new DefaultConfiguration
      startActors(List(new M1, new M2, new M4))
    }
  }
  @Test def forwardBangQuestionNioTest() {
    withResources {
      println("Forward BangQuestion NIO Test")
      implicit val cfg = new DefaultNonBlockingConfiguration
      startActors(List(new M1, new M2, new M4))
    }
  }
}
