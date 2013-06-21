package net.liftweb.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.{ChannelInitializer, ChannelOption}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.SocketChannel
import java.net.InetSocketAddress
import net.liftweb.common.Empty
import net.liftweb.http.provider.{HTTPProvider, HTTPRequest, HTTPResponse}

object LiftNettyServer extends App with HTTPProvider { APP =>
  private var loopGroup1: Option[NioEventLoopGroup] = None
  private var loopGroup2: Option[NioEventLoopGroup] = None

  override def main(args: Array[String]) {
    start(8080)
  }

  def start(port: Int): ServerBootstrap = {
    loopGroup1 = Some(new NioEventLoopGroup)
    loopGroup2 = Some(new NioEventLoopGroup)
    val srv = new ServerBootstrap
    val addr = new InetSocketAddress(port)
    srv.group(loopGroup1.get, loopGroup2.get)
      .localAddress(addr)
      .channel(classOf[NioServerSocketChannel])
      .childOption[java.lang.Boolean](ChannelOption.TCP_NODELAY, true)
      .childOption[java.lang.Boolean](ChannelOption.SO_KEEPALIVE, true)
      .childOption[java.lang.Boolean](ChannelOption.SO_REUSEADDR, true)
      .childOption[java.lang.Integer](ChannelOption.SO_LINGER, 0)
      .childHandler(new ChannelInitializer[SocketChannel] {
        override def initChannel(ch: SocketChannel) {
          ch.pipeline.addLast("nego", ProtoNegoHandler)
        }
      })
    bootLift(Empty)
    srv.bind().syncUninterruptibly()
    println("Listening on %s:%s".format(addr.getAddress.getHostAddress, addr.getPort))
        // Add Shutdown Hook to cleanly shutdown Netty
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() { APP.stop() }
    })

    println("Ready")
    srv
  }

  val context = new NettyHttpContext
  def liftService(req : HTTPRequest, resp : HTTPResponse)(chain : => Unit) = super.service(req, resp)(chain)

  def stop() {
    loopGroup1.map(_.shutdownGracefully())
    loopGroup2.map(_.shutdownGracefully())
    loopGroup1 = None
    loopGroup2 = None
    terminate
    println("Shutdown complete")
  }
}