package remote_actors.util

import scala.actors.remote._

class AsyncSendDelegate(val impl: AsyncSendLike) extends AsyncSend {
  override def senderName   = impl.senderName
  override def receiverName = impl.receiverName
  override def metaData     = impl.metaData
  override def data         = impl.data
}

class AsyncSendDelegate0(val impl: AsyncSendLike0) extends AsyncSend {
  override def senderName   = impl.senderName.map(s => Symbol(s))
  override def receiverName = Symbol(impl.receiverName)
  override def metaData     = impl.metaData
  override def data         = impl.data
}

class SyncSendDelegate(val impl: SyncSendLike) extends SyncSend {
  override def senderName   = impl.senderName
  override def receiverName = impl.receiverName
  override def metaData     = impl.metaData
  override def data         = impl.data
  override def session      = impl.session
}

class SyncSendDelegate0(val impl: SyncSendLike0) extends SyncSend {
  override def senderName   = Symbol(impl.senderName)
  override def receiverName = Symbol(impl.receiverName)
  override def metaData     = impl.metaData
  override def data         = impl.data
  override def session      = Symbol(impl.session)
}

class SyncReplyDelegate(val impl: SyncReplyLike) extends SyncReply {
  override def receiverName = impl.receiverName
  override def metaData     = impl.metaData
  override def data         = impl.data
  override def session      = impl.session
}

class SyncReplyDelegate0(val impl: SyncReplyLike0) extends SyncReply {
  override def receiverName = Symbol(impl.receiverName)
  override def metaData     = impl.metaData
  override def data         = impl.data
  override def session      = Symbol(impl.session)
}

class RemoteApplyDelegate(val impl: RemoteApplyLike) extends RemoteApply {
  override def senderName   = impl.senderName
  override def receiverName = impl.receiverName
  override def function     = impl.function
}

class RemoteApplyDelegate0(val impl: RemoteApplyLike0) extends RemoteApply {
  override def senderName   = Symbol(impl.senderName)
  override def receiverName = Symbol(impl.receiverName)
  override def function     = impl.function
}
