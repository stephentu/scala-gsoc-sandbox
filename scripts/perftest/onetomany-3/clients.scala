import scala.actors._
import Actor._
import remote._
import RemoteActor._

object Client {
  val request = (0x0 to 0xff).map(_.toByte).toArray
}

case class Done(sym: Symbol)

class ClientFamily(port: Int, numClients: Int, numReqPerClient: Int, serviceFactory: ServiceFactory) {
  def spawn() {
    (1 to numClients).foreach(i => {
      val c = new Client(port, i, numReqPerClient, serviceFactory)
      c.start()
    })
  }
}

class Client(port: Int, id: Int, numRequests: Int, serviceFactory: ServiceFactory) extends Actor {
  val thisSym = Symbol("client_" + port + "_" + id)
  def act() {
    alive(port, serviceFactory = serviceFactory)
    register(thisSym, self)
    val server = select(Node("127.0.0.1", 9000), 'server, serviceFactory = serviceFactory)
    var i = 0
    loopWhile(i <= numRequests) {
      if (i < numRequests) {
        if (i % 1000 == 0) 
          println(this + " is on request " + i)
        server ! Request(Client.request)
        i += 1
        react {
          case Response(resp) =>
            if (resp.toList != Server.transformRequest(Client.request).toList)
              throw new Exception("transformed resp not valid: got <" + resp.mkString(",") + 
                  ">, expected <" + Server.transformRequest(Client.request).mkString(",") + ">")
        }
      } else {
        println(this + " is done")
        Clients ! Done(thisSym)
        exit()
      }
    }
  }

  override def toString = "<Client " + thisSym + ">"
}

object Clients extends Actor {

  def act() {
    val server = select(Node("127.0.0.1", 9000), 'server, serviceFactory = serviceFactory)
    var i = 0
    loopWhile(i <= numClients) {
      if (i < numClients) {
        react {
          case Done(sym) =>
            i += 1
            println(i + "/" + numClients + " completed")
        }
      } else {
        println("Stopping server")
        server ! Stop()
        val endTime = System.currentTimeMillis
        val esp = endTime - startTime 
        println("Done!")
        println("Time (ms): " + esp)
        exit()
      }
    }
  }

  var serviceFactory: ServiceFactory = _
  var numClients: Int = _
  var startTime: Long = _

  def main(args: Array[String]) {
    //Debug.level = 3

    def containsOpt(opt: String) = args.contains(opt)
    def parseOpt(opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size).toInt

    val numPorts = parseOpt("--numports=")
    val numActors = parseOpt("--numclientsperport=") 
    val numReqsPerActor = parseOpt("--numreqperclient=")

    numClients = numPorts * numActors

    serviceFactory = 
      if (containsOpt("--nio")) {
        println("Clients using NIO")
        NioServiceFactory 
      } else {
        println("Clients using TCP")
        TcpServiceFactory
      }

    startTime = System.currentTimeMillis

    (1 to numPorts) foreach { i =>
      val cfam = new ClientFamily(TcpService.generatePort, numActors, numReqsPerActor, serviceFactory)
      cfam.spawn()
    }

    start()

  }
}
