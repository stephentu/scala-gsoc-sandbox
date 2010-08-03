package localhost.chat

import scala.collection.mutable.HashMap

import scala.actors._
import remote._

trait SessionManagement { this: Actor => 
 
  val storage: AbstractActor // needs someone to provide the ChatStorage
  val sessions = new HashMap[String, AbstractActor]
 
  protected def sessionManagement: PartialFunction[AnyRef, Unit] = {
    case Login(username) => 
      println("User [%s] has logged in", username)
      val session = new Session(username, storage)
      session.start()
      sessions += (username -> session)
 
    case Logout(username) =>        
      println("User [%s] has logged out", username)
      val session = sessions(username)
      session ! Terminate()
      sessions -= username 
  }  
 
  protected def shutdownSessions = 
    sessions.foreach { case (_, session) => session ! Terminate() }  
}

