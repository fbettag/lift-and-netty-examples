package net.liftweb.netty

import net.liftweb.http.provider.HTTPContext
import net.liftweb.common.{Loggable, Empty, Box}
import java.net.URL
import java.io.{ InputStream, File }
import io.netty.handler.codec.http.FullHttpRequest
import scala.collection.concurrent.TrieMap

object NettyHttpContext extends HTTPContext with Loggable {

  // netty has no context, so we assume root
  def path: String = ""

  def resource(path: String): URL = {
    getClass.getResource("/lift-webapp" + path)
  }

  def resourceAsStream(path: String): InputStream = Option(resource(path)) map(_.openStream) getOrElse null

  // FIXME  @see{net.liftweb.http.provider.HTTPContext}
  def mimeType(path: String): Box[String] = Some("text/html")

  /* All Methods Below Should Not Be Needed for Netty */

  def initParam(name: String): Box[String] = Empty

  def initParams: List[(String, String)] = List()

  val _attributes = TrieMap.empty[String, Any]

  def attribute(name: String): Box[Any] = _attributes.get(name)

  def attributes: List[(String, Any)] = _attributes.toList

  def setAttribute(name: String, value: Any) = _attributes.put(name, value)

  def removeAttribute(name: String) = _attributes.remove(name)
}