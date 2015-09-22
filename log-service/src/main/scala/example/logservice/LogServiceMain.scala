package example.logservice

import java.io.File
import java.util.concurrent.atomic.AtomicLong
import akka.stream.io.{ SynchronousFileSource, Framing }
import scala.concurrent.duration._
import scala.concurrent.{ Promise, Future }
import akka.http.scaladsl.coding.Gzip
import akka.stream.scaladsl.{ Flow, Source, Sink }
import akka.stream.stage._
import akka.util.ByteString
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import akka.pattern.after

object LogServiceMain extends App {
  implicit val system = ActorSystem("LogService")
  implicit val materializer = ActorMaterializer()
  import system.dispatcher

  val config = system.settings.config.getConfig("log")
  val file = config.getString("file")
  val port = config.getInt("port")
  val mode = config.getString("mode")
  val needsUngzipping = file.endsWith(".gz")
  def logStreamBase() =
    mode match {
      case "tail"        ⇒ tailStream
      case "replay"      ⇒ replayStream(replayGaps)
      case "replay-fast" ⇒ replayStream(dontReplayGaps)
      case _             ⇒ sys.error(s"Invalid configured mode `$mode`")
    }
  def logStream() =
    logStreamBase()
      .conflate[Either[Int, ByteString]](Right(_)) {
        case (Right(_), _) ⇒ Left(2)
        case (Left(n), _)  ⇒ Left(n + 1)
      }
      .map {
        case Right(bytes) ⇒ bytes
        case Left(n)      ⇒ ByteString(s"Dropped $n lines")
      }
      .map { x ⇒
        println(x.utf8String.slice(6, 80) + "...")
        x
      }

  println(s"Serving '$file' at port $port in mode $mode")

  val binding = Http().bindAndHandleSync(handler, interface = "localhost", port = port)
  //logStream.runForeach(bytes ⇒ println(bytes.utf8String))

  def handler: HttpRequest ⇒ HttpResponse = {
    case req @ HttpRequest(GET, Uri.Path("/log"), _, entity, _) ⇒
      drain(entity)
      HttpResponse(entity = HttpEntity.Chunked.fromData(MediaTypes.`text/plain`, logStream()))
    case req ⇒
      drain(req.entity)
      HttpResponse(404, entity = "Not found!")
  }

  def drain(entity: HttpEntity): Unit = entity.dataBytes.runWith(Sink.ignore)

  lazy val tailStream: Source[ByteString, Any] = {
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

    val publisher = Source.repeat(0)
      .mapAsync(1)(_ ⇒ readOne())
      .takeWhile(_.nonEmpty)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 10000, allowTruncation = true))
      .transform(() ⇒ new PushStage[ByteString, ByteString] {
        def onPush(elem: ByteString, ctx: Context[ByteString]): SyncDirective = ctx.push(elem ++ ByteString('\n'))
        override def postStop(): Unit = {
          println("Destroying")
          proc.destroy()
        }
      })
      .runWith(Sink.fanoutPublisher(16, 128))
    val source = Source(publisher)
    source.runWith(Sink.ignore) // "keep-alive" subscriber
    source
  }

  def replayStream(gaps: Flow[(String, Long), String, Any]): Source[ByteString, Any] = {
    // 09/16 19:26:02.123 DEBUG[lt-dispatcher-8] repo|:|404|:|37.140.181.3|:|Nexus/2.6.0-05 (OSS; Linux; 3.10.69-25; amd64; 1.7.0_80) apacheHttpClient4x/2.6.0-05|:|http://repo.spray.io/org/freemarker/freemarker/maven-metadata.xml
    fileLinesStream
      .mapConcat {
        case line @ r"""(\d+)$m/(\d+)$d (\d+)$h:(\d+)$mm:(\d+)$s\.(\d+)$ss.*""" ⇒
          val clicks = DateTime(2015, m.toInt, d.toInt, h.toInt, mm.toInt, s.toInt).clicks + ss.toInt
          List(line -> clicks)
        case line ⇒
          // println("log line regex mismatch on line:\n" + line)
          Nil
      }
      .via(gaps)
      .map(line ⇒ ByteString(line))
  }

  def replayGaps: Flow[(String, Long), String, Any] = {
    val lastClicks = new AtomicLong(Long.MaxValue)
    Flow[(String, Long)].mapAsync(1) {
      case (line, clicks) ⇒
        val gap = clicks - lastClicks.getAndSet(clicks)
        val successful = Future.successful(line + '\n')
        if (gap > 0) after(gap.millis, system.scheduler)(successful)
        else successful
    }
  }

  def dontReplayGaps = Flow[(String, Long)].map {
    case (line, _) ⇒
      val start = System.nanoTime()
      while (System.nanoTime() - start < 5000000) {}
      line + '\n'
  }

  def fileLinesStream: Source[String, Any] =
    SynchronousFileSource(new File(file))
      .via(maybeUnGzip)
      .via(Framing.delimiter(ByteString("\n"), maximumFrameLength = 10000))
      .map(_.utf8String)

  def maybeUnGzip: Flow[ByteString, ByteString, Unit] =
    if (needsUngzipping) Gzip.decoderFlow else Flow[ByteString]

  implicit class Regex(sc: StringContext) {
    def r = new util.matching.Regex(sc.parts.mkString, sc.parts.tail.map(_ ⇒ "x"): _*)
  }
}