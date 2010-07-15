package remote_actors
package perftest

class Timer {
  private var startTime: Option[Long] = None
  def start() {
    startTime = Some(System.nanoTime)
  }
  def end(): Long = startTime match {
    case Some(time) =>
      System.nanoTime - time
    case None =>
      throw new IllegalStateException("Timer was not started")
  }
}
