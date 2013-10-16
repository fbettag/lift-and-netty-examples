package com.something.lift.snippet

import java.text.SimpleDateFormat
import net.liftweb.util.Helpers._
import java.util.Date

class Test {
  def theDate = new SimpleDateFormat("hh:mm:ss").format(new Date())
  def render = "#snippet-time *" #> theDate
}
