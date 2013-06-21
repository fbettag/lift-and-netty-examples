package net.liftweb.netty

import io.netty.channel._
import io.netty.handler.codec.http._
import net.liftweb.common.{Box, Empty}
import net.liftweb.http.provider.{HTTPProvider, HTTPRequest}
import net.liftweb.util.{Helpers, Schedule}
import net.liftweb.http.{LiftSession, LiftRules, LiftServlet}

/**
 * Handles incoming requests which will be sent to an AuthActor
 */
//@ChannelHandler.Sharable
class NettyRequestHandler extends ChannelInboundHandlerAdapter {

  val context = new NettyHttpContext
  val liftLand = new LiftServlet(context)

  private def findObject(cls: String): Box[AnyRef] =
    Helpers.tryo[Class[_]](Nil)(Class.forName(cls + "$")).flatMap {
      c =>
        Helpers.tryo {
          val field = c.getField("MODULE$")
          field.get(null)
        }
    }

  type VarProvider = { def apply[T](session: Box[LiftSession], f: => T): T}
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
    println("client connected")
    LiftRules.setContext(context)
  }

  override def channelUnregistered(ctx: ChannelHandlerContext) {
    println("client disconnected")
  }

  override def messageReceived(ctx: ChannelHandlerContext, msgs: MessageList[Object]) {
    val iter = msgs.iterator
    while (iter.hasNext) messageReceived(ctx, iter.next)
    msgs.clear
  }

  def messageReceived(ctx: ChannelHandlerContext, msg: Object) {
    msg match {
      case req: FullHttpRequest =>
         println(req.toString)
        val keepAlive = HttpHeaders.isKeepAlive(req)

        if (HttpHeaders.is100ContinueExpected(req)) {
          ctx.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
        }

        def doNotHandled() {}

        Schedule(() => {
          try {
            transientVarProvider(Empty,
              reqVarProvider(Empty, {
                val httpRequest: HTTPRequest = new NettyHttpRequest(req, ctx.channel, context, LiftNettyServer)
                val httpResponse = new NettyHttpResponse(ctx.channel, keepAlive)

                handleLoanWrappers(LiftNettyServer.liftService(httpRequest, httpResponse) {
                  doNotHandled()
                })
              }))
          } catch {
            case excp: Throwable => {
              excp.printStackTrace()
              val future = ctx.write(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
              if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
            }
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

