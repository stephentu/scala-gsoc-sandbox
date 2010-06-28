import scala.actors._
import Actor._
import remote._
import RemoteActor._

class B_actor(serviceFactory: ServiceFactory) extends Actor {
  def act() {
    alive(9200, serviceFactory = serviceFactory)
    register('b, self)
    loop {
      react {
        case Ping() =>
          sender ! Pong()
        case Stop() => 
          println("B is finished")
          exit()
      }
    }
  }
}

object B {
  import OptParse._
  def main(args: Array[String]) {
    //Debug.level = 3
    println("Started B with args: `" + args.mkString(" ") + "`")
    (new B_actor(getServiceFactory(args))).start
  }
}
