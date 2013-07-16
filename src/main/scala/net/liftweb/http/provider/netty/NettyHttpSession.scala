package net.liftweb.http.provider.netty

import net.liftweb.http.provider.HTTPSession
import net.liftweb.http.LiftSession
import scala.collection.concurrent.TrieMap
import net.liftweb.common.Box
import net.liftweb.actor.{LAPinger, LiftActor}
import java.util.{Date, Calendar}
import net.liftweb.util.Helpers._
import scala.collection.immutable.HashMap

case class NettyHttpSession(val sessionId: String) extends HTTPSession {

  private val LiftMagicID = "$lift_magic_session_thingy$"

  // FIXME implement me for realz
  def link(liftSession: LiftSession) =
    setAttribute(LiftMagicID, liftSession)

  // FIXME
  def unlink(liftSession: LiftSession) =
    removeAttribute(LiftMagicID)

  private var _maxInactiveInterval= 2.minutes

  /**
   * Default of 10 minutes
   */
  def maxInactiveInterval: Long = _maxInactiveInterval

  // FIXME
  def setMaxInactiveInterval(interval: Long) = _maxInactiveInterval = interval

  @volatile var _lastAccessedTime = System.currentTimeMillis

  private[netty] def touch() = {
    _lastAccessedTime = System.currentTimeMillis
    this
  }

  // FIXME return real time when sessions are supported
  def lastAccessedTime: Long = _lastAccessedTime

  def expires: Date = {
    val calendar = Calendar.getInstance()
    calendar.setTimeInMillis(lastAccessedTime)
    calendar.add(Calendar.MILLISECOND, maxInactiveInterval.toInt)
    calendar.getTime
  }

  val _attributes = TrieMap.empty[String, Any]

  def attribute(name: String): Box[Any] = _attributes.get(name)

  def setAttribute(name: String, value: Any) = _attributes.put(name, value)

  def removeAttribute(name: String) = _attributes.remove(name)

  def terminate() = {
    _attributes.clear()
    NettyHttpSession ! NettyHttpSession.SessionTerminated(this)
  }

}

object NettyHttpSession extends LiftActor {

  import net.liftweb.util.Helpers._

  //Volatile so lookups can be done from other threads
  @volatile var sessions = HashMap.empty[String, NettyHttpSession]

  private var cleanupScheduled = false

  protected def messageHandler: PartialFunction[Any, Unit] = {
    case m: Message => m match {
      case RegisterSession(session) =>
        sessions += (session.sessionId -> session)
        if(!cleanupScheduled) {
          scheduleCleanup()
          cleanupScheduled
        }
      case SessionTerminated(session) =>
        sessions -= session.sessionId
      case CleanupSessions =>
        cleanupScheduled = false
        val now = Calendar.getInstance().getTime
        sessions foreach { case(id, s) =>
          if(s.expires.before(now)) {
            s.terminate()
          }
        }
        if(!sessions.isEmpty)
          scheduleCleanup()
    }
  }

  def find(sessionId: String) = sessions.values.find(_.sessionId == sessionId)

  private def scheduleCleanup() = {
    LAPinger.schedule(this, CleanupSessions, 30.seconds)
    cleanupScheduled = true
  }

  scheduleCleanup()

  sealed trait Message

  case class RegisterSession(session: NettyHttpSession) extends Message

  case class SessionTerminated(session: NettyHttpSession) extends Message

  object CleanupSessions extends Message

}
