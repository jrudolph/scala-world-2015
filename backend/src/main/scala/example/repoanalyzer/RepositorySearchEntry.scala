package example.repoanalyzer

import spray.json.{ JsValue, RootJsonFormat }

sealed trait AccessEntry {
  def timestampEpoch: Long
  def statusCode: Int
}

sealed trait AccessEntryWithGroup extends AccessEntry {
  def groupId: String
}
object AccessEntryWithGroup {
  import spray.json._
  import spray.json.DefaultJsonProtocol._
  implicit def artifactEntryFormat = jsonFormat8(ArtifactAccessEntry)
  implicit def metadataEntryFormat = jsonFormat3(MetadataAccessEntry)
  implicit def snapshotMetadataEntryFormat = jsonFormat5(SnapshotMetadataAccessEntry)

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

case class ArtifactAccessEntry(
  timestampEpoch: Long,
  statusCode: Int,
  repo: String,
  groupId: String,
  moduleId: String,
  version: String,
  extra: Option[String],
  //artifactName: String,
  fileExtension: String) extends AccessEntryWithGroup

case class MetadataAccessEntry(timestampEpoch: Long,
                               statusCode: Int,
                               groupId: String) extends AccessEntryWithGroup
case class SnapshotMetadataAccessEntry(timestampEpoch: Long,
                                       statusCode: Int,
                                       groupId: String,
                                       module: String,
                                       version: String) extends AccessEntryWithGroup
case class UnknownAccessEntry(repoLogEntry: RepoLogEntry) extends AccessEntry {
  def timestampEpoch: Long = repoLogEntry.timestampEpoch
  def statusCode: Int = repoLogEntry.statusCode
}

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

  def fromLogEntry(entry: RepoLogEntry): AccessEntry = entry.url match {
    case ArtifactFormat(repo, groupElements, module, version, extra, extension) ⇒
      ArtifactAccessEntry(
        entry.timestampEpoch,
        entry.statusCode,
        repo,
        groupElements.split('/').mkString("."),
        module,
        version,
        Some(extra).filter(e ⇒ (e ne null) && e.trim().nonEmpty),
        extension)
    case ArtifactSnapshotFormat(repo, groupElements, module, version, snapVersion, extra, extension) ⇒
      ArtifactAccessEntry(
        entry.timestampEpoch,
        entry.statusCode,
        repo,
        groupElements.split('/').mkString("."),
        module,
        version,
        Some(extra).filter(e ⇒ (e ne null) && e.trim().nonEmpty),
        extension)
    case MetadataSnapshotAccessFormat(repo, groupElements, module, version) ⇒
      SnapshotMetadataAccessEntry(
        entry.timestampEpoch,
        entry.statusCode,
        groupElements.split('/').mkString("."),
        module,
        version)
    case MetadataAccessFormat(repo, groupElements) ⇒
      MetadataAccessEntry(
        entry.timestampEpoch,
        entry.statusCode,
        groupElements.split('/').mkString("."))
    case e @ SimpleEntryFormat(repo, pathElements, fileName, extension) ⇒
      println(s"Got $repo '$pathElements' '$fileName' '$extension' for '$e'")
      UnknownAccessEntry(entry)
    case other ⇒
      println(s"Got other: '$other'")
      UnknownAccessEntry(entry)
  }
}
