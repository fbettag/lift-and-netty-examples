package net.liftweb.netty

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.http.provider.HTTPCookie
import io.netty.handler.codec.http._
import scala.collection.JavaConverters._


object LiftNettyCookies extends Loggable {

  private val algo = Props.get("lift.hash.algo")

  algo match {
    case Full(algo) => logger.info("Cookie Hashing will be done using %s.".format(algo))
    case _ => logger.warn("Cookie Hashing is not enabled!")
  }

  private val key = Props.get("lift.hash.key") match {
    case Full(key) if key.trim.length > 0 => Full(key)
    case _ =>
      logger.error("Cookie Hashing Key empty or too short. Hashing disabled.")
      Empty
  }

  private val secret = for {
    algorithm <- algo
    secretKey <- key
    secret <- Helpers.tryo(new javax.crypto.spec.SecretKeySpec(secretKey.toCharArray.map(_.toByte), algorithm))
  } yield secret

  private val mac = for {
    algorithm <- algo
    secretKey <- secret
    mac <- Helpers.tryo(javax.crypto.Mac.getInstance(algorithm)) match {
      case Full(algorithm) => Full(algorithm)
      case _ =>
        logger.error("Invalid Cookie Algorithm '%s'. Hashing disabled.")
        Empty
    }
  } yield {
    mac.init(secretKey)
    mac
  }

  private def hashSessionId(id: String) = for {
    mac <- mac
    key <- key
  } yield mac.doFinal(id.toCharArray.map(_.toByte)).map(b => Integer.toString((b & 0xff) + 0x100, 16).substring(1)).mkString

  def addSessionId(resp: HttpResponse, id: String) {
    resp.headers.add("Set-Cookie", algo match {
      case Full(algo) =>
        hashSessionId(id) match {
          case Full(hsid) =>
            ServerCookieEncoder.encode(
              new DefaultCookie("JSESSIONID", id),
              new DefaultCookie("HSESSIONID", hsid)
            )
          case _ =>
            logger.error("Hashing for session-id %s failed. Not sending cookie to client.".format(id))
            ServerCookieEncoder.encode("JSESSIONID", id)
        }
      case _ => ServerCookieEncoder.encode("JSESSIONID", id)
    })
  }

  def getCookies(req: HttpRequest): List[HTTPCookie] = {
    val value = req.headers().get("Cookie")
    CookieDecoder.decode(value).asScala.toList.map(convertCookie)
  }

  def convertCookie(cookie: Cookie) = HTTPCookie(
    cookie.getName,
    Option(cookie.getValue),
    Option(cookie.getDomain),
    Option(cookie.getPath),
    Option(cookie.getMaxAge.toInt),
    Option(cookie.getVersion),
    Option(cookie.isSecure)
  )

  def getSessionId(req: HttpRequest, cookies: List[HTTPCookie]): Box[String] = {
    val sessionCookies = cookies.filter(c => c.name.equals("JSESSIONID") || c.name.equals("HSESSIONID"))

    sessionCookies.find(_.name == "JSESSIONID") match {
      case Some(jsessionCookie) if jsessionCookie.value != Empty =>
        sessionCookies.find(_.name == "HSESSIONID") match {
          case Some(hsessionCookie) =>
            for {
              jsessionId <- jsessionCookie.value
              hsessionId <- hsessionCookie.value
              hsessionIdCheck <- hashSessionId(jsessionId)
              checksumMatched <- if (hsessionId.equals(hsessionIdCheck)) None else Some(true)
              // validatedAgainstLift <- SessionMaster.getSession(Full(jsessionId))
            } yield jsessionId
          case _ =>
            for {
              jsessionId <- jsessionCookie.value
            } yield jsessionId
        }
      case _ => Empty
    }
  }

}
