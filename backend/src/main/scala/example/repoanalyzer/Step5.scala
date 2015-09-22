package example.repoanalyzer

import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.io.Framing
import akka.stream.scaladsl._
import akka.util.ByteString

object Step5 extends Scaffolding with App {

  type GroupIdHistogram = Map[String, Int]

  def logLinesStreamFuture: Future[Source[Vector[(String, Int)], Any]] =
    Http().singleRequest(logStreamRequest).map { // Future[HttpResponse]
      _.entity.dataBytes
        // .via(Gzip.decoderFlow)
        .via(Framing.delimiter(ByteString("\n"),
          maximumFrameLength = 10000, allowTruncation = true))
        .map(_.utf8String)
        .mapConcat(line ⇒ RepoAccess.fromLine(line).toList)
        .scan[GroupIdHistogram](Map.empty)(updateHistogram)
        .map(_.toVector.sortBy(-_._2))
    }

  // format: OFF
  runWebService {
    get {
      pathSingleSlash {
        getFromResource("web/group-counts-table.html")
      } ~
      path("bars") {
        getFromResource("web/group-counts-bars.html")
      } ~
      path("group-counts") {
        onSuccess(logLinesStreamFuture) { stream ⇒
          import spray.json._
          import spray.json.DefaultJsonProtocol._
          val outStream = stream
            .map(_.toJson.prettyPrint)
            .map(ws.TextMessage(_))
          val flow = Flow.wrap(Sink.ignore, outStream)(Keep.none)
          handleWebsocketMessages(flow)
        }
      } ~
      getFromResourceDirectory("web")
    }
  }
  // format: ON

  def updateHistogram(histogram: GroupIdHistogram,
                      ra: RepoAccess): GroupIdHistogram =
    ra.groupId.map { gid ⇒
      histogram.updated(gid, histogram.getOrElse(gid, 0) + 1)
    } getOrElse histogram
}
