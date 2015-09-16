package example.repoanalyzer

import java.util.regex.Pattern

case class ClientInfo(ip: String, userAgent: String)

sealed trait LogEntry
case class RepoLogEntry(
  timestampEpoch: Long,
  repository: String,
  statusCode: Int,
  clientInfo: ClientInfo,
  url: String) extends LogEntry
case class OtherEntry(logText: String) extends LogEntry

object RepoLogEntry {
  // format
  // 09/16 19:26:02 DEBUG[lt-dispatcher-8] repo|:|404|:|37.140.181.3|:|Nexus/2.6.0-05 (OSS; Linux; 3.10.69-25; amd64; 1.7.0_80) apacheHttpClient4x/2.6.0-05|:|http://repo.spray.io/org/freemarker/freemarker/maven-metadata.xml
  val LogEntryFormat = """(\d+/\d+ \d+:\d+:\d+) \w+\[[^\]]+\] (.*)""".r

  def parseFromLine(line: String): LogEntry = line match {
    case e @ LogEntryFormat(timestamp, entries) ⇒
      entries.split(Pattern.quote("|:|")) match {
        case Array(repo, status, ip, userAgent, url) ⇒ RepoLogEntry(0L, repo, status.toInt, ClientInfo(ip, userAgent), url)
      }
    case _ ⇒ OtherEntry(line)
  }
}