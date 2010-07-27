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
      .setSession(namedSend.session.map(_.name).orNull)
      .build
  implicit def fromPBufNamedSend(pbufNamedSend: PBufNamedSend): NamedSend = 
    NamedSend(pbufNamedSend.getSenderLoc, 
              pbufNamedSend.getReceiverLoc,
              pbufNamedSend.getMetaData,
              pbufNamedSend.getData,
              Option(pbufNamedSend.getSession))
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
	implicit def fromPBufRemoteStartInvoke(pbufRsi: PBufRemoteStartInvoke): RemoteStartInvoke = 
		RemoteStartInvoke(pbufRsi.getActorClass)
	implicit def toPBufRemoteStartInvoke(rsi: RemoteStartInvoke): PBufRemoteStartInvoke = 
		PBufRemoteStartInvoke.newBuilder
			.setActorClass(rsi.actorClass)
			.build
	implicit def fromPBufRemoteStartInvokeAndListen(pbufRsil: PBufRemoteStartInvokeAndListen): RemoteStartInvokeAndListen = 
		RemoteStartInvokeAndListen(pbufRsil.getActorClass,
															 pbufRsil.getPort,
															 pbufRsil.getName,
															 pbufRsil.getMode)
	implicit def toPBufRemoteStartInvokeAndListen(rsil: RemoteStartInvokeAndListen): PBufRemoteStartInvokeAndListen = 
		PBufRemoteStartInvokeAndListen.newBuilder
			.setActorClass(rsil.actorClass)
			.setPort(rsil.port)
		  .setName(rsil.name)
			.setMode(rsil.mode)
			.build

  val NodeClass      = classOf[Node]
  val LocatorClass   = classOf[Locator]
  val NamedSendClass = classOf[NamedSend]
  val ProxyClass     = classOf[Proxy]

  val PBufNodeClass                       = classOf[PBufNode]
  val PBufLocatorClass                    = classOf[PBufLocator]
  val PBufNamedSendClass                  = classOf[PBufNamedSend]
  val PBufProxyClass                      = classOf[PBufProxy]
  val PBufRemoteStartInvokeClass          = classOf[PBufRemoteStartInvoke]
  val PBufRemoteStartInvokeAndListenClass = classOf[PBufRemoteStartInvokeAndListen]
}


class ProtobufSerializer 
  extends Serializer[DefaultProxyImpl]
  with    IdResolvingSerializer
  with    DefaultEnvelopeMessageCreator
  with    DefaultControllerMessageCreator
  with    DefaultProxyCreator {

  import PBufInternalConverters._
  private val MessageClass = classOf[Message]

  override def uniqueId = 894256467L

  override def serializeMetaData(message: AnyRef): Option[Array[Byte]] = {
    val c = message match {
      case m: Message                    => m.getClass.getName
      case _: Node                       => PBufNodeClass.getName
      case _: Locator                    => PBufLocatorClass.getName
      case _: NamedSend                  => PBufNamedSendClass.getName
      case _: Proxy                      => PBufProxyClass.getName
      case _: RemoteStartInvokeAndListen => PBufRemoteStartInvokeAndListenClass.getName
      case _: RemoteStartInvoke          => PBufRemoteStartInvokeClass.getName
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
		case rsil: RemoteStartInvokeAndListen =>
			toPBufRemoteStartInvokeAndListen(rsil).toByteArray
		case rsi: RemoteStartInvoke =>
			toPBufRemoteStartInvoke(rsi).toByteArray
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
		case PBufRemoteStartInvokeClass =>
			fromPBufRemoteStartInvoke(PBufRemoteStartInvoke.parseFrom(message))
		case PBufRemoteStartInvokeAndListenClass =>
			fromPBufRemoteStartInvokeAndListen(PBufRemoteStartInvokeAndListen.parseFrom(message))
    case messageClz if (MessageClass.isAssignableFrom(messageClz)) =>
      // TODO: do this the "right way" for pbuf
      val parseFromMethod = clz.getDeclaredMethod("parseFrom", classOf[Array[Byte]])
      parseFromMethod.invoke(null, message)
  }

}
