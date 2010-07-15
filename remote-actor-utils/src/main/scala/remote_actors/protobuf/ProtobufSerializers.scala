package remote_actors.protobuf

import java.io._

import scala.actors._
import Actor._
import remote._
import RemoteActor._

import com.google.protobuf._
import RemoteActorsInternal._

object PBufInternalConverters {
  implicit def strToSymbol(s: String): Symbol = Symbol(s)
  implicit def symbolToStr(sym: Symbol): String = sym.name
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
  implicit def toPBufProxy(proxy: Proxy): PBufProxy =
    PBufProxy.newBuilder
      .setRemoteNode(proxy.remoteNode)
      .setMode(proxy.mode)
      .setSerializerClassName(proxy.serializerClassName)
      .setName(proxy.name)
      .build
  implicit def fromPBufProxy(pbufProxy: PBufProxy): Proxy =
    new DefaultProxyImpl(pbufProxy.getRemoteNode,
                         pbufProxy.getMode,
                         pbufProxy.getSerializerClassName,
                         pbufProxy.getName)
  implicit def fromPBufServiceMode(pbufServiceMode: PBufServiceMode): ServiceMode.Value = pbufServiceMode match {
    case PBufServiceMode.BLOCKING => ServiceMode.Blocking
    case PBufServiceMode.NON_BLOCKING => ServiceMode.NonBlocking
  }
  implicit def toPBufServiceMode(serviceMode: ServiceMode.Value): PBufServiceMode = serviceMode match {
    case ServiceMode.Blocking => PBufServiceMode.BLOCKING
    case ServiceMode.NonBlocking => PBufServiceMode.NON_BLOCKING
  }
  val NodeClass = classOf[Node]
  val LocatorClass = classOf[Locator]
  val NamedSendClass = classOf[NamedSend]
  val ProxyClass = classOf[Proxy]

  val PBufNodeClass = classOf[PBufNode]
  val PBufLocatorClass = classOf[PBufLocator]
  val PBufNamedSendClass = classOf[PBufNamedSend]
  val PBufProxyClass = classOf[PBufProxy]
}


class ProtobufSerializer 
  extends Serializer[DefaultProxyImpl]
  with    IdResolvingSerializer
  with    DefaultEnvelopeMessageCreator
  with    DefaultProxyCreator {

  import PBufInternalConverters._
  private val MessageClass = classOf[Message]

  override def uniqueId = 894256467L

  override def serializeMetaData(message: AnyRef): Option[Array[Byte]] = {
    val c = message match {
      case m: Message => m.getClass.getName
      case n: Node => PBufNodeClass.getName
      case l: Locator => PBufLocatorClass.getName
      case n: NamedSend => PBufNamedSendClass.getName
      case p: Proxy => PBufProxyClass.getName
    }
    Some(c.getBytes)
  }

  override def serialize(o: AnyRef): Array[Byte] = o match {
    case message: Message =>
      message.toByteArray
    case node: Node =>
      toPBufNode(node).toByteArray
    case locator: Locator =>
      toPBufLocator(locator).toByteArray
    case namedSend: NamedSend =>
      toPBufNamedSend(namedSend).toByteArray
    case proxy: Proxy =>
      toPBufProxy(proxy).toByteArray
  }

  protected def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) => Class.forName(new String(bytes))
    case None        => throw new IllegalArgumentException("protobuf serializer requires class name in metadata")
  }

  override final def deserialize(metaData: Option[Array[Byte]], message: Array[Byte]): AnyRef = {
    doDeserialize(handleMetaData(metaData), message)
  }

  protected def doDeserialize(clz: Class[_], message: Array[Byte]): AnyRef = clz match {
    case PBufNodeClass =>
      fromPBufNode(PBufNode.parseFrom(message))
    case PBufLocatorClass =>
      fromPBufLocator(PBufLocator.parseFrom(message))
    case PBufNamedSendClass =>
      fromPBufNamedSend(PBufNamedSend.parseFrom(message))
    case PBufProxyClass =>
      fromPBufProxy(PBufProxy.parseFrom(message))
    case messageClz if (MessageClass.isAssignableFrom(messageClz)) =>
      // TODO: do this the "right way" for pbuf
      val parseFromMethod = clz.getDeclaredMethod("parseFrom", classOf[Array[Byte]])
      parseFromMethod.invoke(null, message)
  }

}
