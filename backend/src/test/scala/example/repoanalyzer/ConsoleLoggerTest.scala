package example.repoanalyzer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

import scala.util.{ Failure, Success }

object ConsoleLoggerTest extends App {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val logStreamer = new LogStreamer()

  logStreamer.requestSemanticLogLines().onComplete {
    case Success(logStream) ⇒ logStream.runForeach(println)
    case Failure(e) ⇒
      println("Request failed")
      e.printStackTrace()
      system.shutdown()
  }
}
