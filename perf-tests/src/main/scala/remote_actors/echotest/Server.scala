package remote_actors
package echotest

import remote_actors.perftest.TestUtils._

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

object Server {
  def main(args: Array[String]) {
    val port = parseOptIntDefault(args, "--port=", 9000)
    val mode = if (containsOpt(args, "--nio")) ServiceMode.NonBlocking else ServiceMode.Blocking
    implicit object cfg extends Configuration with HasJavaSerializer {
      override def aliveMode  = mode
      override def selectMode = mode
    }
    actor {
      alive(port)
      register('server, self)
      println("Actor registered on port: " + port + " in mode: " + mode)
      loop {
        react {
          case e => sender ! e
        }
      }
    }
  }
}
