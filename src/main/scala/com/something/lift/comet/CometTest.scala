package com.something.lift.comet

import net.liftweb.common._
import net.liftweb.http._
import scala.xml._
import net.liftweb.http.js.JsCmds.SetHtml
import net.liftweb.actor.LAPinger
import net.liftweb.util.Helpers._
import java.text.SimpleDateFormat
import java.util.Date

class CometTest extends CometActor {

  object Update

  override protected def localSetup() {
    super.localSetup()
    scheduleUpdate()
  }

  def scheduleUpdate() = LAPinger.schedule(this, Update, 5.seconds)

  def theDate = new SimpleDateFormat("hh:mm:ss").format(new Date())

  override def lowPriority = super.lowPriority orElse {
    case Update =>
      partialUpdate(SetHtml("comet-time", new Text(theDate)))
      scheduleUpdate()
  }

  def render = "#comet-time *" #> theDate

}
