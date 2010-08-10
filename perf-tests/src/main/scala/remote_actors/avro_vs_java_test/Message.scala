package remote_actors
package avro_vs_java_test

import com.googlecode.avro.marker._

case class TestMessage(var b: Array[Byte]) extends AvroRecord

case class TestMessage0(var i1: Inner1, var i2: Inner2) extends AvroRecord

case class Inner1(var s: String) extends AvroRecord

case class Inner2(var l: Long) extends AvroRecord
