import scala.actors._
import Actor._
import remote._
import RemoteActor._

case class UseConn(handle: OutputChannel[Any])
case class NextServerRequest()
case class Stop()

object Test2 {
  def main(args: Array[String]) {
    Debug.level = 3
    (new LoadBalancer).start()
  }
}

class LoadBalancer extends Actor {

  override def act() {
    val proxies = (0 until 5).map(i => select(Node("127.0.0.1", 9100 + i), Symbol("worker" + i), serviceMode = ServiceMode.NonBlocking)).toArray
    alive(8000, ServiceMode.NonBlocking)
    register('server, self)
    var i = 0
    loop {
      react {
        case NextServerRequest() =>
          val nextProxy = proxies(i % proxies.length)
          i += 1
          sender ! UseConn(nextProxy)
        case Stop() =>
          proxies.foreach(p => p ! Stop())
          releaseResourcesInActor()
          exit()
      }
    }
  }

}
