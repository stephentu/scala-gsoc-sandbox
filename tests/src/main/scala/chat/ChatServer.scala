package localhost.chat

import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

trait ChatServer extends Actor {
  trapExit = true

  val port: Int
  val name: Symbol

  implicit val config: Configuration
 
  val storage: AbstractActor

  start()

  // actor message handler
  override def act() {
    println("Chat server is starting up...")
    alive(port)
    register(name, self)
    link(storage)
    loop {
      react {
        case (_: Exit) | Terminate() =>
          shutdown0()
        case e: AnyRef =>
          sessionManagement orElse chatManagement apply e
      }
    }
  } 
 
  // abstract methods to be defined somewhere else
  protected def chatManagement: PartialFunction[AnyRef, Unit]
  protected def sessionManagement: PartialFunction[AnyRef, Unit]   

  protected def shutdownSessions: Unit

  def shutdown() {
    this.send(Terminate(), null)
  }
 
  private def shutdown0() { 
    println("Chat server is shutting down...")
    shutdownSessions
    unlink(storage)
    storage ! Terminate()
  }
}
