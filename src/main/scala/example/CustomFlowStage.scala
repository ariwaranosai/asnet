package example

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Flow, Sink, Source, Tcp}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString
import datafragment.SizedByteFrame

import scala.concurrent.Future
import scala.util.{Failure, Success}

/**
  * Created by sai on 2016/10/31.
  */
class CustomFlowStage extends GraphStage[FlowShape[ByteString, String]] {
  val in = Inlet[ByteString]("Custom.in")
  val out = Outlet[String]("Custom.out")

  override def shape: FlowShape[ByteString, String] = FlowShape(in, out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic =
    new GraphStageLogic(shape) {
      val bufferSize = 8192
      var buffer = ByteString.empty

      def writeBuffer(data: ByteString): Unit = {
        if(buffer.length > bufferSize)
          failStage(BufferOverflowException("Buffer Overflow"))
        else
          buffer ++= data
      }

      override def preStart(): Unit = {
        super.preStart()
        pull(in)
      }

      setHandler(in, new InHandler {
        def processBuffer(): Unit =
          buffer match {
            case SizedByteFrame((s, data), raw) => {
              data match {
                case Some(str) => emit(out, str, () => pull(in))
                case None => pull(in)
              }
              buffer = ByteString(raw:_*)
            }
            case _ => pull(in)
          }

        override def onPush(): Unit = {
          val data = grab(in)
          writeBuffer(data)
          processBuffer()
        }
      })

      setHandler(out, eagerTerminateOutput)
    }
}

object CustomTest {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorMaterializer()

    val source = Source.repeat(0.toByte +: 12.toByte +: ByteString("hello world!")).map(x => {println(x); x})
    val server: Flow[ByteString, String, NotUsed] = Flow.fromGraph(new CustomFlowStage)
    val sink: Sink[String, Future[Done]] = Sink.foreach(println(_))
    source.via(server).to(sink).run()
  }
}


object Main {
  def main(args: Array[String]): Unit = {
    val system = ActorSystem("Server")
    server(system, "127.0.0.1", 7070)
  }

  def server(system: ActorSystem, address: String, port: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val connection: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind(address, port)

    val server: Flow[ByteString, String, NotUsed] = Flow.fromGraph(new CustomFlowStage)

    val handler = Sink.foreach[IncomingConnection] {
      conn =>
        println(s"New connection from ${conn.remoteAddress}")
        conn handleWith server.map(s => {println(s); ByteString(s + "\n")})
    }

    val binding = connection.to(handler).run()

    binding.onComplete {
      case Success(b) =>
        println(s"Server started at port ${b.localAddress}")
      case Failure(e) =>
        println(s"Server could not bind to $address:$port: ${e.getMessage}")
        system.terminate()
    }
  }
}