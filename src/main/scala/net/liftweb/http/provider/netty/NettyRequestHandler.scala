package net.liftweb.http.provider.netty

import io.netty.channel._
import io.netty.handler.codec.http._
import io.netty.handler.codec.http.HttpHeaders._
import net.liftweb.common.{Full, Loggable, Box, Empty}
import net.liftweb.http.provider.{HTTPProvider, HTTPRequest}
import net.liftweb.util.{Helpers, Schedule}
import net.liftweb.http.{LiftSession, LiftRules, LiftServlet}
import org.apache.tika.config.TikaConfig
import org.apache.tika.io.TikaInputStream
import org.apache.tika.metadata.{TikaCoreProperties, Metadata}
import org.apache.tika.mime.MediaType
import io.netty.buffer.Unpooled
import com.google.common.io.ByteStreams
import io.netty.util.CharsetUtil

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
          ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.CONTINUE))
        }

        def doNotHandled(httpRequest: NettyHttpRequest) {
          Option(getClass.getResource(httpRequest.uri)) match {
            case Some(res) =>
              val config = TikaConfig.getDefaultConfig
              val detector = config.getDetector

              val stream = TikaInputStream.get(res)

              val metadata = new Metadata()
              metadata.add(TikaCoreProperties.TITLE, httpRequest.uri.split("/").last)
              val mediaType = detector.detect(stream, metadata)

              val buffer = Unpooled.wrappedBuffer(ByteStreams.toByteArray(res.openStream()))
              val resp = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, buffer)
              setContentLength(resp, buffer.readableBytes)
              resp.headers.set(HttpHeaders.Names.CONTENT_TYPE, mediaType.getType)
              if (!keepAlive) resp.headers.set(HttpHeaders.Names.CONNECTION, Values.CLOSE)
              val future = ctx.writeAndFlush(resp)
              if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
            case _ =>
              val future = ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.NOT_FOUND))
              if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
          }
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
              val future = ctx.writeAndFlush(new DefaultHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.BAD_REQUEST))
              if (!keepAlive) future.addListener(ChannelFutureListener.CLOSE)
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

