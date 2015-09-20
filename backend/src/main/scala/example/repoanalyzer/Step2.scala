package example.repoanalyzer

import scala.concurrent.Future
import akka.util.ByteString
import akka.stream.scaladsl.Source
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._

object Step2 extends Scaffolding with App {

  val logLinesStreamFuture: Future[Source[String, Any]] =
    logStreamResponseFuture.map { response ⇒
      response.entity.dataBytes // Source[ByteString, Any]
        // .via(Gzip.decoderFlow) // Source[ByteString, Any]
        .map(_.utf8String) // Source[String, Any]
    }

  runWebService {
    get {
      onSuccess(logLinesStreamFuture) { stream ⇒
        complete {
          HttpResponse(
            entity = HttpEntity.Chunked(
              MediaTypes.`text/plain`,
              stream.map(line ⇒ ByteString(line + '\n', "UTF8"))))
        }
      }
    }
  }
}
