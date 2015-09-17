package example.repoanalyzer

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer

object FreeGeoIPResolverTest extends App {
  implicit val system = ActorSystem()
  import system.dispatcher
  implicit val materializer = ActorMaterializer()
  val geoIp = new FreeGeoIPResolver()

  geoIp.infoFor(IP("217.91.78.143")).onComplete { res ⇒
    println(res)
    system.shutdown()
  }
}