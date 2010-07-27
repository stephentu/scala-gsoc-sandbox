package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import com.googlecode.avro.marker._
import remote_actors.avro._

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

trait IntMessage {
  def i: Int
}

case class JavaIntMessage(i: Int) extends IntMessage
case class AvroIntMessage(var i: Int) extends IntMessage with AvroRecord
case class AvroStopMessage() extends AvroRecord

class BangBangTest extends AssertionsForJUnit {

  trait MessageCreator {
    val StopMessage: AnyRef
    def newIntMessage(i: Int): IntMessage
  }

  trait JavaMessageCreator extends MessageCreator {
    val StopMessage = Stop
    def newIntMessage(i: Int) = JavaIntMessage(i)
  }

  trait AvroMessageCreator extends MessageCreator {
    val StopMessage = AvroStopMessage()
    def newIntMessage(i: Int) = AvroIntMessage(i)
  }

  trait Sender extends JUnitActor with MessageCreator {
    override def act() {
      defaultActor {
        val two = select(Node(null, 9100), 'two)
        (1 to 5) foreach { i =>
          val ftch = two !! newIntMessage(i)
          val m = ftch()
          assert(m.asInstanceOf[IntMessage].i === i)
          if (m.asInstanceOf[IntMessage].i == 5) 
            two ! StopMessage
        }
      }
    }
  }

  trait Receiver extends JUnitActor with MessageCreator {
    override def act() {
      alive(9100)
      register('two, self)
      signalAlive()
      loop {
        react {
          case StopMessage => 
            println("Got Stop from one")
            signalDone()
            exit()
          case i: IntMessage => sender ! newIntMessage(i.i) 
        }
      }
    }
  }

  class JavaSender(implicit cfg: Configuration[Proxy]) extends Sender with JavaMessageCreator
  class JavaReceiver(implicit cfg: Configuration[Proxy]) extends Receiver with JavaMessageCreator

  class AvroSender(implicit cfg: Configuration[Proxy]) extends Sender with AvroMessageCreator
  class AvroReceiver(implicit cfg: Configuration[Proxy]) extends Receiver with AvroMessageCreator

  @Before def initialize() {
    setExplicitTermination(true)
  }

  @Test def bangBangTest() {
    withResources {
      println("BangBang Test")
      implicit val cfg = new DefaultConfiguration
      startActors(List(new JavaReceiver, new JavaSender))
    }
  }

  @Test def bangBangNioTest() {
    withResources {
      println("BangBang nio Test")
      implicit val cfg = new DefaultConfiguration {
        override def aliveMode = ServiceMode.NonBlocking
        override def selectMode = ServiceMode.NonBlocking
      }
      startActors(List(new JavaReceiver, new JavaSender))
    }
  }

  @Test def bangBangAvroTest() {
    withResources {
      println("BangBang Avro Test")
      implicit val cfg = new HasMultiClassAvroSerializer with
                        HasNonBlockingAlive with
                        HasNonBlockingSelect
      startActors(List(new AvroReceiver, new AvroSender))
    }
  }
}
