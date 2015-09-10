package example.logservice

import akka.http.scaladsl.coding.Gzip
import akka.stream.scaladsl.{ Source, Sink }
import akka.util.ByteString

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.concurrent.{ Future, Await }

object Main extends App {
  implicit val system = ActorSystem("LogService")
  implicit val fm = ActorMaterializer()

  val binding = Http().bindAndHandleAsync({
    case req @ HttpRequest(GET, Uri.Path("/logs"), _, _, _) ⇒
      Future.successful(
        Gzip.encode(
          HttpResponse(entity = HttpEntity.CloseDelimited(MediaTypes.`text/plain`, tailLog()))))
    case req ⇒
      req.entity.dataBytes.runWith(Sink.ignore)
      Future.successful(HttpResponse(404, entity = "Not found!"))
  }, interface = "localhost", port = 9002)

  def tailLog(): Source[ByteString, Unit] = {
    import sys.process._
    Source(() ⇒ "tail -f /opt/spray.io/site.log".lineStream_!.map(str ⇒ ByteString(str + "\n", "utf8")).iterator)
  }
}
