package remote_actors.protobuf

import scala.actors.Debug
import scala.actors.remote._

import com.google.protobuf._
import RemoteActorsInternal._

object PBufInternalConverters {
  import PBufRemoteApply._
  import PBufRemoteFunctionType._
  implicit def strToSymbol(s: String): Symbol = Symbol(s)
  implicit def symbolToStr(sym: Symbol): String = sym.name
  implicit def byteArrayToByteString(bytes: Array[Byte]): ByteString =
    ByteString.copyFrom(bytes)
  implicit def byteStringToByteArray(byteString: ByteString): Array[Byte] =
    byteString.toByteArray 
  def typeToRemoteFunction(t: PBufRemoteFunctionType, r: Option[String]): RemoteFunction = t match {
    case LINK_FUN   => LinkToFun
    case UNLINK_FUN => UnlinkFromFun
    case EXIT_FUN   => ExitFun(r.getOrElse("No Reason"))
  }
  def remoteFunctionToTuple(r: RemoteFunction): (PBufRemoteFunctionType, Option[String]) = r match {
    case LinkToFun       => (LINK_FUN, None)
    case UnlinkFromFun   => (UNLINK_FUN, None)
    case ExitFun(reason) => (EXIT_FUN, Some(reason.toString))
  }
}

abstract class HasProtobufSerializer extends Configuration {
  override def newSerializer() = new ProtobufSerializer
}

class ProtobufSerializer 
  extends Serializer
  with    IdResolvingSerializer
  with    ProtobufControllerMessageCreator 
  with    ProtobufEnvelopeMessageCreator {

  import PBufSend.PBufSendType._

  override def uniqueId = 894256467L

  override def serializeMetaData(message: AnyRef) = 
    Some(serializeClassName(message))

  override def serialize(message: AnyRef) = message match {
    case m: Message => m.toByteArray
    case _ => throw new IllegalArgumentException("Cannot serialize message: " + message)
  }

  protected def handleMetaData(metaData: Option[Array[Byte]]): Class[_] = metaData match {
    case Some(bytes) =>
      decodeClassName(bytes).getOrElse(throw new IllegalArgumentException("Could not interpret metdata"))
    case None        =>
      throw new IllegalArgumentException("No metadata given")
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

  protected def parse[M <: Message](messageClz: Class[M], data: Array[Byte]): M = {
    // TODO: do this the "right way" for pbuf
    val parseFromMethod = messageClz.getDeclaredMethod("parseFrom", classOf[Array[Byte]])
    parseFromMethod.invoke(null, data).asInstanceOf[M]
  }

  private final val PBufSendClass                       = classOf[PBufSend]
  private final val PBufRemoteApplyClass                = classOf[PBufRemoteApply]
  private final val PBufRemoteStartInvokeClass          = classOf[PBufRemoteStartInvoke]
  private final val PBufRemoteStartInvokeAndListenClass = classOf[PBufRemoteStartInvokeAndListen]

  override def deserialize(metaData: Option[Array[Byte]], data: Array[Byte]): AnyRef =
    handleMetaData(metaData) match {
      case PBufSendClass                                                 => 
        toDelegate(parse(PBufSendClass, data))
      case PBufRemoteApplyClass                                          => 
        new PBufRemoteApplyDel(parse(PBufRemoteApplyClass, data))
      case PBufRemoteStartInvokeClass                                    => 
        new PBufRemoteStartInvokeDel(parse(PBufRemoteStartInvokeClass, data))
      case PBufRemoteStartInvokeAndListenClass                           => 
        new PBufRemoteStartInvokeAndListenDel(parse(PBufRemoteStartInvokeAndListenClass, data))
      case messageClz if (classOf[Message].isAssignableFrom(messageClz)) => 
        parse(messageClz.asInstanceOf[Class[Message]], data)
      case e =>
        throw new IllegalArgumentException("Don't know how to deserialize message of class " + e.getName)
    }

  private def toDelegate(p: PBufSend) = p.getSendType match {
    case ASYNC_SEND => new PBufAsyncSendDel(p)
    case SYNC_SEND  => new PBufSyncSendDel(p)
    case SYNC_REPLY => new PBufSyncReplyDel(p)
  }

}

trait ProtobufControllerMessageCreator { _: Serializer =>
  import PBufInternalConverters._
  override def newRemoteStartInvoke(actorClass: String) =
    PBufRemoteStartInvoke.newBuilder()
      .setActorClass(actorClass)
      .build()

  override def newRemoteStartInvokeAndListen(actorClass: String, port: Int, name: Symbol) =
    PBufRemoteStartInvokeAndListen.newBuilder()
      .setActorClass(actorClass)
      .setPort(port)
      .setName(name)
      .build()
}

trait ProtobufEnvelopeMessageCreator { _: Serializer =>
  import PBufInternalConverters._
  import PBufSend.PBufSendType._
  override def newAsyncSend(senderName: Option[Symbol], receiverName: Symbol, metaData: Array[Byte], data: Array[Byte]) =
    PBufSend.newBuilder()
      .setSendType(ASYNC_SEND)
      .setSenderName(senderName.map(_.name).orNull)
      .setReceiverName(receiverName)
      .setMetaData(metaData)
      .setData(data)
      .build()
      
  override def newSyncSend(senderName: Symbol, receiverName: Symbol, metaData: Array[Byte], data: Array[Byte], session: Symbol) =
    PBufSend.newBuilder()
      .setSendType(SYNC_SEND)
      .setSenderName(senderName)
      .setReceiverName(receiverName)
      .setMetaData(metaData)
      .setData(data)
      .setSession(session)
      .build()

  override def newSyncReply(receiverName: Symbol, metaData: Array[Byte], data: Array[Byte], session: Symbol) =
    PBufSend.newBuilder()
      .setSendType(SYNC_REPLY)
      .setReceiverName(receiverName)
      .setMetaData(metaData)
      .setData(data)
      .setSession(session)
      .build()

  override def newRemoteApply(senderName: Symbol, receiverName: Symbol, rfun: RemoteFunction) = {
    val (t, r) = remoteFunctionToTuple(rfun)
    PBufRemoteApply.newBuilder()
      .setSenderName(senderName)
      .setReceiverName(receiverName)
      .setFunctionType(t)
      .setReason(r.orNull)
      .build()
  }

}

class PBufAsyncSendDel(impl: PBufSend) extends AsyncSend {
  import PBufSend.PBufSendType._
  import PBufInternalConverters._
  assert(impl.getSendType == ASYNC_SEND)
  override def senderName: Option[Symbol] = Option(impl.getSenderName).map(s => Symbol(s))
  override def receiverName: Symbol = impl.getReceiverName
  override def metaData: Array[Byte] = impl.getMetaData
  override def data: Array[Byte] = impl.getData
}

class PBufSyncSendDel(impl: PBufSend) extends SyncSend {
  import PBufSend.PBufSendType._
  import PBufInternalConverters._
  assert(impl.getSendType == SYNC_SEND)
  override def senderName: Symbol = impl.getSenderName
  override def receiverName: Symbol = impl.getReceiverName
  override def metaData: Array[Byte] = impl.getMetaData
  override def data: Array[Byte] = impl.getData
  override def session: Symbol = impl.getSession
}

class PBufSyncReplyDel(impl: PBufSend) extends SyncReply {
  import PBufSend.PBufSendType._
  import PBufInternalConverters._
  assert(impl.getSendType == SYNC_REPLY)
  override def receiverName: Symbol = impl.getReceiverName
  override def metaData: Array[Byte] = impl.getMetaData
  override def data: Array[Byte] = impl.getData
  override def session: Symbol = impl.getSession
}

class PBufRemoteApplyDel(impl: PBufRemoteApply) extends RemoteApply {
  import PBufInternalConverters._
  override def senderName: Symbol = impl.getSenderName
  override def receiverName: Symbol = impl.getReceiverName
  override def function: RemoteFunction =
    typeToRemoteFunction(impl.getFunctionType, Option(impl.getReason))
}

class PBufRemoteStartInvokeDel(impl: PBufRemoteStartInvoke) extends RemoteStartInvoke {
  override def actorClass: String = impl.getActorClass
}

class PBufRemoteStartInvokeAndListenDel(impl: PBufRemoteStartInvokeAndListen) extends RemoteStartInvokeAndListen {
  import PBufInternalConverters._
  override def actorClass: String = impl.getActorClass
  override def port: Int = impl.getPort
  override def name: Symbol = impl.getName
}
