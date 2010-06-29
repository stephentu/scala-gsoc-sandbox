import scala.actors._
import Actor._
import remote._
import RemoteActor._


class Client(port: Int, serviceFactory: ServiceFactory) extends Actor {
  def act() {
    alive(port, serviceFactory = serviceFactory)
    register('client, self)
    var numRequests = 0
    var controller: OutputChannel[Any] = null 
    receive {
      case Start(num) => 
        numRequests = num
        controller  = sender
    }

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
        controller ! Finished()
        exit()
      }
    }
  }

  override def toString = "<Client port: " + port + ">"
}

object Client {
  //Debug.level = 3
  val request = (0x0 to 0xff).map(_.toByte).toArray
  import OptParse._
  def main(args: Array[String]) {
    val serviceFactory = 
      if (containsOpt(args, "--nio")) {
        println("Client using NIO")
        NioServiceFactory
      } else {
        println("Client using TCP")
        TcpServiceFactory
      }
    val port = parseIntOpt(args, "--port=")
    (new Client(port, serviceFactory)).start
  }
}
