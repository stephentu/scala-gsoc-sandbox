package remote_actors
package perftest

import scala.actors.remote._

import java.util.concurrent.atomic._

object TestUtils {
  private val idGen = new AtomicInteger
  def newId() = idGen.getAndIncrement()

  def nanoToSeconds(ns: Long): Double = ns / 1e9
  def nanoToSeconds(ns: Double): Double = ns / 1e9
  def nanoToMilliseconds(ns: Long): Double = ns / 1e6
  def nanoToMilliseconds(ns: Double): Double = ns / 1e6

  def javaSerializationMessageSize(o: AnyRef): Long = {
    val s = new JavaSerializer(null, null)
    s.serialize(o).length
  }

  def newMessage(numBytes: Int): Array[Byte] = {
    val b = new Array[Byte](numBytes)
    var i = 0
    while (i < b.length) {
      b(i) = i.toByte // deliberate overflow, we don't really care here
      i += 1
    }
    b
  }

  def containsOpt(args: Array[String], opt: String) = args.filter(_.startsWith(opt)).size > 0
  def parseOptList(args: Array[String], opt: String): List[String] = 
    parseOptString(args, opt).split(",").toList
  def parseOptListDefault(args: Array[String], opt: String, default: List[String]): List[String] = 
    if (containsOpt(args, opt))
      parseOptList(args, opt)
    else
      default
  def parseOptString(args: Array[String], opt: String) = args.filter(_.startsWith(opt)).head.substring(opt.size)
  def parseOptStringDefault(args: Array[String], opt: String, default: String) = 
    if (containsOpt(args, opt))
      parseOptString(args, opt)
    else
      default
  def parseOptInt(args: Array[String], opt: String) = parseOptString(args, opt).toInt
  def parseOptIntDefault(args: Array[String], opt: String, default: Int) =
    if (containsOpt(args, opt))
      parseOptInt(args, opt)
    else
      default
}
