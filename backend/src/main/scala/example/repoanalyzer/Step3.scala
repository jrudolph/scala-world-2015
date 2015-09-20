package example.repoanalyzer

import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.io.Framing
import akka.util.ByteString

object Step3 extends Scaffolding with App {

  val logLinesStreamFuture =
    logStreamResponseFuture.map { response ⇒
      response.entity.dataBytes // Source[ByteString, Any]
        .via(Gzip.decoderFlow) // Source[ByteString, Any]
        .via(Framing.delimiter(ByteString("\n"),
          maximumFrameLength = 10000, allowTruncation = true))
        .map(_.utf8String) // Source[String, Any]
        .mapConcat(line ⇒ RepoAccess.fromLine(line).toList)
    }

  runWebService {
    get {
      onSuccess(logLinesStreamFuture) { stream ⇒
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked(
              MediaTypes.`text/plain`,
              stream.map(x ⇒ ByteString(x.toString, "UTF8"))))
        }
      }
    }
  }
}
