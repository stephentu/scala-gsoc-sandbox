import scala.actors._
import Actor._
import remote._
import RemoteActor.{actor => remoteActor, _}

import remote_actors.protobuf._
import examples.Example._

implicit val config = new HasProtobufSerializer with HasNonBlockingMode

def newExample(i: Int, s: String) =
    PBufExample.newBuilder().setI(i).setS(s).build()
