import scala.actors._
import Actor._
import remote._
import RemoteActor._

import java.util.concurrent.CountDownLatch

object Test1 {
  def main(args: Array[String]) {
    Debug.level = 3
    val latch = new CountDownLatch(5)
    (0 until 5).foreach(i => {
      val worker = new ServerWorker(9100 + i, Symbol("worker" + i), latch)
      worker.start()
    })
    latch.await()
    releaseResources()
  }
}

class ServerWorker(port: Int, name: Symbol, latch: CountDownLatch) extends Actor {

  override def act() {
    alive(port, ServiceMode.NonBlocking)
    register(name, self)
    loop {
      react {
        case Stop() =>
          latch.countDown()
          exit()
        case e =>
          println(this + ": received " + e)
          sender ! e
      }
    }
  }

}
