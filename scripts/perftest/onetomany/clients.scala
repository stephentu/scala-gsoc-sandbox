import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Client {
  val request = (0x0 to 0xff).map(_.toByte).toArray
  val lock = new Object
  var numFinished = 0
}

class Client(id: Int, numRequests: Int, serviceFactory: ServiceFactory, numTotal: Int) extends Actor {
  def act() {
    alive(9100, serviceFactory = serviceFactory)
    register(Symbol("client" + id), self)
    val server = select(Node("127.0.0.1", 9000), 'server, serviceFactory = serviceFactory)
    var i = 0
    loopWhile(i <= numRequests) {
      if (i < numRequests) {
        if (i % 1000 == 0) 
          println(this + " is on request " + i)
        server ! Request(Client.request)
        i += 1
        react {
          case Response(_) =>
            // do nothing
        }
      } else {
        println(this + " is done")
        Client.lock.synchronized {
          Client.numFinished += 1
          if (Client.numFinished >= numTotal) {
            println("Stopping server")
            server ! Stop()
            println("Notifying main thread")
            Client.lock.notifyAll
          }
        }
        exit()
      }
    }
  }

  override def toString = "<Client " + id + ">"
}

object Clients {
  def main(args: Array[String]) {
    def containsOpt(opt: String) = args.contains(opt)
    def parseOpt(opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size).toInt
    val numActors = parseOpt("--numclients=") 
    val numReqsPerActor = parseOpt("--numreqperclient=")
    val serviceFactory = if (containsOpt("--nio"))
      NioServiceFactory else TcpServiceFactory
    val actors = (1 to numActors).map(i => new Client(i, numReqsPerActor, serviceFactory, numActors))
    val startTime = System.currentTimeMillis
    actors.foreach(_.start)
    Client.lock.synchronized {
      Client.lock.wait
    }
    val endTime = System.currentTimeMillis
    val esp = endTime - startTime 
    println("Main thread awake")
    println("Time (ms): " + esp)
  }
}
