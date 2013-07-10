package bootstrap.liftweb

import com.something.lift.MyRest
import net.liftweb.common._
import net.liftweb.http._
import net.liftweb.util._
import net.liftweb.sitemap._

class Boot extends Loggable {
  def boot() {
    logger.info("Run Mode: " + Props.mode.toString)

    // where to search snippet
    LiftRules.addToPackages("com.something.lift")

    // set the default htmlProperties
    LiftRules.htmlProperties.default.set((r: Req) => new Html5Properties(r.userAgent))

    // Build SiteMap
    LiftRules.setSiteMap(SiteMap(
      Menu.i("Home") / "index"
    ))

    LiftRules.explicitlyParsedSuffixes = Set("htm", "html", "shtml")

    // Show the spinny image when an Ajax call starts
    LiftRules.ajaxStart =
      Full(() => LiftRules.jsArtifacts.show("ajax-spinner").cmd)

    // Make the spinny image go away when it ends
    LiftRules.ajaxEnd =
      Full(() => LiftRules.jsArtifacts.hide("ajax-spinner").cmd)

    // Force the request to be UTF-8
    LiftRules.early.append(_.setCharacterEncoding("UTF-8"))

    LiftRules.dispatch.append(MyRest)
  }
}