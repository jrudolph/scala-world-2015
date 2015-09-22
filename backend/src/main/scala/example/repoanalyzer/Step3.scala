package example.repoanalyzer

import scala.concurrent.Future
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.io.Framing
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString

object Step3 extends Scaffolding with App {

  def logLinesStreamFuture: Future[Source[RepoAccess, Any]] =
    Http().singleRequest(logStreamRequest).map { // Future[HttpResponse]
      _.entity.dataBytes
        // .via(Gzip.decoderFlow)
        .via(Framing.delimiter(ByteString("\n"),
          maximumFrameLength = 10000, allowTruncation = true))
        .map(_.utf8String)
        .mapConcat(line ⇒ RepoAccess.fromLine(line).toList)
        .via(logFlow)
    }

  runWebService {
    get {
      onSuccess(logLinesStreamFuture) { stream ⇒
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked(
              MediaTypes.`text/plain`,
              stream.map(x ⇒ ByteString(pretty(x) + "\n\n", "UTF8"))))
        }
      }
    }
  }

  def logFlow[T] = Flow[T].map { line ⇒
    println(line.toString.take(80) + "...")
    line
  }

  import pprint._
  def pretty[T: PPrint](x: T): String = {
    import Config.Defaults._
    tokenize(x).mkString
  }
}
