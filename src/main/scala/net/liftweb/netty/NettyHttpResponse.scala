package net.liftweb.netty

import io.netty.buffer._
import net.liftweb.http.provider.{HTTPParam, HTTPCookie, HTTPResponse}
import java.io.OutputStream
import io.netty.channel.{Channel, ChannelFutureListener}
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

  val buf = Unpooled.buffer(1024)
  lazy val nettyResponse: HttpResponse = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buf)
  
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

    def write(i: Int) {
      buf.writeByte(i)
    }

    override def write(bytes: Array[Byte]) {
      buf.writeBytes(bytes)
    }
    
    override def write(bytes: Array[Byte], offset: Int, len: Int) {
      buf.writeBytes(bytes, offset, len)
    }

    override def flush() {
      if (cookies.length > 0) writeCookiesToResponse()
      val future = channel.write(nettyResponse)
      if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
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