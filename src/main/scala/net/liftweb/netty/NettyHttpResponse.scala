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

  lazy val nettyResponse: HttpResponse = new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK)
  
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
      if (appearOnce.contains(h.name.toLowerCase)) nettyResponse.headers().set(h.name, value)
      else nettyResponse.headers.set(h.name, value)
    }
  }

  def setStatus(status: Int) {
    nettyResponse.setStatus(HttpResponseStatus.valueOf(status))
  }

  def getStatus: Int = nettyResponse.getStatus.code

  def setStatusWithReason(status: Int, reason: String) {
    nettyResponse.setStatus(new HttpResponseStatus(status, reason))
  }

  // TODO better flush
  def outputStream: OutputStream = new OutputStream {

    var last: Option[ChannelFuture] = None

    lazy val firstWrite = {
      if (cookies.length > 0) writeCookiesToResponse()
      last = Option(channel.write(nettyResponse))
      println("Wrote headers to channel")
    }

    def writeBuffer(buffer: ByteBuf) = {
      buffer.retain()
      if(buffer.isReadable) {
        println(s"Writing buffer of size ${buffer.readableBytes()}")
        last = Option(channel.write(buffer))
      }
    }

    def write(i: Int) {
      firstWrite
      val buf = Unpooled.buffer(1)
      buf.writeByte(i)
      writeBuffer(buf)
    }

    override def write(bytes: Array[Byte]) {
      firstWrite
      writeBuffer(Unpooled.copiedBuffer(bytes))
    }
    
    override def write(bytes: Array[Byte], offset: Int, len: Int) {
      firstWrite
      writeBuffer(Unpooled.copiedBuffer(bytes, offset, len))
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
        firstWrite
        flush()
      } finally {
        channel.close().awaitUninterruptibly()
        println("Closed channel")
      }
    }

  }
  
  private def writeCookiesToResponse() {

    for (c <- cookies) {
      val cookie = new DefaultCookie(c.name, c.value openOr null)
      c.domain foreach (cookie.setDomain(_))
      c.path foreach (cookie.setPath(_))
      c.maxAge foreach (cookie.setMaxAge(_))
      c.version foreach (cookie.setVersion(_))
      c.secure_? foreach (cookie.setSecure(_))
      c.httpOnly foreach (cookie.setHttpOnly(_))
      nettyResponse.headers().add(HttpHeaders.Names.SET_COOKIE, ServerCookieEncoder.encode(cookie))
    }
  }
}