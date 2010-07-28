package localhost.test

import scala.actors._
import Actor._

class EchoActor extends Actor {
  override def act() {
    println("EchoActor started")
    loop {
      react {
        case "STOP" => 
          println("Stopping")
          exit()
        case e =>
          println("Got: " + e)
          sender ! e
      }
    }
  }
}
