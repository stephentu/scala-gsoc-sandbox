package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class BangQuestionLoop extends AssertionsForJUnit {

  class Listener(implicit cfg: Configuration[Proxy]) extends JUnitActor {
    override def act() {

    }
  }

  class Sender(implicit cfg: Configuration[Proxy]) extends JUnitActor {
    override def act() {
      defaultActor {

      }
    }
  }

  @Test def bangQuestionLoopTest() {
    withResources {
      println("BangQuestionLoop test")

    }
  }

  @Test def bangQuestionLoopNioTest() {
    withResources {
      println("BangQuestionLoop nio test")

    }
  }
}
