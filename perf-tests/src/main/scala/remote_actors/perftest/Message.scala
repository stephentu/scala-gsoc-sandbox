package remote_actors
package perftest

case class Message(bytes: Array[Byte], timeCreated: Long) {
  def timeElasped = System.nanoTime - timeCreated
}

case class NodeMessage(bytes: Array[Byte], timeCreated: Long, machine: String, runId: Int, actorId: Int, msgId: Int, hasEchoed: Boolean) {
  def timeElasped = System.nanoTime - timeCreated
}
