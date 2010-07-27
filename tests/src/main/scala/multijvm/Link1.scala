package localhost.test

import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Link1 {
  def main(args: Array[String]) {
    Debug.level = 3
    actor {
      alive(9100)
      register('linkToMe, self)
      loop {
        react {
          case "STOP" =>
            println("Stopping")
            exit()
        }
      }
    }
  }
}
