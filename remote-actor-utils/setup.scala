import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import remote_actors.avro._
import examples._

implicit val config = new HasMultiClassAvroSerializer with HasNonBlockingMode

// vim: set ts=4 sw=4 et:
