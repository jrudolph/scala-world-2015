package example.logservice

import scala.concurrent.Future

import akka.http.scaladsl.coding.Gzip
import akka.stream.scaladsl.{ Flow, Source, Sink }
import akka.stream.stage._
import akka.util.ByteString

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.util.control.NoStackTrace

object LogServiceMain extends App {
  implicit val system = ActorSystem("LogService")
  import system.dispatcher
  implicit val fm = ActorMaterializer()

  val file = system.settings.config.getString("log.file")
  val port = 9002
  println(s"Serving '$file' at port $port")

  def handler: HttpRequest ⇒ Future[HttpResponse] = {
    case req @ HttpRequest(GET, Uri.Path("/logs"), _, entity, _) ⇒
      entity.dataBytes.runWith(Sink.ignore)
      val stream = tailLog()
      Future.successful(
        Gzip.encode(
          HttpResponse(entity = HttpEntity.Chunked.fromData(MediaTypes.`text/plain`, stream))))
    case req ⇒
      req.entity.dataBytes.runWith(Sink.ignore)
      Future.successful(HttpResponse(404, entity = "Not found!"))
  }
  val binding = Http().bindAndHandleAsync(handler, interface = "localhost", port = port)

  def tailLog(): Source[ByteString, Any] = {
    val proc = new java.lang.ProcessBuilder()
      .command("tail", /*"-n100",*/ "-f", file)
      .start()
    proc.getOutputStream.close()
    val input = proc.getInputStream

    def readOne(): Future[ByteString] = Future {
      val buffer = new Array[Byte](8096)
      val read = input.read(buffer)
      if (read > 0) ByteString.fromArray(buffer, 0, read)
      else throw CompleteException
    }

    Source.repeat(0).mapAsync(1)(_ ⇒ readOne()).transform(() ⇒ new PushStage[ByteString, ByteString] {
      def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = ctx.push(elem)

      override def onUpstreamFailure(cause: Throwable, ctx: Context[ByteString]): TerminationDirective = cause match {
        case CompleteException ⇒ ctx.finish()
      }

      override def postStop(): Unit = {
        println("Destroying")
        proc.destroy()
      }
    })
  }

  def printEvent[T](name: String): Flow[T, T, Unit] = Flow[T].log(name)
  //tailLog().runForeach(println)
}

case object CompleteException extends RuntimeException with NoStackTrace