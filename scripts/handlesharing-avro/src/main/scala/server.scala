import scala.actors._
import Actor._
import remote._
import RemoteActor._

import remote_actors.avro._
import com.googlecode.avro.marker._

case class UseConn(var handle: AvroProxy) extends AvroRecord
case class NextServerRequest() extends AvroRecord
case class Stop() extends AvroRecord

object Test2 {
  def main(args: Array[String]) {
    Debug.level = 3
    (new LoadBalancer).start()
  }
}

class LoadBalancer extends Actor {

  override def act() {
    val proxies = (0 until 5).map(i => select(Node("127.0.0.1", 9100 + i), Symbol("worker" + i), new MultiClassSpecificAvroSerializer, ServiceMode.NonBlocking)).toArray
    alive(8000, ServiceMode.NonBlocking)
    register('server, self)
    var i = 0
    loop {
      react {
        case NextServerRequest() =>
          val nextProxy = proxies(i % proxies.length)
          i += 1
          sender ! UseConn(nextProxy.asInstanceOf[AvroProxy])
        case Stop() =>
          proxies.foreach(p => p ! Stop())
          releaseResourcesInActor()
          exit()
      }
    }
  }

}
