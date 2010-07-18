package remote_actors
package perftest

case class Message(bytes: Array[Byte], timeCreated: Long) {
  def timeElasped = System.nanoTime - timeCreated
}

case class FromActor(machine: String, actorId: Symbol)
case class ToActor(machine: String, actorId: Symbol)

case class NodeMessage(bytes: Array[Byte], 
                       timeCreated: Long, 
                       fromActor: FromActor,
                       toActor: ToActor,
                       runId: Int, 
                       msgId: Int, 
                       hasEchoed: Boolean) {
  def timeElasped = System.nanoTime - timeCreated
}
