package example

import java.io.IOException

import akka.{Done, NotUsed}
import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source, Tcp}
import akka.stream.stage._
import akka.util.ByteString
import datafragment.SizedByteFrame

import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success}

/**
  * Created by sai on 2016/10/31.
  */

class CustomFlowStage extends GraphStageWithMaterializedValue[FlowShape[ByteString, String], Future[Int]] {
  val in = Inlet[ByteString]("Custom.in")
  val out = Outlet[String]("Custom.out")

  override def shape: FlowShape[ByteString, String] = FlowShape(in, out)

  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Int]) = {
    val promise = Promise[Int]()
    val logic = new GraphStageLogic(shape) {
      val bufferSize = 8192
      var buffer = ByteString.empty
      var count = 0

      def failWithException(throwable: Throwable) = {
        promise.tryFailure(throwable)
        failStage(throwable)
      }

      def writeBuffer(data: ByteString): Unit = {
        if (buffer.length > bufferSize)
          failWithException(BufferOverflowException("Buffer Overflow"))
        else
          buffer ++= data
      }

      override def preStart(): Unit = {
        super.preStart()
        pull(in)
      }

      override def postStop() = {
        promise.tryFailure(new IOException("Connection closed"))
        super.postStop()
      }

      def finishRequest(): Unit = {
        var resList = List[String]()
        while(buffer.nonEmpty) {
          buffer match {
             case SizedByteFrame((s, data), raw) => {
              data match {
                case Some(str) => resList = str :: resList
                case None => resList = "None" :: resList
              }
              buffer = ByteString(raw: _*)
            }
            case _ => failWithException(BufferOverflowException("Package Unexcepted"))
          }
        }

        emitMultiple(out, resList)
        count += resList.size
        promise.success(count)
      }

      setHandler(in, new InHandler {
        def processBuffer(): Unit = {
          buffer match {
            case SizedByteFrame((s, data), raw) => {
              data match {
                case Some(str) => emit(out, str, () => {count += 1; pull(in)})
                case None => emit(out, "None", () => {count += 1; pull(in)})
              }
              buffer = ByteString(raw: _*)
            }
            case d if d.head == 0x00 => pull(in)
            case _ => {
              failWithException(BufferOverflowException("Package Unexcepted"))
            }
          }
        }

        override def onPush(): Unit = {
          val data = grab(in)
          writeBuffer(data)
          processBuffer()
        }

        override def onUpstreamFinish(): Unit = {
          finishRequest()
          super.onUpstreamFinish()
          completeStage()
        }
      })

      setHandler(out, eagerTerminateOutput)
    }

    (logic, promise.future)
  }
}

object CustomTest {
  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorMaterializer()
    import scala.concurrent.ExecutionContext.Implicits.global

    val l = List.fill(5)(0.toByte +: 0.toByte +: 5.toByte +: ByteString("hello"))

    val source = Source.fromIterator(() => l.toIterator)
    val server: Flow[ByteString, String, Future[Int]] = Flow.fromGraph(new CustomFlowStage)
    val sink: Sink[String, Future[Done]] = Sink.foreach(println)
    val t = source.viaMat(server)(Keep.right).toMat(sink)(Keep.left).run()
    t.onComplete {
      case Success(x) => println(s"success with $x")
      case Failure(t) => println(s"failed with ${t.getMessage}")
    }
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

    val server: Flow[ByteString, String, Future[Int]] = Flow.fromGraph(new CustomFlowStage)

    val handler = Sink.foreach[IncomingConnection] {
      conn =>
        println(s"New connection from ${conn.remoteAddress}")
        val count = conn handleWith server.map(s => {println(s); ByteString(s + "\n")})
        count.onComplete {
          case Success(x) => println(s"$x lines")
          case Failure(t) => println(s"${conn.remoteAddress} failed with ${t.getMessage}")
        }
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