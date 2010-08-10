package localhost.chat

import scala.collection.mutable.HashMap
import scala.actors._

trait ChatManagement { this: Actor =>
  val sessions: HashMap[String, AbstractActor] // needs someone to provide the Session map
 
  protected def chatManagement: PartialFunction[AnyRef, Unit] = {
    case msg @ ChatMessage(from, _) => sessions(from) ! msg
    case msg @ GetChatLog(from) =>     sessions(from) forward msg
  }
}
