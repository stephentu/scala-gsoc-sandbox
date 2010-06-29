import scala.actors._
import Actor._
import remote._
import RemoteActor._

case class Start(num: Int)
case class Finished()

object OptParse {
  def containsOpt(args: Array[String], opt: String) = args.contains(opt)
  def parseOpt(args: Array[String], opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size)
  def parseIntOpt(args: Array[String], opt: String) = parseOpt(args, opt).toInt
  def parseArrayOpt(args: Array[String], opt: String, sep: String = ",") = parseOpt(args, opt).split(sep)
  def parseRangeOpt(args: Array[String], opt: String) = {
    val array = parseArrayOpt(args, opt, "\\.\\.")
    (array(0).toInt to array(1).toInt)
  }

}

object Controller {
  import OptParse._
  def main(args: Array[String]) {
    val serviceFactory = 
      if (containsOpt(args, "--nio")) {
        println("Controller using NIO")
        NioServiceFactory
      } else {
        println("Controller using TCP")
        TcpServiceFactory
      }
    val ports = parseRangeOpt(args, "--ports=")
    val numActors = ports.size
    println("Controller: num processes: " + numActors)
    val numReqsPerActor = parseIntOpt(args, "--numreqperclient=")
    val startTime = System.currentTimeMillis
    println("Controller: Time started")
    actor {
      alive(8000, serviceFactory = serviceFactory)
      register('controller, self)
      val server = select(Node("127.0.0.1", 9000), 'server, serviceFactory = serviceFactory)
      val actors = ports.map(p => select(Node("127.0.0.1", p), 'client, serviceFactory = serviceFactory))
      actors.foreach(_ ! Start(numReqsPerActor))
      var i = 0
      loopWhile(i <= numActors) {
        if (i < numActors) {
          react {
            case Finished() =>
              i += 1
          }
        } else {
          println("All actors done")
          server ! Stop()
          val endTime = System.currentTimeMillis
          val esp = endTime - startTime
          println("Time (ms): " + esp)
          exit()
        }
      }
    }
  }
}
