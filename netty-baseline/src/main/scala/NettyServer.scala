import java.net._
import java.util.concurrent._

import org.jboss.netty._
import bootstrap._
import channel._
import socket.nio._
import handler.codec.frame._

object NettyServer {

  def main(args: Array[String]) {
    val bootstrap = new ServerBootstrap(
      new NioServerSocketChannelFactory(
                                  Executors.newCachedThreadPool(),
                                  Executors.newCachedThreadPool()))

    bootstrap.setPipelineFactory(new ChannelPipelineFactory {
      override def getPipeline(): ChannelPipeline = {
        Channels.pipeline(
          new LengthFieldPrepender(4),
          new LengthFieldBasedFrameDecoder(0x0fffffff, 0, 4, 0, 4),
          new NettyServerHandler)
      }
    })

    bootstrap.bind(new InetSocketAddress(9000))
  }

}

class NettyServerHandler extends SimpleChannelUpstreamHandler {
  override def messageReceived(ctx: ChannelHandlerContext, e: MessageEvent) {
    e.getChannel.write(e.getMessage)
  }
}
