package example.repoanalyzer

sealed trait LogEntry
case class RepoLogEntry(
  timestampEpoch: Long,
  repository: String,
  statusCode: Int,
  url: String) extends LogEntry
case class OtherEntry(logText: String) extends LogEntry

object RepoLogEntry {
  // example entry:
  // 09/10 12:26:01 DEBUG[t-dispatcher-19] a.a.RepointableActorRef - nightlies 404: http://nightlies.spray.io/org/freemarker/freemarker/maven-metadata.xml
  val LogEntryFormat = """(\d+/\d+ \d+:\d+:\d+).* - (\w+) (\d{3}): (.*)""".r

  def parseFromLine(line: String): LogEntry = line match {
    case LogEntryFormat(timestamp, repo, status, url) ⇒ RepoLogEntry(0L, repo, status.toInt, url)
    case _ ⇒ OtherEntry(line)
  }
}