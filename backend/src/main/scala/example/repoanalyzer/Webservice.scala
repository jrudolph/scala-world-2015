package example.repoanalyzer

import akka.actor.ActorSystem
import akka.http.scaladsl.model.ws.TextMessage
import akka.http.scaladsl.model.{ HttpResponse, MediaTypes, HttpEntity }
import akka.http.scaladsl.server.{ Directive1, Directives }
import akka.stream.Materializer
import akka.stream.scaladsl.{ Source, Keep, Sink, Flow }
import akka.util.ByteString
import spray.json.JsonFormat

import scala.concurrent.Future

class Webservice(implicit system: ActorSystem, materializer: Materializer) extends Directives {
  // multiplexed source
  import system.dispatcher
  lazy val theRequest: Future[Source[AccessEntryWithGroup, Any]] = LogStream.requestSemanticLogLines() map { source ⇒
    val publisher = source.runWith(Sink.fanoutPublisher[AccessEntryWithGroup](16, 128))
    Source(publisher)
  }

  def route =
    get {
      pathSingleSlash {
        getFromResource("web/index.html")
      } ~
        path("raw") {
          withLogStream { stream ⇒
            complete(streamedToStringResponse(stream))
          }
        } ~
        path("group-counts") {
          withLogStream { stream ⇒
            complete(streamedToStringResponse(stream.via(LogStream.groupCountUpdates)))
          }
        } ~
        pathPrefix("ws") {
          path("all-entries") {
            withLogStream { stream ⇒
              val outStream = stream.via(toJsonWSMessage)
              handleWebsocketMessages(Flow.wrap(Sink.ignore, outStream)(Keep.none))
            }
          } ~
            path("group-counts") {
              withLogStream { stream ⇒
                val outStream =
                  stream.via(LogStream.groupCountUpdates).via(toJsonWSMessage)
                handleWebsocketMessages(Flow.wrap(Sink.ignore, outStream)(Keep.none))
              }
            }
        } ~
        // Scala-JS puts them in the root of the resource directory per default,
        // so that's where we pick them up
        path("frontend-launcher.js")(getFromResource("frontend-launcher.js")) ~
        path("frontend-fastopt.js")(getFromResource("frontend-fastopt.js"))
    } ~ getFromResourceDirectory("web")

  def withLogStream: Directive1[Source[AccessEntryWithGroup, Any]] =
    onSuccess(theRequest)

  def toJsonStringFlow[T: JsonFormat]: Flow[T, String, Any] = {
    import spray.json._
    Flow[T].map(t ⇒ t.toJson.compactPrint)
  }

  def toJsonWSMessage[T: JsonFormat]: Flow[T, TextMessage, Any] =
    toJsonStringFlow.map(TextMessage(_))

  def streamedToStringResponse(stream: Source[AnyRef, Any]): HttpResponse =
    HttpResponse(
      entity = HttpEntity.CloseDelimited(
        MediaTypes.`text/plain`,
        stream.map(e ⇒ ByteString(e.toString + "\n", "utf8"))))
}
