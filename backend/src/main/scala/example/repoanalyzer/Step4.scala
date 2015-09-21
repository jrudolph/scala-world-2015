package example.repoanalyzer

import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.io.Framing
import akka.stream.scaladsl.Source
import akka.util.ByteString

object Step4 extends Scaffolding with App {

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

  runWebService {
    get {
      pathSingleSlash {
        onSuccess(logLinesStreamFuture) { stream ⇒
          complete {
            HttpResponse(
              entity = HttpEntity.Chunked(
                MediaTypes.`text/plain`,
                stream.map(x ⇒ ByteString(x.toString + "\n---\n", "UTF8"))))
          }
        }
      }
    }
  }

  def updateHistogram(histogram: GroupIdHistogram,
                      ra: RepoAccess): GroupIdHistogram =
    ra.groupId.map { gid ⇒
      histogram.updated(gid, histogram.getOrElse(gid, 0) + 1)
    } getOrElse histogram

}
