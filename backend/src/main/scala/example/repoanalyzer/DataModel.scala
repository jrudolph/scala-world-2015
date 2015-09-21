package example.repoanalyzer

import akka.http.scaladsl.model.DateTime

case class RepoAccess(
    timestamp: Long,
    repository: String,
    responseStatusCode: Int,
    ip: String,
    userAgent: String,
    url: String,
    ipInfo: Option[IPInfo],
    accessType: Option[AccessType]) {

  def groupId = accessType.map(_.groupId)
  def userAgentProduct: String = userAgent.split("/").headOption getOrElse ""
  def withIPInfo(ipInfo: IPInfo) = copy(ipInfo = Some(ipInfo))
}

object RepoAccess {
  // 09/18 16:32:58.850 DEBUG[t-dispatcher-15] repo|:|404|:|116.94.155.132|:|Nexus/1.9.0.2 (OSS; Linux; 2.6.32-33-server; amd64; 1.6.0_31) apacheHttpClient3x/1.9.0.2|:|http://repo.spray.io/org/slf4j/log4j-over-slf4j/maven-metadata.xml
  val LogLineFormat = """(\d+)/(\d+) (\d+):(\d+):(\d+)\.(\d+) \w+\[[^\]]+\] ([a-z]+)\|:\|(\d+).*\|:\|(.+)\|:\|(.+)\|:\|(.+)""".r

  // http://repo.spray.io/org/apache/directory/api/api-parent/1.0.0-M20/api-parent-1.0.0-M20.jar
  // http://repo.spray.io/com/typesafe/akka/akka-actor_2.11/2.4-SNAPSHOT/akka-actor_2.11-2.4-SNAPSHOT.pom
  val ArtifactFormat = """http://\w+\.spray\.io/(.*)/([^/]+)/([^/]+)/.+""".r

  // http://nightlies.spray.io/org/springframework/spring-tx/maven-metadata.xml
  // http://repo.spray.io/com/typesafe/akka/akka-actor_2.11/2.4-SNAPSHOT/maven-metadata.xml
  val MetadataAccessFormat = """http://\w+\.spray\.io/(.*)/maven-metadata.xml""".r

  val NonArtifactFormat = """http://\w+\.spray\.io/(.*)/([^/]+)\.([^\.]+)""".r

  val fromLine: String ⇒ Option[RepoAccess] = {
    case line @ LogLineFormat(m, d, h, mm, s, ss, repo, status, ip, userAgent, url) ⇒
      val timestamp = DateTime(2015, m.toInt, d.toInt, h.toInt, mm.toInt, s.toInt).clicks + ss.toInt
      val accessType = url match {
        case ArtifactFormat(groupPath, module, version, extension) ⇒
          Some(ArtifactAccess(groupPath.replace('/', '.'), module, version, extension))
        case MetadataAccessFormat(groupPath) ⇒
          Some(MetadataAccess(groupPath.replace('/', '.')))
        case NonArtifactFormat(path, fileName, extension) ⇒
          //println(s"Dropped(1): $line\n  [$repo] [$path] [$fileName] [$extension]")
          None
        case _ ⇒
          //println("Dropped(2): " + line)
          None
      }
      Some(RepoAccess(timestamp, repo, status.toInt, ip, userAgent, url, None, accessType))

    case line ⇒
      //println("Dropped(3): " + line)
      None
  }
}

sealed trait AccessType {
  def groupId: String
}

case class ArtifactAccess(
  groupId: String,
  moduleId: String,
  version: String,
  fileExtension: String) extends AccessType

case class MetadataAccess(groupId: String) extends AccessType

import spray.json._
import spray.json.DefaultJsonProtocol._

object AccessType {
  implicit def f0 = jsonFormat4(ArtifactAccess)
  implicit def f1 = jsonFormat1(MetadataAccess)
  implicit def f3: RootJsonFormat[AccessType] =
    new RootJsonFormat[AccessType] {
      def read(json: JsValue): AccessType = throw new UnsupportedOperationException
      def write(obj: AccessType): JsValue = obj match {
        case a: ArtifactAccess ⇒ a.toJson
        case m: MetadataAccess ⇒ m.toJson
      }
    }
}

// {"ip":"192.30.252.129","country_code":"US","country_name":"United States","region_code":"CA","region_name":"California","city":"San Francisco","zip_code":"94107","time_zone":"America/Los_Angeles","latitude":37.77,"longitude":-122.394,"metro_code":807}
case class IPInfo(
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
  implicit def infoFormat: RootJsonFormat[IPInfo] = jsonFormat10(IPInfo.apply)
}