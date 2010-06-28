import scala.actors._
import Actor._
import remote._
import RemoteActor._

class A_actor(serviceFactory: ServiceFactory) extends Actor {
  import Instrument._
  def act() {
    val bootstrapTime = measureTimeMillisNoReturn {
      alive(9100, serviceFactory = serviceFactory)
      register('a, self)
    }
    var numPings = 0
    var origPings = 0
    receive {
      case Start(i) => 
        numPings = i
        origPings = i
    }
    val (connectTime, b) = measureTimeMillis {
      select(Node("127.0.0.1", 9200), 'b, serviceFactory = serviceFactory)
    }
    val timer = new SimpleTimer
    timer.start()
    b ! Ping()
    numPings -= 1
    loopWhile(numPings >= 0) {
      if (numPings > 0) {
        react {
          case Pong() =>
            sender ! Ping()
            numPings -= 1
        }
      } else {
        val pingTime = timer.finish()
        b ! Stop()
        println("A is finished")
        println("Results to ping " + origPings + " messages (ms): ")
        println("Bootstrap Time: " + bootstrapTime)
        println("Connect Time  : " + connectTime)
        println("Ping Time     : " + pingTime)
        exit()
      }
    }
  }
}

object A {
  import OptParse._
  def main(args: Array[String]) {
    //Debug.level = 3
    println("Started A with args: `" + args.mkString(" ") + "`")
    (new A_actor(getServiceFactory(args))).start
  }
}
