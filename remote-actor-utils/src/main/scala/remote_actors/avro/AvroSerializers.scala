package remote_actors.avro 

import java.io._

import scala.reflect.Manifest

import org.apache.avro.{ io, specific, Schema }
import io._
import specific._

import com.googlecode.avro.marker._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

/** Avro version of internal classes */
case class AvroNode(var _address: String, var _port: Int) extends Node with AvroRecord {
  override def address = _address
  override def port    = _port

  override def newNode(a: String, p: Int) = AvroNode(a, p)
}

case class AvroLocator(var _node: AvroNode, var _name: String) extends Locator with AvroRecord {
  override def node = _node
  override def name = Symbol(_name)
}

case class AvroNamedSend(var _senderLoc: AvroLocator,
                         var _receiverLoc: AvroLocator,
                         var _metaData: Option[Array[Byte]],
                         var _data: Array[Byte],
                         var _session: String) extends NamedSend with AvroRecord {
  override def senderLoc   = _senderLoc
  override def receiverLoc = _receiverLoc
  override def metaData    = _metaData.getOrElse(null)
  override def data        = _data
  override def session     = Symbol(_session)
}

object AvroServiceMode {
  // substitute for the ServiceMode enum
  val Blocking    = 0
  val NonBlocking = 1
  implicit def intToMode(i: Int): ServiceMode.Value = i match {
    case Blocking    => ServiceMode.Blocking
    case NonBlocking => ServiceMode.NonBlocking
  }
  implicit def modeToInt(m: ServiceMode.Value): Int = m match {
    case ServiceMode.Blocking    => Blocking
    case ServiceMode.NonBlocking => NonBlocking
  }
}

case class AvroProxy(var _remoteNode: AvroNode, 
                     var _mode: Int,
                     var _serializerClassName: String,
                     var _name: String) extends Proxy with AvroRecord {
  import AvroServiceMode._

  override def remoteNode          = _remoteNode
  override def mode                = _mode
  override def serializerClassName = _serializerClassName
  override def name                = Symbol(_name)
}

case class AvroRemoteStartInvoke(var _actorClass: String) 
  extends RemoteStartInvoke with AvroRecord {
  override def actorClass = _actorClass
}

case class AvroRemoteStartInvokeAndListen(var _actorClass: String,
                                          var _port: Int,
                                          var _name: String,
                                          var _mode: Int) 
  extends RemoteStartInvokeAndListen with AvroRecord {
  import AvroServiceMode._
  
  override def actorClass = _actorClass
  override def port       = _port
  override def name       = Symbol(_name)
  override def mode       = _mode
}

class ScalaSpecificDatumReader[T](schema: Schema)(implicit m: Manifest[T]) extends SpecificDatumReader[T](schema) {
  private val clz = m.erasure.asInstanceOf[Class[T]]
  override def newRecord(old: AnyRef, schema: Schema): AnyRef = {
    if ((old ne null) && clz.isInstance(old)) old
    else super.newRecord(old, schema)
  }
}

trait AvroEnvelopeMessageCreator { this: Serializer[Proxy] => 
  override type MyNode = AvroNode
  override type MyNamedSend = AvroNamedSend
  override type MyLocator = AvroLocator

  override def newNode(address: String, port: Int): AvroNode = AvroNode(address, port)

  override def newNamedSend(senderLoc: AvroLocator, receiverLoc: AvroLocator, metaData: Array[Byte], data: Array[Byte], session: Symbol): AvroNamedSend =
    AvroNamedSend(senderLoc, receiverLoc, if (metaData eq null) None else Some(metaData), data, session.name)

  override def newLocator(node: AvroNode, name: Symbol): AvroLocator =
    AvroLocator(node, name.name)

}

trait AvroControllerMessageCreator { this: Serializer[Proxy] =>
  override type MyRemoteStartInvoke = AvroRemoteStartInvoke
  override type MyRemoteStartInvokeAndListen = AvroRemoteStartInvokeAndListen

  import AvroServiceMode._

  override def newRemoteStartInvoke(actorClass: String): AvroRemoteStartInvoke = 
    AvroRemoteStartInvoke(actorClass)

  override def newRemoteStartInvokeAndListen(actorClass: String, port: Int, name: Symbol, mode: ServiceMode.Value): AvroRemoteStartInvokeAndListen =
    AvroRemoteStartInvokeAndListen(actorClass, port, name.name, mode)
}

abstract class BasicSpecificAvroSerializer 
  extends Serializer[AvroProxy]
  with    AvroEnvelopeMessageCreator
  with    AvroControllerMessageCreator {

  override def serializeMetaData(message: AnyRef): Option[Array[Byte]] = Some(serializeClassName(message))

  protected def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) => Class.forName(new String(bytes))
    case None        => throw new IllegalArgumentException("avro serializer requires class name in metadata")
  }

  protected def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef

  override final def deserialize(metaData: Option[Array[Byte]], data: Array[Byte]): AnyRef = 
    doDeserialize(handleMetaData(metaData), data)

  protected def toBytes[T <: SpecificRecord](record: T): Array[Byte] = {
    val writer = new SpecificDatumWriter[T](record.getSchema)
    val buffer = new ByteArrayOutputStream(1024)
    val encoder = new BinaryEncoder(buffer)
    writer.write(record, encoder) 
    buffer.toByteArray
  }

  protected def fromBytes[T <: SpecificRecord](message: Array[Byte], srClz: Class[T])(implicit m: Manifest[T]): T = {
    val newInstance = srClz.newInstance
    val decoderFactory = new DecoderFactory
    val reader = new ScalaSpecificDatumReader[T](newInstance.getSchema)
    val inStream = decoderFactory.createBinaryDecoder(message, null)
    reader.read(newInstance, inStream)
    newInstance
  }

  override def newProxy(remoteNode: AvroNode, mode: ServiceMode.Value, serializerClassName: String, name: Symbol): AvroProxy =
    AvroProxy(remoteNode, AvroServiceMode.modeToInt(mode), serializerClassName, name.name)

}

/** Simplest, naive serializer */
class MultiClassSpecificAvroSerializer 
  extends BasicSpecificAvroSerializer 
  with    IdResolvingSerializer {

  override def uniqueId = 3761204115L

  override def serialize(message: AnyRef): Array[Byte] = message match {
    case record: SpecificRecord => toBytes(record)
    case e => throw new IllegalArgumentException("Don't know how to serializer message " + e + " of class " + e.getClass.getName)
  }

  override def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef = {
    if (classOf[SpecificRecord].isAssignableFrom(clz)) 
      fromBytes[SpecificRecord](message, clz.asInstanceOf[Class[SpecificRecord]])
    else 
      throw new IllegalArgumentException("Don't know how to deserialize message of class " + clz.getName)
  }

}

object SingleClassSpecificAvroSerializer {
  /** ResolvingDecoder.resolve is NOT threadsafe... */
  val resolvingDecoderLock = new Object

  case object SendMySchema
  case object ExpectOtherSchema

  val AvroNodeClass = classOf[AvroNode]
  val AvroLocatorClass = classOf[AvroLocator]
  val AvroNamedSendClass = classOf[AvroNamedSend]
  val AvroProxyClass = classOf[AvroProxy]
}

abstract class SingleClassSpecificAvroSerializer[R <: SpecificRecord](implicit m: Manifest[R]) 
  extends BasicSpecificAvroSerializer {
  import SingleClassSpecificAvroSerializer._

  private val RecordClass = m.erasure.asInstanceOf[Class[R]]
  private val schema = RecordClass.newInstance.getSchema
  private var cachedResolverObj: AnyRef = _

  override def initialState: Option[Any] = Some(SendMySchema)

  override def nextHandshakeMessage = {
    case SendMySchema => (ExpectOtherSchema, Some(schema.toString))
    case Resolved     => (Resolved, None)
  }

  override def handleHandshakeMessage = {
    case (ExpectOtherSchema, otherSchemaJson: String) =>
      println("trying to resolve schema: " + otherSchemaJson)
      val otherSchema = Schema.parse(otherSchemaJson)
      println("parsed other schema")
      resolvingDecoderLock.synchronized {
        cachedResolverObj = ResolvingDecoder.resolve(otherSchema, schema)
      }
      println("resolved other schema")
      Resolved
  }

  override def serialize(message: AnyRef): Array[Byte] = message.getClass match {
    case RecordClass => toBytes(message.asInstanceOf[R])
    case AvroNodeClass | AvroLocatorClass | AvroNamedSendClass | AvroProxyClass => // special cases
      toBytes(message.asInstanceOf[SpecificRecord])
    case e => throw new IllegalArgumentException("Don't know how to serializer message " + e + " of class " + e.getName)
  }

  private def fromBytesResolving(message: Array[Byte]): R = {
    if (cachedResolverObj eq null)
      throw new IllegalStateException("serializer has not been initialized with handshake")
    val newInstance = RecordClass.newInstance
    val reader = new ScalaSpecificDatumReader[R](newInstance.getSchema)
    val decoderFactory = new DecoderFactory
    val inStream = decoderFactory.createBinaryDecoder(message, null)
    val resolvingDecoder = new ResolvingDecoder(cachedResolverObj, inStream)
    reader.read(newInstance, resolvingDecoder)
    newInstance
  }

  override def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) => 
      val clzName = new String(bytes)
      if (clzName.endsWith(schema.getName)) // allow the remote side to hold the record in a different class
        RecordClass
      else
        Class.forName(clzName)
    case None        => throw new IllegalArgumentException("avro serializer requires class name in metadata")
  }

  override def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef = clz match {
    case RecordClass => fromBytesResolving(message)
    case AvroNodeClass | AvroLocatorClass | AvroNamedSendClass | AvroProxyClass => 
      fromBytes(message, clz.asInstanceOf[Class[SpecificRecord]])
    case _ => 
      throw new IllegalArgumentException("Don't know how to deserialize message of class " + clz.getName)
  }

}
