package remote_actors.javaserializer

import java.io._

import scala.actors.Debug
import scala.actors.remote._

class EnhancedJavaSerializer(cl: ClassLoader) extends JavaSerializer(cl) {
  def this() = this(null)

  override val uniqueId = 2898829171L

  override def writeString(os: DataOutputStream, s: String) {
    val output = new VarIntOutputStream(os) 
    if (s eq null) {
      output.writeInt(0)
    } else {
      val l = s.length
      output.writeInt(l)
      var i = 0
      while (i < l) {
        output.write(s.charAt(i))
        i += 1
      }
    }
  }

  override def readString(is: DataInputStream) = {
    val input = new VarIntInputStream(is)
    val len = input.readInt()
    if (len == 0) null
    else {
      val bytes = new Array[Byte](len)
      val read = input.read(bytes, 0, len)
      assert(read == len) // for now
      new String(bytes)
    }
  }

  override def writeLong(os: DataOutputStream, l: Long) {
    val output = new VarIntOutputStream(os) 
    output.writeLong(l)
  }

  override def readLong(is: DataInputStream) = {
    val input = new VarIntInputStream(is)
    input.readLong()
  }

}
