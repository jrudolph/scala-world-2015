package example.repoanalyzer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.HttpRequest
import akka.stream.ActorMaterializer
import akka.stream.io.Framing
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn
import scala.util.{ Failure, Success }

object StepX extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val logStreamRequest = HttpRequest(uri = "http://localhost:9002/log")
  val logStreamResponseFuture = Http().singleRequest(logStreamRequest) // Future[HttpResponse]
  val logLinesStreamFuture =
    logStreamResponseFuture.map { response ⇒
      response.entity.dataBytes
        .via(Gzip.decoderFlow)
        .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 10000, allowTruncation = true))
        .map(_.utf8String)
        .mapConcat(line ⇒ RepoAccess.fromLine(line).toList)
        .recover {
          case e ⇒
            // helps when a connection fails
            e.printStackTrace()
            throw e
        }
    }

  logLinesStreamFuture.onComplete {
    case Success(logStream) ⇒
      //logStream.runForeach(println).onComplete(println)
      logStream.runWith(Sink.ignore).onComplete(println)
    case Failure(e) ⇒
      println("Request failed")
      e.printStackTrace()
      system.shutdown()
  }

  StdIn.readLine()
  system.shutdown()
  system.awaitTermination()
}
