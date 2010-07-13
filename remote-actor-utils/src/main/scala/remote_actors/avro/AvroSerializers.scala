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
case class AvroNode(var address: String, var port: Int) extends AvroRecord
case class AvroLocator(var node: AvroNode, var name: String) extends AvroRecord
case class AvroNamedSend(var senderLoc: AvroLocator,
                         var receiverLoc: AvroLocator,
                         var metaData: Option[Array[Byte]],
                         var data: Array[Byte],
                         var session: String) extends AvroRecord

object AvroInternalConverters {

  implicit def strToSymbol(s: String): Symbol = Symbol(s)
  implicit def symbolToStr(sym: Symbol): String = sym.toString.substring(1)
  implicit def toAvroNode(node: Node): AvroNode = AvroNode(node.address, node.port)
  implicit def fromAvroNode(avroNode: AvroNode): Node = Node(avroNode.address, avroNode.port)
  implicit def toAvroLocator(locator: Locator): AvroLocator = AvroLocator(locator.node, locator.name)
  implicit def fromAvroLocator(avroLocator: AvroLocator): Locator = Locator(avroLocator.node, avroLocator.name)
  implicit def toAvroNamedSend(namedSend: NamedSend): AvroNamedSend = 
    AvroNamedSend(namedSend.senderLoc, 
                  namedSend.receiverLoc, 
                  if (namedSend.metaData eq null) None else Some(namedSend.metaData), 
                  namedSend.data, 
                  namedSend.session)
  implicit def fromAvroNamedSend(avroNamedSend: AvroNamedSend): NamedSend = 
    NamedSend(avroNamedSend.senderLoc, 
              avroNamedSend.receiverLoc, 
              avroNamedSend.metaData.getOrElse(null), 
              avroNamedSend.data, 
              avroNamedSend.session)

  val NodeClass = classOf[Node]
  val LocatorClass = classOf[Locator]
  val NamedSendClass = classOf[NamedSend]

  val AvroNodeClass = classOf[AvroNode]
  val AvroLocatorClass = classOf[AvroLocator]
  val AvroNamedSendClass = classOf[AvroNamedSend]

}

class ScalaSpecificDatumReader[T](schema: Schema)(implicit m: Manifest[T]) extends SpecificDatumReader[T](schema) {
  private val clz = m.erasure.asInstanceOf[Class[T]]
  override def newRecord(old: AnyRef, schema: Schema): AnyRef = {
    if ((old ne null) && clz.isInstance(old)) old
    else super.newRecord(old, schema)
  }
}

abstract class BasicAvroSerializer extends IdResolvingSerializer {

  import AvroInternalConverters._

  override def serializeMetaData(message: AnyRef): Option[Array[Byte]] = Some(serializeClassName(message))

  override def serialize(message: AnyRef): Array[Byte] = message match {
    case node: Node =>
      toBytes(toAvroNode(node))
    case locator: Locator =>
      toBytes(toAvroLocator(locator))
    case namedSend: NamedSend =>
      toBytes(toAvroNamedSend(namedSend))
  }

  protected def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) => Class.forName(new String(bytes))
    case None        => throw new IllegalArgumentException("avro serializer requires class name in metadata")
  }

  override def deserialize(metaData: Option[Array[Byte]], message: Array[Byte]): AnyRef = {
    doDeserialize(handleMetaData(metaData), message)
  }

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

  protected def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef = clz match {
    case NodeClass => 
      fromAvroNode(fromBytes(message, AvroNodeClass))
    case LocatorClass => 
      fromAvroLocator(fromBytes(message, AvroLocatorClass))
    case NamedSendClass => 
      fromAvroNamedSend(fromBytes(message, AvroNamedSendClass))
  }

}


/** Simplest, naive serializer */
class MultiClassSpecificAvroSerializer extends BasicAvroSerializer {

  override def uniqueId = 3761204115L

  override def serialize(message: AnyRef): Array[Byte] = message match {
    case record: SpecificRecord =>
      toBytes(record)
    case _ => 
      super.serialize(message)
  }

  override def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef = {
    if (classOf[SpecificRecord].isAssignableFrom(clz)) 
      fromBytes[SpecificRecord](message, clz.asInstanceOf[Class[SpecificRecord]])
    else 
      super.doDeserialize(clz, message)
  }

}

object SingleClassSpecificAvroSerializer {
  /** ResolvingDecoder.resolve is NOT threadsafe... */
  val resolvingDecoderLock = new Object

  case object SendMySchema
  case object ExpectOtherSchema
}

abstract class SingleClassSpecificAvroSerializer[R <: SpecificRecord](implicit m: Manifest[R]) extends BasicAvroSerializer {
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
    case RecordClass =>
      toBytes(message.asInstanceOf[R])
    case _ => 
      super.serialize(message)
  }

  private def fromBytesResolving(message: Array[Byte]): R = {
    if (cachedResolverObj eq null)
      throw new Exception("serializer has not been initialized with handshake")
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
    case RecordClass =>
      fromBytesResolving(message)
    case _ =>
      super.doDeserialize(clz, message)
  }

}
