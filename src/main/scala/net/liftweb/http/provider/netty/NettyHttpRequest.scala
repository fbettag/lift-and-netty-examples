package net.liftweb.http.provider.netty

import net.liftweb.http.provider._
import net.liftweb.common._
import net.liftweb.http.{ParamHolder, LiftResponse, Req}
import java.util.Locale
import java.net.{URI, InetSocketAddress}
import java.io.{ByteArrayInputStream, InputStream}
import io.netty.handler.codec.http.{CookieDecoder, QueryStringDecoder, HttpHeaders, FullHttpRequest}
import io.netty.channel.Channel
import io.netty.util.CharsetUtil
import scala.collection.JavaConverters._

/**
 * The representation of a HTTP request state
 */
class NettyHttpRequest(request: FullHttpRequest, channel: Channel) extends HTTPRequest {
  def provider = LiftNettyServer

  lazy val nettyLocalAddress = channel.localAddress.asInstanceOf[InetSocketAddress]
  lazy val nettyRemoteAddress = channel.remoteAddress.asInstanceOf[InetSocketAddress]
  private val queryStringDecoder = new QueryStringDecoder(request.getUri, CharsetUtil.UTF_8)

  val contextPath = ""

  lazy val cookies: List[HTTPCookie] = LiftNettyCookies.getCookies(request)

  def headers(name: String): List[String] = request.headers().getAll(name).asScala.toList

  lazy val headers: List[HTTPParam] = request.headers().names().asScala.map(n => HTTPParam(n, request.headers().get(n))).toList
  def context: HTTPContext = LiftNettyServer.context

  def contentType: Box[String] = Box !! request.headers().get(HttpHeaders.Names.CONTENT_TYPE)

  def uri: String =  request.getUri

  def url: String = request.getUri

  def queryString: Box[String] =  Box !! uri.splitAt(uri.indexOf("?") + 1)._2

  def param(name: String): List[String] = queryStringDecoder.parameters().get(name).asScala.toList

  lazy val params: List[HTTPParam] = queryStringDecoder.parameters().asScala.map(b => HTTPParam(b._1, b._2.asScala.toList)).toList

  lazy val paramNames: List[String] = queryStringDecoder.parameters().asScala.map(_._1).toList

  // not needed for netty
  def destroyServletSession() {}

  def sessionId: Box[String] = LiftNettyCookies.getSessionId(request, cookies)

  def remoteAddress: String = nettyRemoteAddress.toString

  def remotePort: Int = nettyRemoteAddress.getPort

  def remoteHost: String = nettyRemoteAddress.getHostName

  def serverName: String = nettyLocalAddress.getHostName

  def scheme: String = new URI(request.getUri).getScheme

  def serverPort: Int = nettyLocalAddress.getPort

  def method: String = request.getMethod.toString

  lazy val inputStream: InputStream = {
    val arr = Array[Byte]()
    request.content().getBytes(0, arr)
    new ByteArrayInputStream(arr)
  }

  // FIXME the session is fake
  def session: HTTPSession =  new NettyHttpSession

  // FIXME
  def authType: Box[String] = throw new Exception("Implement me")

  // FIXME not really implemented, @dpp made it false when we started, left comment, "this should be trivial"
  def suspendResumeSupport_? : Boolean = true

  // FIXME not really implemented, @dpp made it None when we started, left comment, "trivial support"
  def resumeInfo : Option[(Req, LiftResponse)] = None

  // FIXME trivial support
  def suspend(timeout: Long): RetryState.Value = RetryState.SUSPENDED //throw new Exception("Implement me")

  // FIXME trivial support
  def resume(what: (Req, LiftResponse)): Boolean = true // throw new Exception("Implement me")

  // FIXME actually detect multipart content
  def multipartContent_? = false //

  // FIXME
  def extractFiles: List[ParamHolder] = throw new Exception("Implement me")

  // FIXME actually detect locale
  def locale: Box[Locale] = Empty

  // FIXME
  def setCharacterEncoding(encoding: String) = throw new Exception("Implement me")

  /**
   * Creates a new HTTPRequest instance as a copy of this one. It is used when
   * snapshots of the current request context is created in order for this request object
   * to be used on different threads (such as asynchronous template fragments processing).
   * The new instance must not keep any reference to the container' instances.
   */
  // FIXME actually copy instance
  def snapshot: HTTPRequest = this.clone().asInstanceOf[NettyHttpRequest]

  def userAgent: Box[String] = Box !! request.headers().get(HttpHeaders.Names.USER_AGENT)
}
