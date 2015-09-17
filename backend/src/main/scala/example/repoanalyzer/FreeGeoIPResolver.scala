package example.repoanalyzer

import java.io.File

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.{ ActorMaterializer, Materializer }
import spray.json.{ JsString, JsValue, JsonFormat, RootJsonFormat }

import scala.concurrent.Future

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
  def infoFor(ip: IP): Future[IPInfo] = cache(ip)

  def getInfoFor(ip: IP): Future[IPInfo] = {
    import akka.http.scaladsl.marshallers.sprayjson.SprayJsonSupport._
    Http().singleRequest(freeGeoIpRequest(ip))
      .flatMap { res ⇒
        Unmarshal(res).to[IPInfo]
      }
  }

  def freeGeoIpRequest(ip: IP): HttpRequest = HttpRequest(uri = s"http://freegeoip.net/json/${ip.address}")
}

