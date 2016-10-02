package example

import java.nio.file.Paths

import akka.{NotUsed, stream}
import akka.stream._
import akka.stream.scaladsl._
import akka.actor.ActorSystem
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._

/**
  * Created by sai on 2016/10/2.
  */
object QuickStart {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("QuickStart")
    implicit val materializer = ActorMaterializer()

    val source: Source[Int, NotUsed] = Source(1 to 100)
    /*
    source.runForeach(i => println(i))
    */

    val factorials = source.scan(BigInt(1))((acc, next) => acc * next)

    /*
    val result: Future[IOResult] =
      factorials.map(num => ByteString(s"$num\n"))
      .runWith(FileIO.toPath(Paths.get("factorials.txt")))
      */

    factorials.map(_.toString).runWith(lineSink("factorials.txt"))

  }

  def lineSink(filename: String): Sink[String, Future[IOResult]] =
    Flow[String]
    .map(s => ByteString(s + "\n"))
    .toMat(FileIO.toPath(Paths.get(filename)))(Keep.right) // right用来保证Flow[A, B]和Sink[A, C]中选B还是C

}

object TimeBasedProcess {
  def main(args: Array[String]): Unit = {

  }
}