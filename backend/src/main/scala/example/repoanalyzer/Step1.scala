package example.repoanalyzer

import scala.io.StdIn
import scala.util.{ Success, Failure }
import scala.concurrent.Future
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Source
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.util.ByteString

object Step1 extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionService = system.dispatcher

  val logStreamRequest = HttpRequest(uri = "http://localhost:9002/log")
  def logLinesStreamFuture: Future[Source[String, Any]] =
    Http().singleRequest(logStreamRequest).map { // Future[HttpResponse]
      _.entity.dataBytes
        // .via(Gzip.decoderFlow)
        .map(_.utf8String)
    }

  val config = system.settings.config.getConfig("app")
  val interface = config.getString("interface")
  val port = config.getInt("port")

  val route =
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

  val binding = Http().bindAndHandle(route, interface, port)

  binding.onComplete {
    case Success(x) ⇒
      println(s"Server is listening on ${x.localAddress.getHostName}:${x.localAddress.getPort}")
    case Failure(e) ⇒
      println(s"Binding failed with ${e.getMessage}")
  }

  StdIn.readLine()
  system.shutdown()
  system.awaitTermination()
}
