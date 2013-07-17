package net.liftweb.http.provider.netty

import io.netty.buffer._
import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.compression.ZlibCodecFactory
import io.netty.handler.codec.compression.ZlibWrapper
import io.netty.handler.codec.ByteToMessageDecoder
import net.liftweb.common.Logger
import net.liftweb.util.Props

/**
 * Manipulates the current pipeline dynamically to switch protocols or enable SSL or GZIP.
 */
object ProtoNegoHandler extends Logger {
  val maxChunkSize = Props.getInt("http.maxChunkSizeInBytes", 8192)
  val maxHeaderSize = Props.getInt("http.maxHeaderSizeInBytes", 8192)
  val maxInitLength = Props.getInt("http.maxInitialLineLength", 1024)
  val maxContentLength = Props.getInt("http.maxContentLengthInKB", 1024) * 1024
}

class ProtoNegoHandler(
  detectSsl: Boolean = false, // SslManager.sslAvailable,
  detectGzip: Boolean = Props.getBool("http.gzip", false)) extends ByteToMessageDecoder {

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    cause match {
      case e: javax.net.ssl.SSLException => //ignore this
      case e: Throwable =>
      /*ExceptionHandler(ctx, cause) foreach { c =>
          if (!debugStackTraces) ProtoNegoHandler.debug(cause.toString, cause)
          else ProtoNegoHandler.debug(stackTraceToString(cause))
        }*/
    }
  }

  override def decode(ctx: ChannelHandlerContext, buffer: ByteBuf, out: java.util.List[Object]) {
    // Will use the first two bytes to detect a protocol.
    if (buffer.readableBytes() < 5) return

    val magic1 = buffer.getUnsignedByte(buffer.readerIndex())
    val magic2 = buffer.getUnsignedByte(buffer.readerIndex() + 1)
    val p = ctx.pipeline

    if (detectSsl) {

      // Check for encrypted bytes
      /*if (SslHandler.isEncrypted(buffer)) {
        val engine = Server.service.sslManager.createSSLEngine
        engine.setEnableSessionCreation(true)
        p.addLast("ssl", new SslHandler(engine))
      }*/

      p.addLast("nego_gzip", new ProtoNegoHandler(false, detectGzip))
      buffer.retain
      p.remove(this)
    } else if (detectGzip) {
      // disable auto-read on the channel until authentication has finished
      //ctx.channel().config().setAutoRead(false)

      // Check for gzip compression
      if (isGzip(magic1, magic2)) {
        p.addLast("gzipdeflater", ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP))
        p.addLast("gzipinflater", ZlibCodecFactory.newZlibDecoder(ZlibWrapper.GZIP))
      }
      p.addLast("nego_http", new ProtoNegoHandler(detectSsl, false))
      buffer.retain
      p.remove(this)
    } else if (isHttp(magic1, magic2)) {
      import ProtoNegoHandler._
      p.addLast("decoder", new HttpRequestDecoder(maxInitLength, maxHeaderSize, maxChunkSize))
      p.addLast("aggregator", new HttpObjectAggregator(maxContentLength))
      p.addLast("encoder", new HttpResponseEncoder())
      if (detectGzip) p.addLast("deflater", new HttpContentCompressor())
      p.addLast("requestHandler", NettyRequestHandler)
      buffer.retain
      p.remove(this)
    } else {
      ProtoNegoHandler.info("unknown protocol: " + ctx.channel.remoteAddress.asInstanceOf[java.net.InetSocketAddress].getAddress.getHostAddress)
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

