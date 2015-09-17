package example.repoanalyzer

import spray.json.{ JsString, JsValue, JsonFormat }

class IP(val address: String) extends AnyVal {
  override def toString: String = address.take(5) + ".XXX.XXX"
}
object IP {
  def apply(address: String): IP = new IP(address)
  implicit def ipFormat: JsonFormat[IP] =
    new JsonFormat[IP] {
      def read(json: JsValue): IP = json match {
        case JsString(ip) ⇒ new IP(ip)
      }
      def write(obj: IP): JsValue = JsString(obj.address)
    }
}
