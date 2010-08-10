package remote_actors
package avro_vs_java_test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Server {
  def main(args: Array[String]) {
    remoteActor(9100, 'echo) {
      loop { react { case e => sender ! e } } 
    }
  }
}
