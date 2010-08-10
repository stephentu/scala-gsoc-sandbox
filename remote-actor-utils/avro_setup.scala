import scala.actors._
import Actor._
import remote._
import RemoteActor._

import remote_actors.avro._
import examples._

import remote_actors.gzip._

implicit val config = 
  new Configuration with HasNonBlockingMode with HasAvroMessageCreator {
    override def newSerializer() = new GZipSerializer(new MultiClassSpecificAvroSerializer)
    override val connectPolicy = ConnectPolicy.NoWait
  }

//implicit val config = new HasSingleClassAvroSerializer[Example] with HasNonBlockingMode
//implicit val config = new HasGZipSerializer with HasNonBlockingMode {
//  override def newUnderlyingSerializer() =
//    new SingleClassClientSpecificAvroSerializer[Example]
//}
