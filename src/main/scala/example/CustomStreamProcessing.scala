package example

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.stream._
import akka.stream.scaladsl.{GraphDSL, Sink, Source, Zip}

import scala.concurrent.ExecutionContext.Implicits.global

/**
  * Created by ariwaranosai on 16/10/27.
  *
  */

/*
final class NumbersSource extends GraphStage[SourceShape[Int]] {
  val out: Outlet[Int] = Outlet("NumberOutlet")
  override def shape: SourceShape[Int] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = {

  }

}
*/

final class CustomOutGraphStage extends GraphStage[SourceShape[Int]] {
  val out = Outlet[Int]("OutProxy.out")

  override def shape: SourceShape[Int] = SourceShape(out)
  private var current = 1

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {

      setHandler(out, new OutHandler {
        override def onPull(): Unit = {
          push(out, current)
          current += 1
        }
      })
    }
}


final class CustomSinkProcessing extends GraphStage[SinkShape[Int]] {
  val in = Inlet[Int]("Sink.in")

  override def shape: SinkShape[Int] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      override def preStart(): Unit = pull(in)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          println(grab(in))
          pull(in)
        }
      })
    }
}


object OutSource {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("SourceOut")
    implicit val materializer = ActorMaterializer()

    val sourceGraph: Graph[SourceShape[Int], NotUsed] = new CustomOutGraphStage
    val sinkGraph: Graph[SinkShape[Int], NotUsed] = new CustomSinkProcessing

    val source = Source.fromGraph(sourceGraph)
    val sink = Sink.fromGraph(sinkGraph)

    source.runWith(sink)
  }
}

