package net.liftweb.netty

import net.liftweb.http.provider.HTTPContext
import net.liftweb.common.{Loggable, Empty, Box}
import java.net.URL
import java.io.{ InputStream, File }
import io.netty.handler.codec.http.FullHttpRequest

class NettyHttpContext extends HTTPContext with Loggable {

  // netty has no context, so we assume root
  def path: String = new File(".").getPath()

  // FIXME this should actually return the path to the resource @see{net.liftweb.http.provider.HTTPContext}
  //def resource(path: String): URL = new File(".").toURI().toURL()
  def resource(path: String): URL =
    new File(classOf[NettyHttpContext].getProtectionDomain().getCodeSource().getLocation().getPath()).toURI().toURL()

  // FIXME @see{net.liftweb.http.provider.HTTPContext}
  def resourceAsStream(path: String): InputStream = throw new Exception("Not Yet Implemented")

  // FIXME  @see{net.liftweb.http.provider.HTTPContext}
  def mimeType(path: String): Box[String] = Some("text/html")

  /* All Methods Below Should Not Be Needed for Netty */

  def initParam(name: String): Box[String] = Empty

  def initParams: List[(String, String)] = List()

  def attribute(name: String): Box[Any] = Empty

  def attributes: List[(String, Any)] = List()

  def setAttribute(name: String, value: Any) { }

  def removeAttribute(name: String) { }
}