package localhost.chat

import scala.actors._

class Session(user: String, storage: AbstractActor) extends Actor {
  private val loginTime = System.currentTimeMillis
  private var userLog: List[String] = Nil
 
  println("New session for user [%s] has been created at [%s]", user, loginTime)
 
  override def act() {
    loop {
      react {
        case event: ChatMessage =>
          userLog ::= event.message
          storage ! event
        case event: GetChatLog =>
          storage forward event
        case Terminate() =>
          println("Session " + user + " got terminate")
          exit()
      }
    } 
  }
}
