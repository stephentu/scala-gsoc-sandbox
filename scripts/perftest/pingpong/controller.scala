import scala.actors._
import Actor._
import remote._
import RemoteActor._

case class Start(num: Int)
case class Stop()
case class Ping()
case class Pong()

class SimpleTimer {
  private var startTime: Long = _
  def start() {
    startTime = System.currentTimeMillis
  }
  def finish() = {
    System.currentTimeMillis - startTime
  }
} 

object Instrument {
  def measureTimeMillisNoReturn(f: => Unit): Long = 
    measureTimeMillis(f)._1
  def measureTimeMillis[A](f: => A): (Long, A) = {
    val start = System.currentTimeMillis
    val ret   = f
    val end   = System.currentTimeMillis
    (end - start, ret)
  }
}

object OptParse {
  def getServiceFactory(args: Array[String]) = {
    val useNio = args.contains("--nio") 
    if (useNio)  
      NioServiceFactory 
    else 
      TcpServiceFactory
  }
  def getNumPings(args: Array[String]) = {
    val possibleArgs = args.filter(_.startsWith("--num="))
    if (possibleArgs isEmpty) {
      Console.err.println("Using default --num=1000")
      1000 // default
    } else {
      if (possibleArgs.size > 1) {
        Console.err.println("more than one --num arg found, using first")
      }
      possibleArgs.head.substring(6).toInt
    }
  }
}


object Controller {
  def main(args: Array[String]) {
    import OptParse._
    actor {
      val a = select(Node("127.0.0.1", 9100), 'a, serviceFactory = getServiceFactory(args))
      val num = getNumPings(args)
      a ! Start(num)
      println("Controller is done: num pings = " + num)
    }
  }
}
