package localhost.test 

import com.googlecode.avro.marker._

case object Stop
case object Start

case class SimpleAvroMessage(var str: String) extends AvroRecord
