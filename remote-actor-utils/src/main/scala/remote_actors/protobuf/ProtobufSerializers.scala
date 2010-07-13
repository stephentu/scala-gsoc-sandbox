package remote_actors.protobuf

import java.io._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import com.google.protobuf._
import gsoc_scala.RemoteActorsInternal
import RemoteActorsInternal._

object PBufInternalConverters {
  implicit def strToSymbol(s: String): Symbol = Symbol(s)
  implicit def symbolToStr(sym: Symbol): String = sym.toString.substring(1)
  implicit def byteArrayToByteString(bytes: Array[Byte]): ByteString = 
    ByteString.copyFrom(bytes)
  implicit def byteStringToByteArray(byteString: ByteString): Array[Byte] =
    byteString.toByteArray 
  implicit def toPBufNode(node: Node): PBufNode = 
    PBufNode.newBuilder
      .setAddress(node.address)
      .setPort(node.port)
      .build
  implicit def fromPBufNode(pbufNode: PBufNode): Node = 
    Node(pbufNode.getAddress, pbufNode.getPort)
  implicit def toPBufLocator(locator: Locator): PBufLocator = 
    PBufLocator.newBuilder
      .setNode(locator.node)
      .setName(locator.name)
      .build
  implicit def fromPBufLocator(pbufLocator: PBufLocator): Locator = 
    Locator(pbufLocator.getNode, pbufLocator.getName)
  implicit def toPBufNamedSend(namedSend: NamedSend): PBufNamedSend = 
    PBufNamedSend.newBuilder
      .setSenderLoc(namedSend.senderLoc)
      .setReceiverLoc(namedSend.receiverLoc)
      .setMetaData(namedSend.metaData)
      .setData(namedSend.data)
      .setSession(namedSend.session)
      .build
  implicit def fromPBufNamedSend(pbufNamedSend: PBufNamedSend): NamedSend = 
    NamedSend(pbufNamedSend.getSenderLoc, 
              pbufNamedSend.getReceiverLoc,
              pbufNamedSend.getMetaData,
              pbufNamedSend.getData,
              pbufNamedSend.getSession)
  val NodeClass = classOf[Node]
  val LocatorClass = classOf[Locator]
  val NamedSendClass = classOf[NamedSend]
}


class ProtobufSerializer extends IdResolvingSerializer {
  import PBufInternalConverters._
  private val MessageClass = classOf[Message]

  override def uniqueId = 894256467L

  override def serializeMetaData(message: AnyRef): Option[Array[Byte]] = Some(serializeClassName(message))

  override def serialize(o: AnyRef): Array[Byte] = o match {
    case message: Message =>
      message.toByteArray
    case node: Node =>
      toPBufNode(node).toByteArray
    case locator: Locator =>
      toPBufLocator(locator).toByteArray
    case namedSend: NamedSend =>
      toPBufNamedSend(namedSend).toByteArray
  }

  protected def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) => Class.forName(new String(bytes))
    case None        => throw new IllegalArgumentException("protobuf serializer requires class name in metadata")
  }

  override def deserialize(metaData: Option[Array[Byte]], message: Array[Byte]): AnyRef = {
    doDeserialize(handleMetaData(metaData), message)
  }

  protected def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef = 
    if (MessageClass.isAssignableFrom(clz)) {
      // gross usage of reflection. can we do better?
      val parseFromMethod = clz.getDeclaredMethod("parseFrom", classOf[Array[Byte]])
      parseFromMethod.invoke(null, message)
    } else clz match {
      case NodeClass =>
        fromPBufNode(PBufNode.parseFrom(message))
      case LocatorClass =>
        fromPBufLocator(PBufLocator.parseFrom(message))
      case NamedSendClass =>
        fromPBufNamedSend(PBufNamedSend.parseFrom(message))
    }

}
