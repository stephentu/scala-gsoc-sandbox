package remote_actors
package perftest

case class Message(bytes: Array[Byte], timeCreated: Long) {
  def timeElasped = System.nanoTime - timeCreated
}

case class FromActor(val machine: String, val actorId: Symbol)
case class ToActor(val machine: String, val actorId: Symbol)

case class NodeMessage(val bytes: Array[Byte], 
                       val timeCreated: Long, 
                       val fromActor: FromActor,
                       val toActor: ToActor,
                       val runId: Int, 
                       val msgId: Int, 
                       val hasEchoed: Boolean) {
  def timeElasped = System.nanoTime - timeCreated
}
