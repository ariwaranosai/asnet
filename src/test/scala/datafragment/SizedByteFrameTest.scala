package datafragment

import org.scalatest.{FlatSpec, Matchers}
import java.lang.NoSuchMethodException

/**
  * Created by sai on 2016/10/31.
  */
class SizedByteFrameTest extends FlatSpec with Matchers {
  "SizedByteFrame" should "encode and decode right" in {
    val raw = SizedByteFrame.toBytes((4, Some("hello")))

    raw match {
      case SizedByteFrame(((l, data), res)) =>
        data should be (Some("hell"))
      case _ => throw new NoSuchMethodException
    }
  }
}
