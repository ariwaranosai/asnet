package example

import java.nio.file.Paths

import akka.{Done, NotUsed, stream}
import akka.stream._
import akka.stream.scaladsl._
import akka.actor.{ActorSystem, Cancellable}
import akka.util.ByteString

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

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
    implicit val system = ActorSystem("TimeBasedProcess")
    implicit val materializer = ActorMaterializer()

    val source = Source(1 to 100).map(x => { println(x); x})
    val sink = Sink.fold[Int, Int](0)(_ + _)

    val runnable: RunnableGraph[Future[Int]] = source.toMat(sink)(Keep.right)

    val sum1 = runnable.run()
    val sum2 = runnable.run()

    (for {
      x <- sum1
      y <- sum2
    } yield x + y).onSuccess {
      case x => println(x)
    }
  }
}

object FlowStart {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("QuickStart")
    implicit val materializer = ActorMaterializer()

    /*
    val source = Source(1 to 100)
    val flow = Flow[Int].map(_ * 2)
    val otherFlow = Flow[Int].map(_ * 3)
    val sink = Sink.foreach[Int](println(_))
    val sum1 = source.via(flow).toMat(sink)(Keep.right)
    val sum2 = source.via(otherFlow).to(sink)

    sum1.run()

    val source: Source[Int, Promise[Option[Int]]] = Source.maybe[Int]

    val flow: Flow[Int, Int, Cancellable] = ???

    val sink: Sink[Int, Future[Int]] = Sink.head[Int]

    val r1: RunnableGraph[Promise[Option[Int]]] = source.via(flow).to(sink)

    val r2: RunnableGraph[Cancellable] = source.viaMat(flow)(Keep.right).to(sink)

    val g = RunnableGraph.fromGraph(GraphDSL.create() {
      implicit builder: GraphDSL.Builder[NotUsed] =>  {
        import GraphDSL.Implicits._
        val in = Source(1 to 10)
        val out = Sink.ignore

        val bcast = builder.add(Broadcast[Int](3))
        val merge = builder.add(Merge[Int](3))

        val f1, f2, f3 = Flow[Int].map(_ + 10)

        in ~> f1 ~> bcast ~> f2 ~> merge ~> f3 ~> out
                    bcast ~> f2 ~> merge
                    bcast ~> f2 ~> merge

        ClosedShape
      }
    })
    */

    val pickMaxOfThree = GraphDSL.create() {
      implicit b =>
        import GraphDSL.Implicits._

        val zip1 = b.add(ZipWith[Int, Int, Int](math.max))
        val zip2 = b.add(ZipWith[Int, Int, Int](math.max))

        zip1.out ~> zip2.in0

        UniformFanInShape(zip2.out, zip1.in0, zip1.in1, zip2.in1)
    }

    val resultSink = Sink.head[Int]

    val g = RunnableGraph.fromGraph(GraphDSL.create(resultSink) { implicit b => sink =>
      import GraphDSL.Implicits._

      val pm3 = b.add(pickMaxOfThree)

      Source.single(1) ~> pm3
      Source.single(2) ~> pm3 ~> sink
      Source.single(4) ~> pm3

      ClosedShape
    })

    g.run().onSuccess {
      case x => println(s" Success in $x")
    }

  }
}

object ConstructingSrouce {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("Constructing")
    implicit val materializer = ActorMaterializer()

    val pairs = Source.fromGraph(GraphDSL.create() { implicit b =>
      import GraphDSL.Implicits._

      val zip = b.add(Zip[Int, Int]())

      def ints = Source.fromIterator(() => Iterator.from(1))

      ints.filter(_ % 2 != 0) ~> zip.in0
      ints.filter(_ % 2 == 0) ~> zip.in1

      SourceShape(zip.out)
    })

    val firstPair = pairs.runWith(Sink.head)

    firstPair.onSuccess {
      case x =>
        println(s"first is ${x._1}, second is ${x._2}")
    }
  }
}

object SSSourcingFlow {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("Constructing")
    implicit val materializer = ActorMaterializer()

    val pairFlowString =
      Flow.fromGraph(GraphDSL.create(){ implicit b =>
        import GraphDSL.Implicits._

        val broadcast = b.add(Broadcast[Int](2))
        val zip = b.add(Zip[Int, String]())

        broadcast.out(0).map(identity) ~>  zip.in0
        broadcast.out(1).map(_.toString) ~>  zip.in1

        FlowShape(broadcast.in, zip.out)
      })

    val sourceSS =
      RunnableGraph.fromGraph(GraphDSL.create(){ implicit b =>
        import GraphDSL.Implicits._

        val s1 = Source(1 to 10)
        val s2 = Source(100 to 110)

        s1 ~> pairFlowString
        s2 ~> pairFlowString ~> Sink.head[(Int, String)]

        ClosedShape
      })

    val s1 = Source(List(2))
    val s2 = Source(List(3))

    val merged = Source.combine(s1, s2)(Merge(_))

    val mergedResult = merged.runWith(Sink.fold(0)(_ + _))

  }
}