package example.repoanalyzer

import scala.io.StdIn
import scala.util.{ Success, Failure }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.server.Route
import akka.http.scaladsl.Http

class Scaffolding {
  implicit val system = ActorSystem()
  implicit val materializer = ActorMaterializer()
  implicit val executionService = system.dispatcher

  val logStreamRequest = HttpRequest(uri = "http://localhost:9002/log")

  def runWebService(route: Route): Unit = {
    val config = system.settings.config.getConfig("app")
    val interface = config.getString("interface")
    val port = config.getInt("port")

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
}