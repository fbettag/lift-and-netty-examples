package net.liftweb.netty

import io.netty.buffer._
import net.liftweb.http.provider.{HTTPParam, HTTPCookie, HTTPResponse}
import java.io.{IOException, OutputStream}
import io.netty.channel.{ChannelFuture, Channel, ChannelFutureListener}
import io.netty.handler.codec.http._
import net.liftweb.http.LiftRules

/**
 * Representation of the HTTPResponseStatus
 *
 * @param channel - the netty channel
 * @param keepAlive - if true the channel's connection will remain open after response is written, otherwise it will be closed.
 *
 */
class NettyHttpResponse(channel: Channel, keepAlive: Boolean) extends HTTPResponse {

  val nettyHeaders = new DefaultHttpHeaders()
  private var responseStatus: HttpResponseStatus = HttpResponseStatus.OK

  private var cookies = List[HTTPCookie]()

  def addCookies(cks: List[HTTPCookie]) {
    cookies ++= cks
  }

  // FIXME should add session id to url if/when sessions are supported but session id not set in cookies
  def encodeUrl(url: String): String = url

  def addHeaders(headers: List[HTTPParam]) {
    val appearOnce = Set(LiftRules.overwrittenReponseHeaders.vend.map(_.toLowerCase):_*)
    for (h <- headers;
         value <- h.values) {
      if (appearOnce.contains(h.name.toLowerCase)) nettyHeaders.set(h.name, value)
      else nettyHeaders.set(h.name, value)
    }
  }

  def setStatus(status: Int): Unit = responseStatus = HttpResponseStatus.valueOf(status)

  def getStatus: Int = responseStatus.code()

  def setStatusWithReason(status: Int, reason: String): Unit = responseStatus = new HttpResponseStatus(status, reason)


  // TODO better flush
  def outputStream: OutputStream = new OutputStream {

    var last: Option[ChannelFuture] = None
    def writeBuffer(buffer: ByteBuf) = if (buffer.isReadable) {
      println(s"Writing buffer of size ${buffer.readableBytes()}")

      last match {
        case None =>
          val nettyResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
          for (c <- cookies) {
            val cookie = new DefaultCookie(c.name, c.value openOr null)

            c.domain foreach (cookie.setDomain(_))
            c.path foreach (cookie.setPath(_))
            c.maxAge foreach (cookie.setMaxAge(_))
            c.version foreach (cookie.setVersion(_))
            c.secure_? foreach (cookie.setSecure(_))
            c.httpOnly foreach (cookie.setHttpOnly(_))
            nettyHeaders.add(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.encode(cookie))
          }
          nettyResponse.setStatus(responseStatus)
          nettyResponse.headers().add(nettyHeaders)
          channel.write(nettyResponse)

          val f = channel.write(new DefaultHttpContent(buffer.retain))
          //if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE)
          last = Some(f)

        case Some(_) =>
          val nettyResponse = new DefaultLastHttpContent(buffer.retain)
          val f = channel.write(nettyResponse)
          if (!keepAlive) f.addListener(ChannelFutureListener.CLOSE)
          last = Some(f)
      }
    }

    def write(i: Int) {
      val buf = Unpooled.buffer(1)
      buf.writeByte(i)
      writeBuffer(buf)
    }

    override def write(bytes: Array[Byte]) {
      if (bytes.length > 0) writeBuffer(Unpooled.copiedBuffer(bytes).retain)
    }
    
    override def write(bytes: Array[Byte], offset: Int, len: Int) {
      if (bytes.length >= offset+len) writeBuffer(Unpooled.copiedBuffer(bytes, offset, len).retain)
    }

    override def flush() {
      last foreach { future =>
        println("Flushing channel")
        future.awaitUninterruptibly()
        if(!future.isSuccess)
          throw new IOException("Error writing to channel", future.cause())
      }
    }

    override def close() {
      try {
        flush()
      } finally {
        channel.close().awaitUninterruptibly()
        println("Closed channel")
      }
    }

  }
}