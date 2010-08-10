package localhost.chat

import scala.collection.mutable.{ HashMap, ListBuffer }

import scala.actors._
import remote._

trait ChatStorage extends Actor

class InMemoryStorage extends ChatStorage {

  trapExit = true

  private val chatLog = new HashMap[String, ListBuffer[String]]

  override def act() {
    loop {
      react {
        case (_: Exit) | Terminate() =>
          println("InMemory storage terminating")
          chatLog.clear()
          exit()
        case ChatMessage(from, message) =>
          println("New chat message [%s]", message)
          chatLog getOrElseUpdate (from, new ListBuffer[String]) append message
        case GetChatLog(user) =>
          reply(ChatLog(chatLog.get(user).getOrElse(new ListBuffer[String]).toList))
      }
    }
  }

}

trait InMemoryStorageFactory { this: Actor =>

  implicit val config: Configuration

  // mimic storage running remotely
  lazy val storage: AbstractActor = RemoteActor.remoteActorFor((new InMemoryStorage).start())
}
