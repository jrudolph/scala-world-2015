package example.repoanalyzer

sealed trait AccessEntry {
  def requestInfo: RequestInfo
}

sealed trait AccessEntryWithGroup extends AccessEntry {
  def groupId: String
}
object AccessEntryWithGroup {
  import spray.json._
  import spray.json.DefaultJsonProtocol._
  implicit def clientInfoFormat = jsonFormat2(ClientInfo)
  implicit def requestInfoFornat = jsonFormat3(RequestInfo)
  implicit def artifactEntryFormat = jsonFormat7(ArtifactAccessEntry)
  implicit def metadataEntryFormat = jsonFormat2(MetadataAccessEntry)
  implicit def snapshotMetadataEntryFormat = jsonFormat4(SnapshotMetadataAccessEntry)

  implicit def entryFormat: RootJsonFormat[AccessEntryWithGroup] =
    new RootJsonFormat[AccessEntryWithGroup] {
      def read(json: JsValue): AccessEntryWithGroup =
        throw new UnsupportedOperationException
      def write(obj: AccessEntryWithGroup): JsValue = obj match {
        case a: ArtifactAccessEntry         ⇒ a.toJson
        case m: MetadataAccessEntry         ⇒ m.toJson
        case s: SnapshotMetadataAccessEntry ⇒ s.toJson
      }
    }
}

case class RequestInfo(
  timestampEpoch: Long,
  clientInfo: ClientInfo,
  statusCode: Int)

case class ArtifactAccessEntry(
  requestInfo: RequestInfo,
  repo: String,
  groupId: String,
  moduleId: String,
  version: String,
  extra: Option[String],
  //artifactName: String,
  fileExtension: String) extends AccessEntryWithGroup

case class MetadataAccessEntry(requestInfo: RequestInfo,
                               groupId: String) extends AccessEntryWithGroup
case class SnapshotMetadataAccessEntry(requestInfo: RequestInfo,
                                       groupId: String,
                                       module: String,
                                       version: String) extends AccessEntryWithGroup
case class UnknownAccessEntry(repoLogEntry: RepoLogEntry, requestInfo: RequestInfo) extends AccessEntry

object RepositorySearchEntry {
  //http://repo.spray.io/org/springframework/spring-expression/maven-metadata.xml
  // http://repo.spray.io/org/apache/directory/api/api-parent/1.0.0-M20/api-parent-1.0.0-M20.jar

  val SimpleEntryFormat =
    """http://(\w+)\.spray\.io/(.*)/([^/]+)\.([^\.]+)""".r

  val ArtifactFormat =
    """http://(\w+)\.spray\.io/(.*)/([^/]+)/([^/]+)/\3-\4(?:-([^\.]+))?\.([^\.]+)""".r

  val ArtifactSnapshotFormat =
    """http://(\w+)\.spray\.io/(.*)/([^/]+)/([^/]+)-SNAPSHOT/\3-\4-(.+)(?:-([^\.]+))?\.([^\.]+)""".r

  val MetadataSnapshotAccessFormat =
    """http://(\w+)\.spray\.io/(.*)/([^/]+)/([^/]+-SNAPSHOT)/maven-metadata.xml""".r

  val MetadataAccessFormat =
    """http://(\w+)\.spray\.io/(.*)/maven-metadata.xml""".r

  def fromLogEntry(entry: RepoLogEntry, ipInfo: IPInfo): AccessEntry = {
    val requestInfo = RequestInfo(entry.timestampEpoch, ClientInfo(ipInfo, entry.userAgent), entry.statusCode)

    entry.url match {
      case ArtifactFormat(repo, groupElements, module, version, extra, extension) ⇒
        ArtifactAccessEntry(
          requestInfo,
          repo,
          groupElements.split('/').mkString("."),
          module,
          version,
          Some(extra).filter(e ⇒ (e ne null) && e.trim().nonEmpty),
          extension)
      case ArtifactSnapshotFormat(repo, groupElements, module, version, snapVersion, extra, extension) ⇒
        ArtifactAccessEntry(
          requestInfo,
          repo,
          groupElements.split('/').mkString("."),
          module,
          version,
          Some(extra).filter(e ⇒ (e ne null) && e.trim().nonEmpty),
          extension)
      case MetadataSnapshotAccessFormat(repo, groupElements, module, version) ⇒
        SnapshotMetadataAccessEntry(
          requestInfo,
          groupElements.split('/').mkString("."),
          module,
          version)
      case MetadataAccessFormat(repo, groupElements) ⇒
        MetadataAccessEntry(
          requestInfo,
          groupElements.split('/').mkString("."))
      case e @ SimpleEntryFormat(repo, pathElements, fileName, extension) ⇒
        println(s"Got $repo '$pathElements' '$fileName' '$extension' for '$e'")
        UnknownAccessEntry(entry, requestInfo)
      case other ⇒
        println(s"Got other: '$other'")
        UnknownAccessEntry(entry, requestInfo)
    }
  }
}
