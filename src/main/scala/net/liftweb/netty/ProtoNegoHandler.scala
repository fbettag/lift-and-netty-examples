package net.liftweb.netty

import scala.collection.JavaConversions._

import io.netty.buffer._
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.ssl.SslHandler
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper

/**
 * Manipulates the current pipeline dynamically to switch protocols or enable SSL or GZIP.
 */
@ChannelHandler.Sharable
object ProtoNegoHandler extends ByteToMessageDecoder {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause match {
      case e: javax.net.ssl.SSLException => //ignore this
      case e: Throwable =>
        NettyExceptionHandler(ctx, e) foreach { c =>
          c.printStackTrace()
        }
    }
  }

  val maxChunkSize = 8192
  val maxHeaderSize = 8192
  val maxInitLength = 1024
  val maxContentLength = 1024 * 1024
  val detectSsl = false //SslManager.sslAvailable
  val detectGzip = true //Config.getBool("http.gzip", false)

  def decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: MessageList[Object]) {
    // Will use the first two bytes to detect a protocol.
    if (buffer.readableBytes() < 5) return

    val magic1 = buffer.getUnsignedByte(buffer.readerIndex())
    val magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1)
    val p = ctx.pipeline
    p.names.find(_.startsWith("nego")) match {
      case Some("nego") => // new connection
        if (detectSsl && SslHandler.isEncrypted(buffer)) {
          //val engine = LiftNettyServer.service.sslManager.createSSLEngine
          //engine.setEnableSessionCreation(true)
          //p.addLast("ssl", new SslHandler(engine))
        }

        p.addLast("nego_gzip", this)
        buffer.retain
        p.remove(this)

      case Some("nego_gzip") => // past SSL
        if (detectGzip && isGzip(magic1, magic2)) {
          p.addLast("gzipdeflater", ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP))
          p.addLast("gzipinflater", ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP))
        }
        p.addLast("nego_http", this)
        buffer.retain
        p.remove(this)

      case Some("nego_http") if isHttp(magic1, magic2) => // past GZIP
        p.addLast("decoder", new HttpRequestDecoder(maxInitLength, maxHeaderSize, maxChunkSize))
        p.addLast("aggregator", new HttpObjectAggregator(maxContentLength))
        p.addLast("encoder", new HttpResponseEncoder())
        if (detectGzip) p.addLast("deflater", new HttpContentCompressor())
        p.addLast("requestHandler", new NettyRequestHandler)
        buffer.retain
        p.remove(this)

      case _ =>
        println("unknown protocol")
        // Unknown protocol; discard everything and close the connection.
        buffer.clear
        ctx.close
    }
  }

  private def isGzip(magic1: Int, magic2: Int): Boolean = {
    magic1 == 31 && magic2 == 139
  }

  private def isHttp(magic1: Int, magic2: Int): Boolean = {
    magic1 == 'G' && magic2 == 'E' || // GET
      magic1 == 'P' && magic2 == 'O' || // POST
      magic1 == 'P' && magic2 == 'U' || // PUT
      magic1 == 'H' && magic2 == 'E' || // HEAD
      magic1 == 'O' && magic2 == 'P' || // OPTIONS
      magic1 == 'P' && magic2 == 'A' || // PATCH
      magic1 == 'D' && magic2 == 'E' || // DELETE
      magic1 == 'T' && magic2 == 'R' || // TRACE
      magic1 == 'C' && magic2 == 'O' // CONNECT
  }

}