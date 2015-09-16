package example.repoanalyzer

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.coding.Gzip
import akka.http.scaladsl.model.{ HttpResponse, HttpRequest }
import akka.stream.Materializer
import akka.stream.io.Framing
import akka.stream.scaladsl.{ Flow, Source }
import akka.util.ByteString
import scala.concurrent.Future

object LogStream {
  def requestLogLines()(implicit system: ActorSystem, materializer: Materializer): Future[Source[String, Any]] = {
    import system.dispatcher

    val request = HttpRequest(uri = "http://[::1]:9002/logs")
    Http().singleRequest(request).map(extractLinesFromRequest)
  }

  def extractLinesFromRequest(response: HttpResponse): Source[String, Any] =
    response.entity.dataBytes
      .via(Gzip.decoderFlow)
      .via(Framing.delimiter(ByteString("\n"), 10000, true))
      .map(_.utf8String)

  def requestParsedLogLines()(implicit system: ActorSystem, materializer: Materializer): Future[Source[RepoLogEntry, Any]] =
    requestLogLines()
      .map(
        _.map(RepoLogEntry.parseFromLine)
          .collect {
            case entry: RepoLogEntry ⇒ entry
            // ignore unreadable entries
          })(system.dispatcher)

  def requestSemanticLogLines()(implicit system: ActorSystem, materializer: Materializer): Future[Source[AccessEntryWithGroup, Any]] =
    requestParsedLogLines()
      .map(
        _.map(RepositorySearchEntry.fromLogEntry)
          .collect {
            case a: AccessEntryWithGroup ⇒ a
          })(system.dispatcher)

  case class GroupCountUpdate(groupId: String, updatedCount: Long)
  object GroupCountUpdate {
    import spray.json.DefaultJsonProtocol._
    implicit val updateFormat = jsonFormat2(GroupCountUpdate.apply _)
  }

  def groupCountUpdates: Flow[AccessEntryWithGroup, GroupCountUpdate, Unit] = {
    case class CountState(
        lastKey: Option[String],
        counts: Map[String, Long]) {
      def increment(key: String): CountState =
        new CountState(Some(key),
          counts.updated(key, counts(key) + 1))
    }
    def initialState: CountState = CountState(None, Map.empty.withDefaultValue(0L))

    Flow[AccessEntryWithGroup]
      .scan(initialState)(_ increment _.groupId)
      .collect {
        case CountState(Some(lastKey), counts) ⇒ GroupCountUpdate(lastKey, counts(lastKey))
      }
  }
}
