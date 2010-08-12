package remote_actors.javaserializer

import java.io._

object VarIntConstants {
  final val MSB    = 0x80
  final val MASK   = 0x7f
  final val MASK_L = 0x7fL
  final val SHIFT  = 7
}

/**
 * Uses variable-length zig-zag encoding for integers and longs. Must be
 * decoded with a <code>VarIntInputStream</code>.
 *
 * The encoding is described in detail at: 
 * http://code.google.com/apis/protocolbuffers/docs/encoding.html
 *
 * Implementation motivated by: 
 * http://svn.apache.org/repos/asf/avro/trunk/lang/java/src/java/org/apache/avro/io/BinaryEncoder.java
 */
class VarIntOutputStream(out: OutputStream) extends FilterOutputStream(out) {

  import VarIntConstants._

  def writeInt(v: Int) {
    var encoded = (v << 1) ^ (v >> 31) // encode
    // write least significant bits first, in chunks of 7-bits
    while ((encoded & ~MASK) != 0) {
      write(MSB | (encoded & MASK))
      encoded = encoded >>> SHIFT 
    }
    write(encoded)
  }

  def writeLong(v: Long) {
    var encoded = (v << 1) ^ (v >> 63) // encode
    // write least significant bits first, in chunks of 7-bits
    while ((encoded & ~MASK) != 0) {
      write(MSB | (encoded & MASK).toInt)
      encoded = encoded >>> SHIFT 
    }
    write(encoded.toInt)
  }
}

/**
 * Used to decode <code>VarIntOutputStream</code>
 */
class VarIntInputStream(in: InputStream) extends FilterInputStream(in) {

  import VarIntConstants._

  def readInt(): Int = {
    var cur     = read()
    if (cur < 0)
      throw new EOFException
    var bits    = 0
    var encoded = 0
    while ((cur & MSB) != 0) {
      encoded = ((cur & MASK) << bits) | encoded
      bits += SHIFT 
      if (bits >= 35)
        throw new IllegalStateException("Bad varlen encoding")
      cur = read()
      if (cur < 0)
        throw new EOFException
    }
    encoded = ((cur & MASK) << bits) | encoded
    (encoded >>> 1) ^ (-(encoded & 1))
  }

  def readLong(): Long = {
    var cur     = read()
    if (cur < 0)
      throw new EOFException
    var bits    = 0
    var encoded = 0L
    while ((cur & MSB) != 0) {
      encoded = ((cur & MASK_L) << bits) | encoded
      bits += SHIFT 
      if (bits >= 70)
        throw new IllegalStateException("Bad varlen encoding")
      cur = read()
      if (cur < 0)
        throw new EOFException
    }
    encoded = ((cur & MASK_L) << bits) | encoded
    (encoded >>> 1) ^ (-(encoded & 1))
  }
}
