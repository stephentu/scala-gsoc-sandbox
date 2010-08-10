package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class LinkTest extends AssertionsForJUnit {

  class A1(implicit cfg: Configuration) extends JUnitActor {
    trapExit = true
    override def act() {
      alive(9100)
      register('a1, self)
      signalAlive()
      loop {
        react { 
          case e: Exit =>
            println("A1 got exit: " + e)
            signalDone()
            exit()
        }
      }
    }
  }

  class A2(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      defaultActor { 
        val a1 = select(Node(9100), 'a1)
        link(a1)
      }
    }
  }

  @Before def initialize() {
    setExplicitShutdown(true)
  }

  @Test def linkTest() {
    withResources {
      println("Link Test")
      implicit val cfg = new DefaultConfiguration
      startActors(List(new A1, new A2))
    }
  }

  @Test def linkNioTest() {
    withResources {
      println("Link NIO Test")
      implicit val cfg = new DefaultNonBlockingConfiguration
      startActors(List(new A1, new A2))
    }
  }

}
