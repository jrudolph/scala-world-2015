package example.logservice

import java.util.concurrent.atomic.AtomicLong
import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration._
import akka.http.scaladsl.coding.Gzip
import akka.stream.scaladsl.{ Source, Sink }
import akka.stream.stage._
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer

import scala.io.StdIn

object LogServiceMain extends App {
  implicit val system = ActorSystem("LogService")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val config = system.settings.config.getConfig("log-stream")
  val file = config.getString("file")
  val port = config.getInt("port")
  val mode = config.getString("mode")
  val logStream =
    mode match {
      case "tail"   ⇒ tailStream()
      case "replay" ⇒ replayStream()
      case _        ⇒ sys.error(s"Invalid configured mode `$mode`")
    }

  println(s"Serving '$file' at port $port in mode $mode")

  val binding = Http().bindAndHandleSync(handler, interface = "localhost", port = port)
  //logStream.runForeach(bytes ⇒ println(bytes.utf8String))

  StdIn.readLine()
  system.shutdown()
  system.awaitTermination()

  def handler: HttpRequest ⇒ HttpResponse = {
    case req @ HttpRequest(GET, Uri.Path("/log"), _, entity, _) ⇒
      drain(entity)
      Gzip.encode(HttpResponse(entity = HttpEntity.Chunked.fromData(MediaTypes.`text/plain`, logStream.log("A", _.utf8String))))
    case req ⇒
      drain(req.entity)
      HttpResponse(404, entity = "Not found!")
  }

  def drain(entity: HttpEntity): Unit = entity.dataBytes.runWith(Sink.ignore)

  def tailStream(): Source[ByteString, Any] = {
    val proc = new java.lang.ProcessBuilder()
      .command("tail", /*"-n100",*/ "-f", file)
      .start()
    proc.getOutputStream.close()
    val input = proc.getInputStream

    def readOne(): Future[ByteString] = Future {
      val buffer = new Array[Byte](8096)
      val read = input.read(buffer)
      if (read > 0) ByteString.fromArray(buffer, 0, read) else ByteString.empty
    }

    Source.repeat(0)
      .mapAsync(1)(_ ⇒ readOne())
      .takeWhile(_.nonEmpty)
      .transform(() ⇒ new PushStage[ByteString, ByteString] {
        def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = ctx.push(elem)
        override def postStop(): Unit = {
          println("Destroying")
          proc.destroy()
        }
      })
  }

  def replayStream(): Source[ByteString, Any] = {
    // 09/16 19:26:02.123 DEBUG[lt-dispatcher-8] repo|:|404|:|37.140.181.3|:|Nexus/2.6.0-05 (OSS; Linux; 3.10.69-25; amd64; 1.7.0_80) apacheHttpClient4x/2.6.0-05|:|http://repo.spray.io/org/freemarker/freemarker/maven-metadata.xml
    val lastClicks = new AtomicLong(Long.MaxValue)
    Source(() ⇒ io.Source.fromFile(file, "UTF8").getLines())
      .mapConcat {
        case line @ r"""(\d+)$m/(\d+)$d (\d+)$h:(\d+)$mm:(\d+)$s\.(\d+)$ss.*""" ⇒
          val clicks = DateTime(2015, m.toInt, d.toInt, h.toInt, mm.toInt, s.toInt).clicks + ss.toInt
          List(line -> clicks)
        case line ⇒
          println("log line regex mismatch on line:\n" + line)
          Nil
      }
      .mapAsync(1) {
        case (line, clicks) ⇒
          val gap = clicks - lastClicks.getAndSet(clicks)
          if (gap > 0) {
            val promise = Promise[String]()
            system.scheduler.scheduleOnce(gap.millis)(promise.success(line + '\n'))
            promise.future
          } else Future.successful(line + '\n')
      }
      .map(line ⇒ ByteString(line))
  }

  implicit class Regex(sc: StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ ⇒ "x"): _*)
  }
}