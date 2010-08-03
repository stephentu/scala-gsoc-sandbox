import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import remote_actors.gzip._

implicit val config = new HasGZipSerializer with HasNonBlockingMode {
  override def newUnderlyingSerializer() = new JavaSerializer(null)
}
