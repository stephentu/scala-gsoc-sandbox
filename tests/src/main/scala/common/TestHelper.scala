package localhost.test

import scala.actors._
import scala.actors.remote._
import RemoteActor._
import java.util.concurrent.CountDownLatch

object TestHelper {
  def makeConfig(args: Array[String]) = {
    import ParseOpt._
    val mode = if (containsOpt(args, "--nio")) {
      println("NonBlocking")
      ServiceMode.NonBlocking
    } else {
      println("Blocking")
      ServiceMode.Blocking
    }
    new DefaultConfiguration {
      override def aliveMode = mode
      override def selectMode = mode
    }
  }

  /**
   * Starts actors in order starting from the first. Then blocks until all
   * actors have reported they are finished via signalDone()
   */
  def startActors(actors: List[JUnitActor]) {
    val finishedLatch = new CountDownLatch(actors.length)
    actors.map(a => (a, new CountDownLatch(1))).foreach { case (actor, latch) =>
      actor.aliveCallback = () => { latch.countDown() }
      actor.finishedCallback = () => { finishedLatch.countDown() }
      actor.start()
      latch.await()
    }
    finishedLatch.await()
    println("startActor(): woke up from latch")
  }

  def withResources(f: => Unit) {
    f
    shutdown()
    Thread.sleep(5000)
  }
}



trait JUnitActor extends Actor {
  type JUnitActorCallback = Function0[Unit]
  var aliveCallback: JUnitActorCallback = _
  var finishedCallback: JUnitActorCallback = _
  protected def signalAlive() { aliveCallback() }
  protected def signalDone() { finishedCallback() }
  protected def defaultActor(f: => Unit) {
    signalAlive()
    f
    signalDone()
  }
}
