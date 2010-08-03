package localhost.chat

import scala.actors.remote._

class ChatService(implicit val config: Configuration) extends ChatServer
                                                      with    SessionManagement
                                                      with    ChatManagement
                                                      with    InMemoryStorageFactory {
  val port = 9999
  val name = 'chatservice
}
