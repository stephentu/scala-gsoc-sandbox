package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import TestHelper._

import org.junit.Assert._
import org.junit._
import org.scalatest.junit._

class SenderReceiverTest extends AssertionsForJUnit {

  class One(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      defaultActor {
        val two = select(Node(null, 9283), 'two)
        two ! "Message 1"
        receive {
          case "Message 1" => 
            println("Got resp back")
        }
        two ! "Message 2"
        receive {
          case m @ "Message 2" => 
            println("Got resp back")
            sender.send(m, remoteActorFor(self))
        }
        receive {
          case m @ "Message 3" =>
            println(m + "received")
            sender ! m
        }
        two ! Stop
        println("DONE")
      }
    }
  }

  class Two(implicit cfg: Configuration) extends JUnitActor {
    override def act() {
      alive(9283)
      register('two, self)
      signalAlive()
      loop {
        react {
          case Stop =>
            println("Received stop from one")
            signalDone()
            exit()
          case m @ "Message 1" => 
            println(m + " received")
            sender.receiver ! m
          case m @ "Message 2" =>
            println(m + " received")
            sender.receiver !? m match {
              case "Message 2" => println("Got ack")
              case _ => println("ERROR")
            }
            val ftch = sender.receiver !! "Message 3"
            ftch respond { msg =>
              msg match {
                case "Message 3" => println("Got ack")
                case _ => println("ERROR")
              }
            }
        }
      }
    }
  }

  @Before def initialize() {
    setExplicitShutdown(true)
  }

  @Test def senderReceiverTest() {
    withResources {
      println("SenderReceiver Test")
      implicit val cfg = new DefaultConfiguration
      startActors(List(new One, new Two))
    }
  }

  @Test def senderReceiverNioTest() {
    withResources {
      println("SenderReceiver NIO Test")
      implicit val cfg = new DefaultNonBlockingConfiguration
      startActors(List(new One, new Two))
    }
  }

}
