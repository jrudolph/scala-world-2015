package example.repoanalyzer

import java.io.File

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import spray.json.DefaultJsonProtocol._

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

class FreeGeoIPResolver()(implicit system: ActorSystem, materializer: Materializer) {
  import system.dispatcher

  val cache = JsonCache.create[String, IPInfo](new File("ip-cache"), identity, getInfoFor)
  def infoFor(ip: String): Future[Option[IPInfo]] =
    noneIfFailed(orTimeoutIn(5000.millis, cache(ip)))

  def getFromCache(ip: String): Future[Option[IPInfo]] =
    Future.successful(cache.get(ip))

  def getInfoFor(ip: String): Future[IPInfo] = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    Http().singleRequest(freeGeoIpRequest(ip))
      .flatMap { res ⇒
        Unmarshal(res).to[IPInfo]
      }
  }

  def orTimeoutIn[T](duration: FiniteDuration, underlying: Future[T]): Future[T] =
    Future.firstCompletedOf(Seq(underlying,
      akka.pattern.after(duration, system.scheduler)(Future.failed(
        new RuntimeException(s"Timed out after $duration.")))))

  def noneIfFailed[T](f: Future[T]): Future[Option[T]] = {
    val p = Promise[Option[T]]()
    f.onComplete {
      case Success(s) ⇒ p.success(Some(s))
      case Failure(e) ⇒ p.success(None)
    }
    p.future
  }

  def freeGeoIpRequest(ip: String): HttpRequest = HttpRequest(uri = s"http://freegeoip.net/json/$ip")
}

