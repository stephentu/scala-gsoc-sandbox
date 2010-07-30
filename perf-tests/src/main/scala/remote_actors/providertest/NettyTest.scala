package remote_actors
package providertest

import scala.actors.remote._

import java.net._
import java.util.concurrent._

import org.jboss.netty._
import bootstrap._
import buffer._
import channel._
import socket.nio._
import handler.codec.frame._

class FixedLengthPipelineFactory(handler: ChannelHandler) extends ChannelPipelineFactory {
  override def getPipeline(): ChannelPipeline = {
    Channels.pipeline(
      new LengthFieldPrepender(4),
      new LengthFieldBasedFrameDecoder(0x0fffffff, 0, 4, 0, 4),
      handler)
  }
}

class NettyServiceProvider extends ServiceProvider {
  override def mode = ServiceMode.NonBlocking
  val serverFactory = new NioServerSocketChannelFactory(
                                Executors.newCachedThreadPool(),
                                Executors.newCachedThreadPool())
  val clientFactory = new NioClientSocketChannelFactory(
                                Executors.newCachedThreadPool(),
                                Executors.newCachedThreadPool())
  override def connect(node: Node, receiveCallback: BytesReceiveCallback) = {
    val bootstrap = new ClientBootstrap(clientFactory)
    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setPipelineFactory(new FixedLengthPipelineFactory(
      new NettyClientHandler(receiveCallback)))
    val ftch = bootstrap.connect(new InetSocketAddress(node.address, node.port))
    new NettyByteConnection(ftch.getChannel)
  }
  override def listen(port: Int, 
                      connectionCallback: ConnectionCallback[ByteConnection], 
                      receiveCallback: BytesReceiveCallback) = {
    val bootstrap = new ServerBootstrap(serverFactory)
    bootstrap.setOption("child.tcpNoDelay", true)
    bootstrap.setPipelineFactory(new FixedLengthPipelineFactory(
      new NettyServerHandler(connectionCallback, receiveCallback)))
    new NettyListener(bootstrap.bind(new InetSocketAddress(port)))
  }
  override def doTerminateImpl(isBottom: Boolean) {
    throw new RuntimeException("UNIMPLEMENTED")
  }
}

class NettyByteConnection(chan: Channel) extends ByteConnection {
  override lazy val receiveCallback = throw new RuntimeException("UNIMPLEMENTED")
  override def remoteNode = throw new RuntimeException("UNIMPLEMENTED")
  override def localNode = throw new RuntimeException("UNIMPLEMENTED")
  override def isEphemeral = throw new RuntimeException("UNIMPLEMENTED")
  override def mode = throw new RuntimeException("UNIMPLEMENTED")
  override def send(data: Array[Byte]) {
    chan.write(ChannelBuffers.wrappedBuffer(data))
  }
  override def send(data0: Array[Byte], data1: Array[Byte]) {
    throw new RuntimeException("UNIMPLEMENTED")
  }
  override def doTerminateImpl(isBottom: Boolean) {
    chan.close()
  }
}

class NettyListener(chan: Channel) extends Listener {
  override lazy val connectionCallback = throw new RuntimeException("UNIMPLEMENTED")
  override def port = throw new RuntimeException("UNIMPLEMENTED")
  override def mode = throw new RuntimeException("UNIMPLEMENTED")
  override def doTerminateImpl(isBottom: Boolean) {
    chan.close()
  }
}

class NettyClientHandler(val recvCallback: BytesReceiveCallback) extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val msg = e.getMessage.asInstanceOf[ChannelBuffer].array
    recvCallback(new NettyByteConnection(e.getChannel), msg) 
  }
}

class NettyServerHandler(val connCallback: ConnectionCallback[ByteConnection], recvCallback: BytesReceiveCallback) extends SimpleChannelUpstreamHandler {
  override def childChannelOpen(ctx: ChannelHandlerContext, e: ChildChannelStateEvent) {
    connCallback(new NettyListener(e.getChannel), new NettyByteConnection(e.getChildChannel))
  } 
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    val msg = e.getMessage.asInstanceOf[ChannelBuffer].array
    recvCallback(new NettyByteConnection(e.getChannel), msg) 
  }
}
