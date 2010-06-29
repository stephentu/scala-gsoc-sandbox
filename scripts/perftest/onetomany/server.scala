import scala.actors._
import Actor._
import remote._
import RemoteActor._

case class Request(bytes: Array[Byte])
case class Response(bytes: Array[Byte])
case class Stop()

object Server {
  def main(args: Array[String]) {
    def containsOpt(opt: String) = args.contains(opt)
    val serviceFactory = 
      if (containsOpt("--nio")) {
        println("Server using NIO")
        NioServiceFactory 
      } else {
        println("Server using TCP")
        TcpServiceFactory
      }
    actor {
      alive(9000, serviceFactory = serviceFactory)
      register('server, self)
      loop {
        react {
          case Request(bytes) =>
            sender ! Response(bytes.map(b => (b + 10).toByte))
          case Stop() =>
            println("Server is stopping")
            exit()
        }
      }
    }
  }
}
