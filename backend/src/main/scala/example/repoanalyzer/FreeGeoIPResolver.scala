package example.repoanalyzer

import java.io.File

import scala.concurrent.duration._

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import spray.json.RootJsonFormat

import scala.concurrent.{ Promise, Future }
import scala.concurrent.duration.FiniteDuration
import scala.util.{ Failure, Success }

// {"ip":"192.30.252.129","country_code":"US","country_name":"United States","region_code":"CA","region_name":"California","city":"San Francisco","zip_code":"94107","time_zone":"America/Los_Angeles","latitude":37.77,"longitude":-122.394,"metro_code":807}
case class IPInfo(
  ip: IP,
  country_code: String,
  country_name: String,
  region_code: String,
  region_name: String,
  city: String,
  zip_code: String,
  time_zone: String,
  latitude: Int,
  longitude: Int,
  metro_code: Int)
object IPInfo {
  import spray.json.DefaultJsonProtocol._

  implicit def infoFormat: RootJsonFormat[IPInfo] = jsonFormat11(IPInfo.apply)
}

class FreeGeoIPResolver()(implicit system: ActorSystem, materializer: Materializer) {
  import system.dispatcher

  val cache = JsonCache.create[IP, IPInfo](new File("ip-cache"), _.address, getInfoFor)
  def infoFor(ip: IP): Future[Option[IPInfo]] =
    noneIfFailed(orTimeoutIn(10.millis, cache(ip)))

  def getInfoFor(ip: IP): Future[IPInfo] = {
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

  def freeGeoIpRequest(ip: IP): HttpRequest = HttpRequest(uri = s"http://freegeoip.net/json/${ip.address}")
}

