package net.liftweb.http.provider.netty

import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.{ChannelInitializer, ChannelOption}
import io.netty.channel.socket.nio.NioServerSocketChannel
import io.netty.channel.socket.SocketChannel
import java.net.InetSocketAddress
import net.liftweb.common.{Loggable, Empty}
import net.liftweb.http.provider.{HTTPProvider, HTTPRequest, HTTPResponse}
import net.liftweb.util.{NamedPF, Helpers}
import net.liftweb.http._
import scala.Some

object LiftNettyServer extends App with HTTPProvider with Loggable { APP =>

  override lazy val context = {
    //Set the context here and in LiftRules simultaneously
    val ctx = NettyHttpContext
    LiftRules.setContext(ctx)
    ctx
  }

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
          ch.pipeline.addLast("nego", new ProtoNegoHandler)
        }
      })
    bootLift(Empty)
    LiftNettyCookies.start()
    srv.bind().syncUninterruptibly()
    println("Listening on %s:%s".format(addr.getAddress.getHostAddress, addr.getPort))

    // Add Shutdown Hook to cleanly shutdown Netty
    Runtime.getRuntime.addShutdownHook(new Thread {
      override def run() { APP.stop() }
    })

    println("Ready")
    srv
  }

  def liftService(req : HTTPRequest, resp : HTTPResponse)(chain : => Unit) = {
      Helpers.tryo {
        LiftRules.early.toList.foreach(_(req))
      }
      println("Servicing " + req.uri)
      CurrentHTTPReqResp.doWith(req -> resp) {
        val newReq = Req(req, LiftRules.statelessRewrite.toList,
          Nil,
          LiftRules.statelessReqTest.toList,
          System.nanoTime)

        CurrentReq.doWith(newReq) {
          println("CurrentReq.doWith " + req.uri)
          URLRewriter.doWith({ url =>
            println("URLRewriter.doWith " + req.uri)
            NamedPF.applyBox(resp.encodeUrl(url),
              LiftRules.urlDecorate.toList) openOr
              resp.encodeUrl(url)}) {
            if (isLiftRequest_?(newReq)) {
              println("Servicing lift request " + req.uri)
              super.service(req, resp)(chain)
            }
            else {
              logger.warn("this should be handled by netty as static file as it is not handled by lift/jar")
              chain
            }
          }
        }
      }
    }

  def stop() {
    loopGroup1.map(_.shutdownGracefully())
    loopGroup2.map(_.shutdownGracefully())
    loopGroup1 = None
    loopGroup2 = None
    terminate
    println("Shutdown complete")
  }
}
