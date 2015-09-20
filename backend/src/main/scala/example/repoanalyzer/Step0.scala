package example.repoanalyzer

import scala.util.{ Failure, Success }
import scala.io.StdIn
import scala.concurrent.Future
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest

object Step0 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val logStreamRequest = HttpRequest(uri = "http://localhost:9002/log")
  val logStreamResponseFuture = Http().singleRequest(logStreamRequest) // Future[HttpResponse]
  val logLinesStreamFuture: Future[Source[String, Any]] =
    logStreamResponseFuture.map { response ⇒
      response.entity.dataBytes // Source[ByteString, Any]
        // .via(Gzip.decoderFlow)
        .map(_.utf8String) // Source[String, Any]
    }

  logLinesStreamFuture.onComplete {
    case Success(logStream) ⇒ logStream.runForeach(println)
    case Failure(e) ⇒
      println("Request failed")
      e.printStackTrace()
      system.shutdown()
  }

  StdIn.readLine()
  system.shutdown()
  system.awaitTermination()
}
