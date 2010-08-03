package localhost.chat

import com.googlecode.avro.marker._

sealed trait Event
 
case class Login(var username: String) extends Event with AvroRecord
case class Logout(var username: String) extends Event with AvroRecord
 
case class ChatMessage(var fromUser: String, var message: String) extends Event with AvroRecord
 
case class GetChatLog(var fromUser: String) extends Event with AvroRecord
case class ChatLog(var messages: List[String]) extends Event with AvroRecord

case class Terminate() extends Event with AvroRecord
