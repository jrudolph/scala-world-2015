package example.repoanalyzer

import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.io.Framing
import akka.stream.scaladsl._
import akka.util.ByteString

import scala.concurrent.Future

object Step6 extends Scaffolding with App {

  type GroupIdHistogram = Map[String, Int]

  def logLinesStreamFuture: Future[Source[Vector[(String, Int)], Any]] =
    Http().singleRequest(logStreamRequest).map { // Future[HttpResponse]
      _.entity.dataBytes
        // .via(Gzip.decoderFlow)
        .via(Framing.delimiter(ByteString("\n"),
          maximumFrameLength = 10000, allowTruncation = true))
        .map(_.utf8String)
        .mapConcat(line ⇒ RepoAccess.fromLine(line).toList)
        .mapAsync(1)(resolveIPInfo)
        .scan[GroupIdHistogram](Map.empty)(updateHistogram(_.ipInfo.map(_.country_code).filter(_.trim.nonEmpty)))
        .map(_.toVector.sortBy(-_._2))
    }

  runWebService {
    get {
      pathSingleSlash {
        getFromResource("web/country-counts-table.html")
      } ~
        path("country-counts") {
          onSuccess(logLinesStreamFuture) { stream ⇒
            import spray.json.DefaultJsonProtocol._
            import spray.json._
            val outStream = stream
              .map(_.toJson.prettyPrint)
              .map(ws.TextMessage(_))
            val flow = Flow.wrap(Sink.ignore, outStream)(Keep.none)
            handleWebsocketMessages(flow)
          }
        } ~
        getFromResourceDirectory("web") ~
        pathPrefix("flags") {
          mapUnmatchedPath(p ⇒ Uri.Path(p.toString().toLowerCase())) {
            getFromResourceDirectory("web/flags")
          } ~
            getFromResource("web/flags/fam.png")
        }
    }
  }

  lazy val ipResolver = new FreeGeoIPResolver()
  def resolveIPInfo(entry: RepoAccess): Future[RepoAccess] =
    ipResolver.infoFor(entry.ip).map(info ⇒ entry.copy(ipInfo = info))

  def updateHistogram(groupByKey: RepoAccess ⇒ Option[String])(histogram: GroupIdHistogram,
                                                               ra: RepoAccess): GroupIdHistogram =
    groupByKey(ra).map { gid ⇒
      histogram.updated(gid, histogram.getOrElse(gid, 0) + 1)
    } getOrElse histogram
}
