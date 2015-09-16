package example.repoanalyzer

import akka.actor.ActorSystem
import akka.http.impl.util.JavaMapping.HttpMethod
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{ HttpMethods, HttpRequest }
import akka.stream.ActorMaterializer
import akka.stream.io.Framing
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.{ Failure, Success }

object ConsoleLogger /*extends App*/ {
  implicit val system = ActorSystem()
  implicit val materiazlier = ActorMaterializer()
  import system.dispatcher

  LogStream.requestSemanticLogLines().onComplete {
    case Success(logStream) ⇒ logStream.runForeach(println)
    case Failure(e) ⇒
      println("Request failed")
      e.printStackTrace()
      system.shutdown()
  }
}
