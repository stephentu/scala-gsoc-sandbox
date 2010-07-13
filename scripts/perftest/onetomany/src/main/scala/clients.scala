import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Client {
  val request = (0x0 to 0xff).map(_.toByte).toArray
  val lock = new Object
  var numFinished = 0
}

class Client(id: Int, serverHost: String, numRequests: Int, serviceMode: ServiceMode.Value, numTotal: Int) extends Actor {
  def act() {
    println("Connecting to server: " + serverHost)
    val server = select(Node(serverHost, 9000), 'server, serviceMode = serviceMode)
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
    println("args: " + args.toList)
    def containsOpt(opt: String) = args.filter(_.startsWith(opt)).size > 0
    def parseOpt(opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size)
    def parseOptWithDefault(opt: String, default: String) = 
        if (containsOpt(opt))
            parseOpt(opt)
        else
            default
    val numActors = parseOpt("--numclients=").toInt
    val numReqsPerActor = parseOpt("--numreqperclient=").toInt
    val connectTo = parseOptWithDefault("--host=", "127.0.0.1")
    println("connectTo: " + connectTo)
    val serviceMode = 
      if (containsOpt("--nio")) {
        println("Clients using NIO")
        ServiceMode.NonBlocking
      } else {
        println("Clients using TCP")
        ServiceMode.Blocking
      }
    val actors = (1 to numActors).map(i => new Client(i, connectTo, numReqsPerActor, serviceMode, numActors))
    val startTime = System.currentTimeMillis
    actors.foreach(_.start)
    Client.lock.synchronized {
      Client.lock.wait
    }
    val endTime = System.currentTimeMillis
    val esp = endTime - startTime 
    println("Main thread awake")
    println("Time (ms): " + esp)
    releaseResources()
  }
}
