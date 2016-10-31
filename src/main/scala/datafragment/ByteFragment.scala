package datafragment

import java.nio.ByteBuffer

import akka.util.ByteString

/**
  * Created by ariwaranosai on 2016/10/31.
  *
  */

trait ByteFragment[T] {
  final type Extractor = PartialFunction[Seq[Byte], (T, Seq[Byte])]

  def fromBytes: Extractor

  def toBytes(value: T): ByteString

  final def unapply(bytes: ByteString): Option[(T, Seq[Byte])] =
    bytes match {
      case bs if fromBytes.isDefinedAt(bs) =>
        Some(fromBytes(bs))
      case _ => None
    }
}

object ByteFragment {
  def wrap(f: => ByteBuffer): ByteString = {
    val buffer = f
    buffer.flip()
    ByteString(buffer)
  }

  def create(size: Int)(f: ByteBuffer => ByteBuffer): ByteString =
    wrap(f(ByteBuffer.allocate(size)))

  implicit def bytesToByteString(b: Seq[Byte]): ByteString = ByteString(b.toArray)
}