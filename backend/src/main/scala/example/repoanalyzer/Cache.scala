package example.repoanalyzer

import java.io.{ FileOutputStream, File }

import spray.json.{ JsValue, RootJsonFormat, JsonFormat }

import scala.concurrent.{ ExecutionContext, Future }
import scala.io.Source

trait Cache[K, E] {
  def apply(t: K): Future[E]
}

object JsonCache {
  import spray.json._

  def create[K: JsonFormat, E: JsonFormat](
    rootDir: File,
    fileName: K ⇒ String,
    operation: K ⇒ Future[E])(implicit ec: ExecutionContext): Cache[K, E] = {
    rootDir.mkdirs()

    def load(file: File): CacheEntry[K, E] =
      Source.fromFile(file).mkString.parseJson.convertTo[CacheEntry[K, E]]

    new Cache[K, E] {
      def apply(key: K): Future[E] = {
        val cached = new File(rootDir, fileName(key) + ".cached")
        // TODO: avoid double calculation

        if (cached.exists()) Future.successful(load(cached).result)
        else {
          val res = operation(key)
          res.foreach { r ⇒
            val tmp = File.createTempFile("cache", ".tmp")
            val fos = new FileOutputStream(tmp)
            try {
              fos.write(CacheEntry(key, r).toJson.prettyPrint.getBytes("utf8"))
              tmp.renameTo(cached)
            } finally {
              fos.close()
              tmp.delete()
            }
          }
          res
        }
      }
    }
  }

  case class CacheEntry[K, E](key: K, result: E)
  object CacheEntry {
    import spray.json.DefaultJsonProtocol._
    implicit def cacheEntryFormat[K: JsonFormat, E: JsonFormat]: RootJsonFormat[CacheEntry[K, E]] =
      jsonFormat2(CacheEntry.apply)
  }
}
