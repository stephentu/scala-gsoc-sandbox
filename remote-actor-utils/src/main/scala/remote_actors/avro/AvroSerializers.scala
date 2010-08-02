package remote_actors.avro 

import java.io._

import scala.reflect.Manifest

import org.apache.avro.{ io, specific, Schema }
import io._
import specific._

import com.googlecode.avro.marker._
import com.googlecode.avro.annotation._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import scala.collection.mutable.HashMap

import java.math.BigInteger
import java.security.MessageDigest

abstract class HasMultiClassAvroSerializer extends Configuration { 
  override def newSerializer() = new MultiClassSpecificAvroSerializer
}

abstract class HasSingleClassAvroSerializer[R <: SpecificRecord : Manifest] extends Configuration { 
  override def newSerializer() = new SingleClassClientSpecificAvroSerializer[R]
}

@AvroUnion sealed trait AvroMessage

/** Avro version of internal classes */
case class AvroContainer(var msg: AvroMessage) extends AvroRecord

case class AvroAsyncSend(var _senderName: Option[String],
                         var _receiverName: String,
                         var _metaData: Option[Array[Byte]],
                         var _data: Array[Byte]) extends AsyncSend 
                                                 with    AvroMessage
                                                 with    AvroRecord {
  override def senderName   = _senderName.map(s => Symbol(s))
  override def receiverName = Symbol(_receiverName)
  override def metaData     = _metaData.orNull
  override def data         = _data
}


case class AvroSyncSend(var _senderName: String,
                        var _receiverName: String,
                        var _metaData: Option[Array[Byte]],
                        var _data: Array[Byte],
                        var _session: String) extends SyncSend 
                                              with    AvroMessage
                                              with    AvroRecord {
  override def senderName   = Symbol(_senderName)
  override def receiverName = Symbol(_receiverName)
  override def metaData     = _metaData.orNull
  override def data         = _data
  override def session      = Symbol(_session)
}

case class AvroSyncReply(var _receiverName: String,
                         var _metaData: Option[Array[Byte]],
                         var _data: Array[Byte],
                         var _session: String) extends SyncReply 
                                               with    AvroMessage
                                               with    AvroRecord {
  override def receiverName = Symbol(_receiverName)
  override def metaData     = _metaData.orNull
  override def data         = _data
  override def session      = Symbol(_session)
}

case class AvroRemoteStartInvoke(var _actorClass: String) extends RemoteStartInvoke 
                                                          with    AvroMessage 
                                                          with    AvroRecord {
  override def actorClass = _actorClass
}

case class AvroRemoteStartInvokeAndListen(var _actorClass: String,
                                          var _port: Int,
                                          var _name: String) 
  extends RemoteStartInvokeAndListen 
  with    AvroMessage
  with    AvroRecord {
  
  override def actorClass = _actorClass
  override def port       = _port
  override def name       = Symbol(_name)
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

case class AvroRemoteApply(var _senderName: String,
                           var _receiverName: String,
                           var _function: Int,
                           var _reason: Option[String]) extends RemoteApply with AvroRecord {
  import AvroRemoteFunction._
  override def senderName   = Symbol(_senderName)
  override def receiverName = Symbol(_receiverName)
  override def function    = intToRemoteFunction(_function, _reason) 
}

class ScalaSpecificDatumReader[T](schema: Schema)(implicit m: Manifest[T]) 
  extends SpecificDatumReader[T](schema) {
  private val clz = m.erasure.asInstanceOf[Class[T]]
  override def newRecord(old: AnyRef, schema: Schema): AnyRef = {
    if ((old ne null) && clz.isInstance(old)) old
    else super.newRecord(old, schema)
  }
}

class ScalaSpecificClassDatumReader[T](schema: Schema, clz: Class[T]) extends SpecificDatumReader[T](schema) {
  override def newRecord(old: AnyRef, schema: Schema): AnyRef = {
    if ((old ne null) && clz.isInstance(old)) old
    else super.newRecord(old, schema)
  }
}


trait AvroEnvelopeMessageCreator { this: Serializer => 
  override type MyAsyncSend   = AvroAsyncSend
  override type MySyncSend    = AvroSyncSend
  override type MySyncReply   = AvroSyncReply
  override type MyRemoteApply = AvroRemoteApply

  override def newAsyncSend(senderName: Option[Symbol], receiverName: Symbol, metaData: Array[Byte], data: Array[Byte]): AvroAsyncSend =
    AvroAsyncSend(senderName.map(_.name), receiverName.name, Option(metaData), data)

  override def newSyncSend(senderName: Symbol, receiverName: Symbol, metaData: Array[Byte], data: Array[Byte], session: Symbol): AvroSyncSend =
    AvroSyncSend(senderName.name, receiverName.name, Option(metaData), data, session.name)
                                                                                   
  override def newSyncReply(receiverName: Symbol, metaData: Array[Byte], data: Array[Byte], session: Symbol): AvroSyncReply = 
    AvroSyncReply(receiverName.name, Option(metaData), data, session.name)

  override def newRemoteApply(senderName: Symbol, receiverName: Symbol, rfun: RemoteFunction): AvroRemoteApply = {
    import AvroRemoteFunction._
    val (funid, reason) = remoteFunctionToInt(rfun)
    AvroRemoteApply(senderName.name, receiverName.name, funid, reason)
  }
}

trait AvroControllerMessageCreator { this: Serializer =>
  override type MyRemoteStartInvoke          = AvroRemoteStartInvoke
  override type MyRemoteStartInvokeAndListen = AvroRemoteStartInvokeAndListen

  override def newRemoteStartInvoke(actorClass: String): AvroRemoteStartInvoke = 
    AvroRemoteStartInvoke(actorClass)

  override def newRemoteStartInvokeAndListen(actorClass: String, port: Int, name: Symbol): AvroRemoteStartInvokeAndListen =
    AvroRemoteStartInvokeAndListen(actorClass, port, name.name)
}

object BasicSpecificAvroSerializer {
  final val AvroMessageClz = classOf[AvroMessage]
}

abstract class BasicSpecificAvroSerializer 
  extends Serializer
  with    AvroEnvelopeMessageCreator
  with    AvroControllerMessageCreator {

  import BasicSpecificAvroSerializer._

  override def serializeMetaData(message: AnyRef): Option[Array[Byte]] = message match {
    case _: AvroMessage => None
    case _              => Some(serializeClassName(message))
  }

  protected def doSerialize(message: SpecificRecord): Array[Byte]

  override final def serialize(message: AnyRef): Array[Byte] = message match {
    case a: AvroMessage    => toBytes(AvroContainer(a))
    case s: SpecificRecord => doSerialize(s)
    case _ => throw new IllegalArgumentException("Cannot serialize message: " + message)
  }

  protected def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) => 
      decodeClassName(bytes).getOrElse(throw new IllegalArgumentException("Could not interpret metdata"))
    case None        => 
      // if no metadata given, its of type AvroMessage
      AvroMessageClz
  }

  protected def decodeClassName(name: String): Option[Class[_]] = {
    try {
      Some(Class.forName(name))
    } catch {
      case e: ClassNotFoundException =>
        Debug.error(this + ": could not find class: " + name)
        None
    }
  }

  protected def decodeClassName(bytes: Array[Byte]): Option[Class[_]] =
    decodeClassName(new String(bytes))

  protected def doDeserialize(clz: Class[SpecificRecord], message: Array[Byte]): AnyRef

  override final def deserialize(metaData: Option[Array[Byte]], data: Array[Byte]): AnyRef = 
    handleMetaData(metaData) match {
      case AvroMessageClz =>
        val c = fromBytes[AvroContainer](data, classOf[AvroContainer])
        c.msg
      case e if (classOf[SpecificRecord].isAssignableFrom(e)) =>
        doDeserialize(e.asInstanceOf[Class[SpecificRecord]], data)
      case e =>
        throw new IllegalArgumentException("Don't know how to deserialize message of class " + e.getName)
    }


  protected def toBytes[T <: SpecificRecord](record: T): Array[Byte] = {
    val writer  = new SpecificDatumWriter[T](record.getSchema)
    val buffer  = new ByteArrayOutputStream(1024)
    val encoder = new BinaryEncoder(buffer)
    writer.write(record, encoder) 
    buffer.toByteArray
  }

  protected def fromBytes[T <: SpecificRecord](message: Array[Byte], srClz: Class[T])(implicit m: Manifest[T]): T = {
    val newInstance    = srClz.newInstance
    val decoderFactory = new DecoderFactory
    val reader         = new ScalaSpecificDatumReader[T](newInstance.getSchema)
    val inStream       = decoderFactory.createBinaryDecoder(message, null)
    reader.read(newInstance, inStream)
    newInstance
  }
}

/** Simplest, naive serializer */
class MultiClassSpecificAvroSerializer 
  extends BasicSpecificAvroSerializer 
  with    IdResolvingSerializer {

  override def uniqueId = 3761204115L

  override def doSerialize(message: SpecificRecord): Array[Byte] = toBytes(message)

  override def doDeserialize(clz: Class[SpecificRecord], message: Array[Byte]): AnyRef =
    fromBytes[SpecificRecord](message, clz)

}

case class HandshakeRequest(var clientHash: Array[Byte], 
                            var clientProtocol: Option[String],
                            var serverHash: Array[Byte],
                            var className: String) extends AvroRecord

object HandshakeResponse {
  final val BOTH   = 0x1
  final val CLIENT = 0x2
  final val NONE   = 0x3
}

case class HandshakeResponse(var _match: Int,
                             var serverProtocol: Option[String],
                             var serverHash: Option[Array[Byte]]) extends AvroRecord

object SingleClassSpecificAvroSerializer {
  /** ResolvingDecoder.resolve is NOT threadsafe... */
  final val resolvingDecoderLock = new Object

  final val cache = new HashMap[Class[_], (Array[Byte], AnyRef)]

  final val FlagMessage = Array[Byte](1) 
}

abstract class SingleClassSpecificAvroSerializer extends BasicSpecificAvroSerializer {
  import SingleClassSpecificAvroSerializer._

  override def isHandshaking = true

  protected var node: Option[Node] = None

  protected def md5AsString(s: String): String = {
    val i = new BigInteger(1, md5(s))     
    String.format("%1$032x", i)             
  }
  protected def md5(s: String): Array[Byte] = {
    val m = MessageDigest.getInstance("MD5")
    val b = s.getBytes                      
    m.update(b, 0, b.length)                
    val ret = m.digest
    assert(ret.length == 16, "MD5 digest did not return 16 bytes length array")
    ret
  }

  protected def tryDecode[R <: SpecificRecord](b: Array[Byte])(implicit m: Manifest[R]): Option[R] = {
    try {
      Some(fromBytes[R](b, m.erasure.asInstanceOf[Class[R]]))
    } catch {
      case e: Exception =>
        Debug.error(this + ": caught exception: " + e.getMessage)
        Debug.doError { e.printStackTrace() }
        None
    }
  }

  protected def tryDecodeResponse(b: Array[Byte]): Option[HandshakeResponse] = 
    tryDecode[HandshakeResponse](b)
  protected def tryDecodeRequest(b: Array[Byte]): Option[HandshakeRequest] =
    tryDecode[HandshakeRequest](b)

  protected def tryResolve(mySchema: Schema, theirSchema: Schema): Option[AnyRef] = {
    try {
      resolvingDecoderLock.synchronized {
        Some(ResolvingDecoder.resolve(theirSchema, mySchema)) /** THEY are the writer, I am the reader */
      }
    } catch {
      case e: Exception =>
        Debug.error(this + ": caught exception when trying to resolve schema: " + e.getMessage)
        Debug.doError { e.printStackTrace() }
        None
    }
  }

  protected def tryResolveAndCache(mySchema: Schema, myRecordClass: Class[_], remoteProtocol: String, remoteHash: Array[Byte]): Option[AnyRef] = {
    val retVal = tryResolve(mySchema, Schema.parse(remoteProtocol)) 
    retVal.foreach(obj => cache.synchronized {
      cache += myRecordClass -> (remoteHash, obj)
    })
    retVal
  }

  protected def hashEquals(a: Array[Byte], b: Array[Byte]) =
    java.util.Arrays.equals(a, b)

}

class SingleClassServerSpecificAvroSerializer
  extends SingleClassSpecificAvroSerializer {

  override def uniqueId = 7297271L

  import BasicSpecificAvroSerializer._
  import SingleClassSpecificAvroSerializer._
  import HandshakeResponse._

  private var activeCachedObj: Option[AnyRef] = None

  private var RecordClass: Class[SpecificRecord] = _

  private lazy val Schema         = RecordClass.newInstance.getSchema
  private lazy val ServerProtocol = Schema.toString
  private lazy val ServerHash     = md5(ServerProtocol)

  override def bootstrapClassName = 
    throw new IllegalStateException("bootstrapClassName should never be called on the server side")

  override def handleNextEvent: PartialFunction[ReceivableEvent, Option[TriggerableEvent]] = {
    case StartEvent(n) =>
      node = Some(n)
      None
    case RecvEvent(b) => 
      b match {
        case bytes: Array[Byte] =>
          tryDecodeRequest(bytes) match {
            case Some(HandshakeRequest(clientHash, None, serverHash, className)) =>
              decodeClassName(className) match {
                case Some(clz) if (classOf[SpecificRecord].isAssignableFrom(clz))  =>
                  RecordClass = clz.asInstanceOf[Class[SpecificRecord]]
                  // check to see if client sent a good hash of the server
                  val hashEq = hashEquals(ServerHash, serverHash)
                  val serverAware = hashEq || cache.synchronized {
                    cache.get(clz) match {
                      case Some((clientHash, clientObj)) =>
                        activeCachedObj = Some(clientObj)
                        true
                      case None => false
                    }
                  }
                  if (hashEq) /** Both sides speaking the same schema */
                    Some(SendWithSuccessEvent(toBytes(HandshakeResponse(BOTH, None, None))))
                  else if (!hashEq && serverAware)
                    /** Client not aware of server's schema, but server aware
                     * of client's schema */
                    Some(SendWithSuccessEvent(toBytes(HandshakeResponse(CLIENT, Some(ServerProtocol), Some(ServerHash)))))
                  else
                    /** Both sides unware of each other */
                    Some(SendEvent(toBytes(HandshakeResponse(NONE, Some(ServerProtocol), Some(ServerHash)))))
                case Some(_) =>
                  Some(Error("Remote side send non SpecificRecord class name: " + className))
                case None =>
                  Some(Error("Remote side send improper class name: " + className))
              }
            case Some(HandshakeRequest(clientHash, Some(clientProtocol), serverHash, className)) =>
              assert(RecordClass ne null)
              activeCachedObj = tryResolveAndCache(Schema, RecordClass, clientProtocol, clientHash)
              activeCachedObj.map(_ => SendEvent(toBytes(HandshakeResponse(BOTH, None, None)))).orElse(Some(Error("Remote side's schema unresolvable with this schema")))
            case None =>
              Some(Error("Remote side did not send a proper HandshakeRequest"))
          }
        case _ =>
          Some(Error("Remote side did not send a byte array back"))
      }
  }

  override def doSerialize(message: SpecificRecord) = message.getClass match {
    case _ if (message.getClass == RecordClass) => toBytes(message)
    case e => throw new IllegalArgumentException("Don't know how to serializer message " + e + " of class " + e.getName)
  }

  private def fromBytesResolving(message: Array[Byte]): SpecificRecord = {
    val cachedResolverObj = activeCachedObj.get
    val newInstance       = RecordClass.newInstance
    val reader            = new ScalaSpecificClassDatumReader[SpecificRecord](newInstance.getSchema, RecordClass)
    val decoderFactory    = new DecoderFactory
    val inStream          = decoderFactory.createBinaryDecoder(message, null)
    val resolvingDecoder  = new ResolvingDecoder(cachedResolverObj, inStream)
    reader.read(newInstance, resolvingDecoder)
    newInstance
  }


  override def serializeMetaData(message: AnyRef) = message match {
    case _: AvroMessage => None
    case _              => Some(FlagMessage)
  }

  override def handleMetaData(metaData: Option[Array[Byte]]) = metaData match {
    case Some(b) if (java.util.Arrays.equals(b, FlagMessage)) =>
      RecordClass
    case Some(_) =>
      throw new IllegalArgumentException("Invalid metaData: " + metaData)
    case None =>
      AvroMessageClz
  }

  override def doDeserialize(clz: Class[SpecificRecord], message: Array[Byte]): AnyRef = clz match {
    case _ if (clz == RecordClass) => 
      activeCachedObj.map(_ => fromBytesResolving(message)).getOrElse(fromBytes(message, RecordClass))
    case _ => 
      throw new IllegalArgumentException("Don't know how to deserialize message of class " + clz.getName)
  }

}

class SingleClassClientSpecificAvroSerializer[R <: SpecificRecord](implicit m: Manifest[R]) 
  extends SingleClassSpecificAvroSerializer {

  import BasicSpecificAvroSerializer._
  import SingleClassSpecificAvroSerializer._
  import HandshakeResponse._

  private val RecordClass         = m.erasure.asInstanceOf[Class[R]]
  private val schema              = RecordClass.newInstance.getSchema
  private lazy val clientProtocol = schema.toString
  private lazy val clientHash     = md5(clientProtocol)

  private var activeCachedObj: Option[AnyRef] = None
  private var activeServerHash: Option[Array[Byte]] = None

  override def uniqueId = 987643972L

  override def bootstrapClassName = classOf[SingleClassServerSpecificAvroSerializer].getName

  override def handleNextEvent: PartialFunction[ReceivableEvent, Option[TriggerableEvent]] = {
    case StartEvent(n) =>
      node = Some(n)
      cache.synchronized {
        cache.get(RecordClass) match {
          case Some((serverHash, cachedObj)) =>
            // and we have saved data for this class
            activeCachedObj  = Some(cachedObj)
            activeServerHash = Some(serverHash)
            Some(SendEvent(toBytes(HandshakeRequest(clientHash, None, serverHash, RecordClass.getName))))
          case None =>
            // no saved data for this server and this class 
            Some(SendEvent(toBytes(HandshakeRequest(clientHash, None, clientHash, RecordClass.getName))))
        }
      }
    case RecvEvent(b) =>
      b match {
        case bytes: Array[Byte] =>
          tryDecodeResponse(bytes) match {
            case Some(HandshakeResponse(BOTH, _, _)) =>
              Some(Success)
            case Some(HandshakeResponse(CLIENT, Some(serverProtocol), Some(serverHash))) =>
              // do resolution with the server protocol, and cache
              activeCachedObj = tryResolveAndCache(schema, RecordClass, serverProtocol, serverHash)
              activeCachedObj
                .map(_ => Success)
                .orElse(Some(Error("Remote side's schema unresolvable with this schema")))
            case Some(HandshakeResponse(CLIENT, _, _)) =>
              Some(Error("Remote side sent an improper HandshakeResponse with CLIENT match back"))
            case Some(HandshakeResponse(NONE, Some(serverProtocol), Some(serverHash))) =>
              activeCachedObj = tryResolveAndCache(schema, RecordClass, serverProtocol, serverHash)
              activeCachedObj
                .map(_ => SendEvent(toBytes(HandshakeRequest(clientHash, Some(clientProtocol), serverHash, RecordClass.getName))))
                .orElse(Some(Error("Remote side's schema unresolvable with this schema")))
            case Some(HandshakeResponse(NONE, _, _)) =>
              Some(SendEvent(toBytes(HandshakeRequest(clientHash, Some(clientProtocol), activeServerHash.getOrElse(clientHash), RecordClass.getName))))
            case Some(r @ HandshakeResponse(_, _, _)) =>
              Some(Error("Invalid handshake response: " + r))
            case None =>
              Some(Error("Remote side did not send a proper HandshakeResponse back"))
          }
        case _ =>
          Some(Error("Remote side did not send a byte array back"))
      }
  }


  override def doSerialize(message: SpecificRecord) = message.getClass match {
    case RecordClass => toBytes(message.asInstanceOf[R])
    case e => throw new IllegalArgumentException("Don't know how to serializer message " + e + " of class " + e.getName)
  }

  private def fromBytesResolving(message: Array[Byte]): R = {
    val cachedResolverObj = activeCachedObj.get
    val newInstance       = RecordClass.newInstance
    val reader            = new ScalaSpecificDatumReader[R](newInstance.getSchema)
    val decoderFactory    = new DecoderFactory
    val inStream          = decoderFactory.createBinaryDecoder(message, null)
    val resolvingDecoder  = new ResolvingDecoder(cachedResolverObj, inStream)
    reader.read(newInstance, resolvingDecoder)
    newInstance
  }


  override def serializeMetaData(message: AnyRef) = message match {
    case _: AvroMessage => None
    case _              => Some(FlagMessage)
  }

  override def handleMetaData(metaData: Option[Array[Byte]]) = metaData match {
    case Some(b) if (java.util.Arrays.equals(b, FlagMessage)) =>
      RecordClass
    case Some(_) =>
      throw new IllegalArgumentException("Invalid metaData: " + metaData)
    case None =>
      AvroMessageClz
  }

  override def doDeserialize(clz: Class[SpecificRecord], message: Array[Byte]): AnyRef = clz match {
    case _ if (clz == RecordClass) => 
      activeCachedObj.map(_ => fromBytesResolving(message)).getOrElse(fromBytes[R](message, RecordClass))
    case _ => 
      throw new IllegalArgumentException("Don't know how to deserialize message of class " + clz.getName)
  }

}
