package net.liftweb.netty

import net.liftweb.util._
import net.liftweb.common._
import net.liftweb.http.provider.HTTPCookie

import io.netty.handler.codec.http._

import collection.mutable.{ Set => MSet }
import collection.convert.WrapAsScala._


object LiftNettyCookies extends Loggable {

  val sessionCookieName = Props.get("lift.cookie.name", "JSESSIONID")

  private val _random = new java.security.SecureRandom

  private val algo = Props.get("lift.hash.algo")

  algo match {
    case Full(algo) => logger.info("Cookie Hashing will be done using %s.".format(algo))
    case _ => logger.warn("Cookie Hashing is not enabled!")
  }

  private val key = Props.get("lift.cookie.secret").map(_.trim) match {
    case Full(key) if key.length > 0 => Full(key)
    case _ =>
      logger.error("Cookie Hashing Key empty or too short. Hashing disabled.")
      Empty
  }

  private val secret = for {
    algorithm <- algo
    secretKey <- key
    secret <- Helpers.tryo(new javax.crypto.spec.SecretKeySpec(secretKey.getBytes, algorithm))
  } yield secret

  private val mac = for {
    algorithm <- algo
    secretKey <- secret
    mac <- Helpers.tryo(javax.crypto.Mac.getInstance(algorithm)) match {
      case f @ Full(algorithm) => f
      case _ =>
        logger.warn("Invalid Cookie Algorithm '%s'. Hashing disabled.")
        Empty
    }
  } yield {
    mac.init(secretKey)
    mac
  }

  private def hashSessionId(id: String) = for {
    mac <- mac
  } yield Helpers.base64Encode(mac.doFinal(id.getBytes))
  
  def addSessionId(resp: HttpResponse, id: String) {
    resp.headers.add("Set-Cookie", 
      hashSessionId(id) match {
        case Full(hsid) =>
          ServerCookieEncoder.encode(
            new DefaultCookie(sessionCookieName, id + "." + hsid)
          )
        case _ =>
          ServerCookieEncoder.encode(sessionCookieName, id)
      }
    )
  }

  def getCookies(req: HttpRequest): List[HTTPCookie] = {
    Option(req.headers().get("Cookie")) match {
      case Some(value) =>
        CookieDecoder.decode(value).map(convertCookie).toList
      case _ => Nil
    }
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

  def generateNewSessionId = _random.synchronized { 
    val array = new Array[Byte](24)
    _random.nextBytes(array)
    Helpers.base64Encode(array)
  }

  def getSessionId(req: HttpRequest, cookies: List[HTTPCookie]): Box[String] = {
    cookies.find(_.name == sessionCookieName).flatMap(_.value) match {
      case Some(sessionCookie) =>
        val splitted = sessionCookie.split(".")

        val id = splitted(0)

        if(splitted.length > 1) {
          val signature = splitted(1)

          hashSessionId(id) match {
            case Full(hash) if hash == signature => Full(id)
            case _ => Full(generateNewSessionId)
          }
        } else {
          Full(id)
        }
      case _ => Full(generateNewSessionId)
    }
  }

  def start() {
    logger.info("Cookie Manager started")
  }

}
