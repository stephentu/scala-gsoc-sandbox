package remote_actors

import scala.actors.remote.RemoteFunction

package object util {
  type AsyncSendLike = {
    def senderName: Option[Symbol]
    def receiverName: Symbol
    def metaData: Array[Byte]
    def data: Array[Byte]
  }
  type AsyncSendLike0 = {
    def senderName: Option[String]
    def receiverName: String
    def metaData: Array[Byte]
    def data: Array[Byte]
  }
  type SyncSendLike = {
    def senderName: Symbol
    def receiverName: Symbol
    def metaData: Array[Byte]
    def data: Array[Byte]
    def session: Symbol
  }
  type SyncSendLike0 = {
    def senderName: String
    def receiverName: String
    def metaData: Array[Byte]
    def data: Array[Byte]
    def session: String
  }
  type SyncReplyLike = {
    def receiverName: Symbol
    def metaData: Array[Byte]
    def data: Array[Byte]
    def session: Symbol
  }
  type SyncReplyLike0 = {
    def receiverName: String
    def metaData: Array[Byte]
    def data: Array[Byte]
    def session: String
  }
  type RemoteApplyLike = {
    def senderName: Symbol
    def receiverName: Symbol
    def function: RemoteFunction
  }
  type RemoteApplyLike0 = {
    def senderName: String
    def receiverName: String
    def function: RemoteFunction
  }
}
