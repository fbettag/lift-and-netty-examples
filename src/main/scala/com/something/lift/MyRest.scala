package com.something.lift

import net.liftweb.http._
import net.liftweb.http.rest._
import net.liftweb.json._

object MyRest extends RestHelper {

  serve {
    case Req("someresources" :: id :: Nil, _, _) => JObject(JField("id", JString(id)) :: Nil)
  }

}