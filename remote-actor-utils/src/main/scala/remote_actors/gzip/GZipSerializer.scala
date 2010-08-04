package remote_actors.gzip

import scala.actors.Debug
import scala.actors.remote._

import java.io._
import java.util.zip._

abstract class HasGZipSerializer extends Configuration {
  protected def newUnderlyingSerializer(): Serializer
  override def newSerializer() = new GZipClientSerializer(newUnderlyingSerializer())
}

class GZipClientSerializer(override val underlying: Serializer) 
  extends GZipBaseSerializer {

  import GZipBaseSerializer._

  override val uniqueId = 2372769496L

  override def bootstrapClassName = classOf[GZipServerSerializer].getName

  private var node: Node = _

  private var underlyingStarted = false

  override def handleNextEvent: PartialFunction[ReceivableEvent, Option[TriggerableEvent]] = {
    case StartEvent(n) =>
      node = n
      Some(SendEvent(MagicNumber))
    case RecvEvent(msg) if (!underlyingStarted) =>
      if (msg != MagicNumber)
        Some(Error("Did not send expected magic number"))
      else
        underlyingStarted = true
        bootstrapUnderlying(node)
    case e if (underlyingStarted) => 
      underlying.handleNextEvent(e)
  }

}

class GZipServerSerializer extends GZipBaseSerializer {

  import GZipBaseSerializer._

  override val uniqueId = 3699026858L

  override def bootstrapClassName = 
    throw new IllegalStateException("bootstrapClassName should never be called on the server side")

  @volatile private var _underlying: Serializer = _

  override def underlying = 
    if (_underlying eq null)
      throw new IllegalStateException("Cannot ask for underlying")
    else 
      _underlying

  private var node: Node = _
  private var confirmed = false
  private var underlyingStarted = false

  override def handleNextEvent: PartialFunction[ReceivableEvent, Option[TriggerableEvent]] = {
    case StartEvent(n) =>
      node = n
      Some(SendEvent(MagicNumber))
    case RecvEvent(msg) if (!confirmed) =>
      if (msg != MagicNumber)
        Some(Error("Did not send expected magic number"))
      else
        confirmed = true
        None
    case RecvEvent(msg: Array[Byte]) if (confirmed && !underlyingStarted) =>
      try {
        _underlying = Class.forName(new String(msg)).newInstance().asInstanceOf[Serializer]
        underlyingStarted = true
        underlying.handleNextEvent(StartEvent(node))
      } catch {
        case e: Exception =>
          Debug.error(this + ": caught exception: " + e.getMessage)
          Debug.doError { e.printStackTrace() }
          Some(Error("Could not instantiate underlying serializer"))
      }
    case e if (confirmed && underlyingStarted) =>
      underlying.handleNextEvent(e)
    case m =>
      Some(Error("Do not know how to handle message: " + m))
  }

}

object GZipBaseSerializer {
  final val MagicNumber = 228081497
}

abstract class GZipBaseSerializer extends Serializer {

  override val isHandshaking = true

  protected def underlying: Serializer

  protected def compress(bytes: Array[Byte]): Array[Byte] = {
    val baos = new ByteArrayOutputStream(bytes.length)
    val gos  = new GZIPOutputStream(baos)
    gos.write(bytes, 0, bytes.length)
    gos.close()
    baos.toByteArray
  }

  protected def uncompress(bytes: Array[Byte]): Array[Byte] = {
    val bais = new ByteArrayInputStream(bytes)
    val gis  = new GZIPInputStream(bais)
    
    val baos = new ByteArrayOutputStream
    val buf  = new Array[Byte](1024)

    var continue = true
    while (continue) {
      var bytesRead = gis.read(buf, 0, buf.length)
      if (bytesRead == -1)
        continue = false
      else
        baos.write(buf, 0, bytesRead)
    }

    baos.toByteArray
  }

  protected def bootstrapUnderlying(node: Node) = underlying.handleNextEvent(StartEvent(node)) match {
    case Some(SendEvent(msgs @ _*)) =>
      Some(SendEvent((Array(underlying.bootstrapClassName.getBytes) ++ msgs) : _*))
    case Some(SendWithSuccessEvent(msgs @ _*)) =>
      Some(SendWithSuccessEvent((Array(underlying.bootstrapClassName.getBytes) ++ msgs) : _*))
    case Some(SendWithErrorEvent(reason, msgs @ _*)) =>
      Some(SendWithErrorEvent(reason, (Array(underlying.bootstrapClassName.getBytes) ++ msgs) : _*))
    case Some(Success) =>
      Some(SendWithSuccessEvent(underlying.bootstrapClassName.getBytes))
    case Some(Error(reason)) =>
      Some(SendWithErrorEvent(reason, underlying.bootstrapClassName.getBytes))
    case None =>
      Some(SendEvent(underlying.bootstrapClassName.getBytes))
  }

  override def newAsyncSend(senderName: Option[Symbol], receiverName: Symbol, metaData: Array[Byte], data: Array[Byte]) = 
    underlying.newAsyncSend(senderName, receiverName, metaData, data)

  override def newSyncSend(senderName: Symbol, receiverName: Symbol, metaData: Array[Byte], data: Array[Byte], session: Symbol) =
    underlying.newSyncSend(senderName, receiverName, metaData, data, session)

  override def newSyncReply(receiverName: Symbol, metaData: Array[Byte], data: Array[Byte], session: Symbol) = 
    underlying.newSyncReply(receiverName, metaData, data, session)

  override def newRemoteApply(senderName: Symbol, receiverName: Symbol, rfun: RemoteFunction) = 
    underlying.newRemoteApply(senderName, receiverName, rfun)

  override def newRemoteStartInvoke(actorClass: String) = 
    underlying.newRemoteStartInvoke(actorClass)

  override def newRemoteStartInvokeAndListen(actorClass: String, port: Int, name: Symbol) =
    underlying.newRemoteStartInvokeAndListen(actorClass, port, name)

  override def serializeMetaData(message: AnyRef) = 
    underlying.serializeMetaData(message) /** No compression on the metadata (since it's usually small) */

  override def serialize(message: AnyRef) =
    compress(underlying.serialize(message))

  override def deserialize(metaData: Option[Array[Byte]], data: Array[Byte]) = 
    underlying.deserialize(metaData, uncompress(data))
}
