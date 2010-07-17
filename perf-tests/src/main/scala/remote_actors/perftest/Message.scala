package remote_actors
package perftest

case class Message(bytes: Array[Byte], timeCreated: Long) {
  def timeElasped = System.nanoTime - timeCreated
}
