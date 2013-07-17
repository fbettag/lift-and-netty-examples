package net.liftweb.http.provider.netty

import io.netty.channel._
import io.netty.handler.codec.http._
import net.liftweb.common.{Loggable, Box, Empty}
import net.liftweb.http.provider.{HTTPProvider, HTTPRequest}
import net.liftweb.util.{Helpers, Schedule}
import net.liftweb.http.{LiftSession, LiftRules, LiftServlet}

/**
 * Handles incoming requests which will be sent to an AuthActor
 */
@ChannelHandler.Sharable
object NettyRequestHandler extends SimpleChannelInboundHandler[Object] with Loggable {

  private def findObject(cls: String): Box[AnyRef] =
    Helpers.tryo[Class[_]](Nil)(Class.forName(cls + "$")).flatMap {
      c =>
        Helpers.tryo {
          val field = c.getField("MODULE$")
          field.get(null)
        }
    }

  type VarProvider = {def apply[T](session: Box[LiftSession], f: => T): T}

  lazy val transientVarProvider = findObject("net.liftweb.http.TransientRequestVarHandler").openOrThrowException("because").asInstanceOf[VarProvider]

  lazy val reqVarProvider = findObject("net.liftweb.http.RequestVarHandler").openOrThrowException("because").asInstanceOf[VarProvider]

  override def exceptionCaught(ctx: ChannelHandlerContext, cause: Throwable) {
    NettyExceptionHandler(ctx, cause) foreach {
      c =>
        c.printStackTrace()
        // Only close on major issues
        if (ctx.channel().isOpen) ctx.channel().close
    }
  }

  override def channelActive(ctx: ChannelHandlerContext) {
    logger.debug("client connected")
  }

  override def channelUnregistered(ctx: ChannelHandlerContext) {
    logger.debug("client disconnected")
  }

  def channelRead0(ctx: ChannelHandlerContext, msg: Object) {
    msg match {
      case req: FullHttpRequest =>
        req.retain

        val keepAlive = HttpHeaders.isKeepAlive(req)

        if (HttpHeaders.is100ContinueExpected(req)) {
          ctx.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
          ctx.flush()
        }

        //FIXME: Needs to serve pages from disk
        def doNotHandled(httpRequest: NettyHttpRequest) {
          logger.warn("do not handled called for " + httpRequest.uri)
        }

        Schedule(() => {
          try {
            transientVarProvider(Empty,
              reqVarProvider(Empty, {
                val httpResponse = new NettyHttpResponse(ctx.channel, keepAlive)
                val httpRequest = new NettyHttpRequest(req, ctx.channel, httpResponse)
                /*
                 * Update the last accessed time on any existing session
                 */
                for(sid <- httpRequest.sessionId; session <- NettyHttpSession.find(sid)) {
                  session.touch()
                }
                handleLoanWrappers(LiftNettyServer.liftService(httpRequest, httpResponse) {
                  doNotHandled(httpRequest)
                })
              }))
          } catch {
            case excp: Throwable => {
              excp.printStackTrace()
              val future = ctx.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
              if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
              ctx.flush()
            }
          } finally {
            req.release()
          }
        })
    }
  }

  /**
   * Wrap the loans around the incoming request
   */
  private def handleLoanWrappers[T](f: => T): T = {
    /*
    FIXME -- this is a 2.5-ism
    LiftRules.allAround.toList match {
      case Nil => f
      case x :: xs => x(handleLoan(xs))
    }
    */
    f
  }
}

