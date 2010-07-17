package remote_actors
package echotest

import remote_actors.perftest.TestUtils._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Server {
  def main(args: Array[String]) {
    val port = parseOptIntDefault(args, "--port=", 9000)
    actor {
      alive(port)
      register('server, self)
      println("Actor registered on port: " + port)
      loop {
        react {
          case e => sender ! e
        }
      }
    }
  }
}
