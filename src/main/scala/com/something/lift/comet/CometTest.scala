package com.something.lift.comet

import net.liftweb.common._
import net.liftweb.http._
import scala.xml._
import net.liftweb.http.js.JsCmds.SetHtml

class CometTest extends CometActor {

  override def defaultPrefix = Full("comet-test-prefix")

  override def messageHandler = {
    case a: String => partialUpdate(SetHtml("comet-test-output", Text(a)))
  }

  def render = {
    "#comet-test-input" #> SHtml.ajaxText("Input here", (x) => this ! x)
  }
}
