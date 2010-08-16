package remote_actors.avro 

/** Avro Imports */
import org.apache.avro.{ io, generic, specific, util, Schema }
import io._
import generic._
import specific._
import util._

/** Avro Scala Compiler Plugin Imports */
import com.googlecode.avro.marker._
import com.googlecode.avro.runtime._
import TypedSchemas._

/** Scala Remote Actors Imports */
import scala.actors.Debug
import scala.actors.remote._

/** Scala Collection Imports */
import scala.collection.mutable.HashMap
import scala.collection.JavaConversions._

/** Java utility imports */
import java.io._
import java.math.BigInteger
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

// configurations

/**
 * The multi class configuration class
 */
abstract class HasMultiClassAvroSerializer extends Configuration { 
  override def newSerializer() = new MultiClassSpecificAvroSerializer
}

//abstract class HasSingleClassAvroSerializer[R <: SpecificRecord : Manifest] extends Configuration { 
//  override def newSerializer() = new SingleClassClientSpecificAvroSerializer[R]
//}

sealed trait AvroNetKernelMessage extends AvroUnion
sealed trait AvroMessageCommand   extends AvroNetKernelMessage {
  def className: Option[String]
  def toNetKernelMessage(message: AnyRef): NetKernelMessage
}

// internal message types

case class AvroLocateRequest(var sessionId: Long,
                             var receiverName: String) 
  extends AvroRecord with LocateRequest with AvroNetKernelMessage

case class AvroLocateResponse(var sessionId: Long,
                              var receiverName: String,
                              var found: Boolean) 
  extends AvroRecord with LocateResponse with AvroNetKernelMessage

case class AvroAsyncSend(var senderName: String,
                         var receiverName: String,
                         var className: Option[String])
  extends AvroRecord with AvroMessageCommand {

  override def toNetKernelMessage(message: AnyRef) =
    DefaultAsyncSendImpl(senderName, receiverName, message)
}


case class AvroSyncSend(var senderName: String,
                        var receiverName: String,
                        var session: String,
                        var className: Option[String])
  extends AvroRecord with AvroMessageCommand {

  override def toNetKernelMessage(message: AnyRef) =
    DefaultSyncSendImpl(senderName, receiverName, message, session)

}

case class AvroSyncReply(var receiverName: String,
                         var session: String,
                         var className: Option[String])
  extends AvroRecord with AvroMessageCommand {

  override def toNetKernelMessage(message: AnyRef) =
    DefaultSyncReplyImpl(receiverName, message, session)

}

object AvroRemoteFunction {
  val LinkTo     = 0
  val UnlinkFrom = 1
  val Exit       = 2
  def intToRemoteFunction(i: Int, r: Option[String]): RemoteFunction = i match {
    case LinkTo     => LinkToFun
    case UnlinkFrom => UnlinkFromFun
    case Exit       => ExitFun(r.getOrElse("No Reason"))
  }
  def remoteFunctionToInt(r: RemoteFunction): (Int, Option[String]) = r match {
    case LinkToFun       => (LinkTo, None)
    case UnlinkFromFun   => (UnlinkFrom, None)
    case ExitFun(reason) => (Exit, Some(reason.toString))
  }
}

case class AvroRemoteApply(var senderName: String,
                           var receiverName: String,
                           var _function: Int,
                           var _reason: Option[String]) extends RemoteApply 
                                                        with    AvroRecord 
                                                        with    AvroNetKernelMessage{
  import AvroRemoteFunction._
  override def function = intToRemoteFunction(_function, _reason)
}

// message creator

case class AvroRemoteStartInvoke(var actorClass: String) extends RemoteStartInvoke 
                                                         with    AvroRecord 

case class AvroRemoteStartInvokeAndListen(var actorClass: String,
                                          var port: Int,
                                          var name: String) 
  extends RemoteStartInvokeAndListen 
  with    AvroRecord

case class AvroRemoteStartResult(var errorMessage: Option[String])
  extends RemoteStartResult 
  with    AvroRecord

trait HasAvroMessageCreator { _: Configuration =>
  override def newMessageCreator() = new AvroMessageCreator
}

class AvroMessageCreator extends MessageCreator { 
  override def newRemoteStartInvoke(actorClass: String) = 
    AvroRemoteStartInvoke(actorClass)

  override def newRemoteStartInvokeAndListen(actorClass: String, port: Int, name: String) =
    AvroRemoteStartInvokeAndListen(actorClass, port, name)

  override def newRemoteStartResult(errorMessage: Option[String]) =
    AvroRemoteStartResult(errorMessage)
}

// utilities

class ScalaSpecificDatumReader[T](schema: Schema)(implicit m: Manifest[T]) 
  extends SpecificDatumReader[T](schema) {
  private val clz = m.erasure.asInstanceOf[Class[T]]
  override def newRecord(old: AnyRef, schema: Schema): AnyRef = {
    if ((old ne null) && clz.isInstance(old)) old
    else super.newRecord(old, schema)
  }
}

class ScalaSpecificClassDatumReader[T](schema: Schema, clz: Class[T]) 
  extends SpecificDatumReader[T](schema) {
  override def newRecord(old: AnyRef, schema: Schema): AnyRef = {
    if ((old ne null) && clz.isInstance(old)) old
    else super.newRecord(old, schema)
  }
}

abstract class BaseAvroSerializer extends Serializer {

  import AvroRemoteFunction._

  protected val writeClassName: Boolean

  private val messageSchema = schemaOf[AvroNetKernelMessage]
  private val messageWriter = new GenericDatumWriter[AvroNetKernelMessage](messageSchema)

  @inline private def writeNKBytes(message: AvroNetKernelMessage, outputStream: OutputStream) {
    messageWriter.write(message, new BinaryEncoder(outputStream))
  }

  private val messageReader  = new SpecificDatumReader[AvroNetKernelMessage](messageSchema)
  private val decoderFactory = (new DecoderFactory).configureDirectDecoder(true) /** No buffering */

  @inline private def readNKBytes(inputStream: InputStream): AvroNetKernelMessage = {
    val inStream = decoderFactory.createBinaryDecoder(inputStream, null)
    messageReader.read(null, inStream)
  }

  protected def writeBytes(container: GenericContainer, outputStream: OutputStream): Unit

  protected def readBytes(srClz: Option[Class[_]], inputStream: InputStream): GenericContainer

  override def writeLocateRequest(outputStream: OutputStream, sessionId: Long, receiverName: String) {
    writeNKBytes(AvroLocateRequest(sessionId, receiverName), outputStream)
  }

  override def writeLocateResponse(outputStream: OutputStream, sessionId: Long, receiverName: String, found: Boolean) {
    writeNKBytes(AvroLocateResponse(sessionId, receiverName, found), outputStream)
  }

  @inline private def getClassName(message: AnyRef) = 
    if (writeClassName)
      Some(message.getClass.getName)
    else 
      None

  override def writeAsyncSend(outputStream: OutputStream, senderName: String, receiverName: String, message: AnyRef) {
    Debug.info("writeAsyncSend [1]: %s".format(outputStream))
    writeNKBytes(AvroAsyncSend(senderName, receiverName, getClassName(message)), outputStream)
    Debug.info("writeAsyncSend [2]: %s".format(outputStream))
    writeBytes(message.asInstanceOf[GenericContainer], outputStream)
    Debug.info("writeAsyncSend [3]: %s".format(outputStream))
  }

  override def writeSyncSend(outputStream: OutputStream, senderName: String, receiverName: String, message: AnyRef, session: String) {
    writeNKBytes(AvroSyncSend(senderName, receiverName, session, getClassName(message)), outputStream)
    writeBytes(message.asInstanceOf[GenericContainer], outputStream)
  }
                                                                                   
  override def writeSyncReply(outputStream: OutputStream, receiverName: String, message: AnyRef, session: String) {
    writeNKBytes(AvroSyncReply(receiverName, session, getClassName(message)), outputStream)
    writeBytes(message.asInstanceOf[GenericContainer], outputStream)
  }

  override def writeRemoteApply(outputStream: OutputStream, senderName: String, receiverName: String, rfun: RemoteFunction) {
    val (funid, reason) = remoteFunctionToInt(rfun)
    writeNKBytes(AvroRemoteApply(senderName, receiverName, funid, reason), outputStream)
  }

  override def read(data: InputStream) = {
    readNKBytes(data) match {
      case m: AvroLocateRequest => m
      case m: AvroLocateResponse => m
      case m: AvroMessageCommand =>
        val message = readBytes(m.className.map(c => Class.forName(c)), data)
        m.toNetKernelMessage(message)
      case AvroRemoteApply(senderName, receiverName, funid, reason) =>
        DefaultRemoteApplyImpl(senderName, receiverName, intToRemoteFunction(funid, reason))
    }
  }

}

object MultiClassSpecificAvroSerializer {
  val cachedReaders = new ConcurrentHashMap[Class[_], SpecificDatumReader[GenericContainer]]
}

/** Simplest, naive serializer */
class MultiClassSpecificAvroSerializer 
  extends BaseAvroSerializer 
  with    IdResolvingSerializer {

  import MultiClassSpecificAvroSerializer._

  override val writeClassName = true

  override def writeBytes(container: GenericContainer, outputStream: OutputStream) {
    val writer  = new GenericDatumWriter[GenericContainer](container.getSchema)
    val encoder = new BinaryEncoder(outputStream)
    writer.write(container, encoder) 
  }

  private val decoderFactory = new DecoderFactory

  override def readBytes(clz: Option[Class[_]], inputStream: InputStream) = {
    @inline def doRead(newInstance: GenericContainer, reader: SpecificDatumReader[GenericContainer]) = {
      val decoder = decoderFactory.createBinaryDecoder(inputStream, null)
      reader.read(newInstance, decoder)
      newInstance
    }
    val typedClass = clz.get.asInstanceOf[Class[GenericContainer]]
    val reader     = cachedReaders.get(typedClass)
    if (reader ne null) 
      doRead(typedClass.newInstance, reader)
    else {
      val newInstance = typedClass.newInstance
      val reader = new ScalaSpecificClassDatumReader[GenericContainer](newInstance.getSchema, typedClass)
      cachedReaders.putIfAbsent(typedClass, reader)
      doRead(newInstance, reader)
    }
  }

  override val uniqueId = 3761204115L
}
