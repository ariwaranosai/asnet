package datafragment
import akka.util.ByteString

/**
  * Created by ariwaranosai on 2016/10/31.
  *
  */

object SizedByteFrame extends ByteFragment[(Int, Option[String])]{
  override def fromBytes: Extractor =
    new PartialFunction[Seq[Byte], ((Int, Option[String]), Seq[Byte])] {
      def getLength(d: Seq[Byte]) = (d.head.toInt, d.tail)
      def isDefinedAt(d: Seq[Byte]) =  {
        val (size, data) = getLength(d.tail)
        (d.head == 0x00) && (size <= data.length)
      }

      def apply(s: Seq[Byte]) = {
        val raw = s.tail
        val (size, data) = getLength(raw)

        if(size > 0) {
          val str = ByteString(data.slice(0, size).toArray).utf8String
          ((size, Some(str)), data.slice(size, data.length))
        } else
          ((0, None), raw.tail)
      }
    }

  override def toBytes(value: (Int, Option[String])): ByteString = {
    val head = 0x00.toByte
    value._2 match {
      case Some(raw) =>
        head +: value._1.toByte +: ByteString(raw.toArray.map(_.toByte))
      case None =>
        head +: ByteString(0.toByte)
    }
  }
}
