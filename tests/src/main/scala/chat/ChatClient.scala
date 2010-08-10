package localhost.chat

import scala.actors.remote._

class ChatClient(val name: String)(implicit val config: Configuration) {
  val chat = RemoteActor.select(Node(null, 9999), 'chatservice) 

  def login =                 chat ! Login(name) 
  def logout =                chat ! Logout(name)  
  def post(message: String) = chat ! ChatMessage(name, name + ": " + message)  
  def chatLog: ChatLog =     (chat !? (10000, GetChatLog(name)))
    .getOrElse(throw new Exception("Couldn't get the chat log from ChatServer"))
    .asInstanceOf[ChatLog]
}
