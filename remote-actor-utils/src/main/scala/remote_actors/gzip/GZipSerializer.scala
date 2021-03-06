package remote_actors.gzip

import scala.actors.Debug
import scala.actors.remote._

import java.io._
import java.util.zip._

class GZipSerializer(val underlying: Serializer) extends Serializer {

  override val uniqueId = 228081497L

  private var node: Node = _

  private var underlyingStarted = false

  override def handleNextEvent: PartialFunction[ReceivableEvent, Option[TriggerableEvent]] = {
    case StartEvent(n) =>
      node = n
      Some(SendEvent(uniqueId))
    case RecvEvent(msg) if (!underlyingStarted) =>
      if (msg != uniqueId)
        Some(Error("Did not send expected uniqueId"))
      else
        underlyingStarted = true
        underlying.handleNextEvent(StartEvent(node))
    case e if (underlyingStarted) => 
      underlying.handleNextEvent(e)
  }

  override val isHandshaking = true

  override def writeLocateRequest(outputStream: OutputStream, sessionId: Long, receiverName: String) {
    val gos = new GZIPOutputStream(outputStream)
    underlying.writeLocateRequest(gos, sessionId, receiverName)
    gos.finish()
  }

  override def writeLocateResponse(outputStream: OutputStream, sessionId: Long, receiverName: String, found: Boolean) {
    val gos = new GZIPOutputStream(outputStream)
    underlying.writeLocateResponse(gos, sessionId, receiverName, found)
    gos.finish()
  }

  override def writeAsyncSend(outputStream: OutputStream, senderName: String, receiverName: String, message: AnyRef) {
    val gos = new GZIPOutputStream(outputStream)
    underlying.writeAsyncSend(gos, senderName, receiverName, message)
    gos.finish()
  }

  override def writeSyncSend(outputStream: OutputStream, senderName: String, receiverName: String, message: AnyRef, session: String) {
    val gos = new GZIPOutputStream(outputStream)
    underlying.writeSyncSend(gos, senderName, receiverName, message, session)
    gos.finish()
  }

  override def writeSyncReply(outputStream: OutputStream, receiverName: String, message: AnyRef, session: String) {
    val gos = new GZIPOutputStream(outputStream)
    underlying.writeSyncReply(gos, receiverName, message, session)
    gos.finish()
  }

  override def writeRemoteApply(outputStream: OutputStream, senderName: String, receiverName: String, rfun: RemoteFunction) {
    val gos = new GZIPOutputStream(outputStream)
    underlying.writeRemoteApply(gos, senderName, receiverName, rfun)
    gos.finish()
  }

  override def read(bytes: InputStream) =
    underlying.read(new GZIPInputStream(bytes))

}
