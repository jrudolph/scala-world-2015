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