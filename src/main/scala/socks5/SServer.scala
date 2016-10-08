package socks5

import java.nio.ByteBuffer

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.{Flow, Framing, Sink, Source, Tcp}
import akka.stream.scaladsl.Tcp.{IncomingConnection, ServerBinding}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler, OutHandler}
import akka.util.ByteString

import scala.concurrent.Future
import scala.language.implicitConversions
import scala.util.{Failure, Success}

/**
  * Created by ariwaranosai on 16/10/8.
  *
  */

object SServer {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("EchoServer")
    val (address, port) = ("127.0.0.1", 7070)

    server(system, address, port)
  }

  def server(system: ActorSystem, address: String, port: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind(address, port)

    val echo: Flow[ByteString, ByteString, Unit] = {

      val delimiter = Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true
      )

    }

    val handler = Sink.foreach[IncomingConnection] {
      conn =>
        println(s"New connection from ${conn.remoteAddress}")
        conn handleWith echo
    }

    val binding = connections.to(handler).run()


    binding.onComplete {
      case Success(b) =>
        println(s"Server started at port ${b.localAddress}")
      case Failure(e) =>
        println(s"Server could not bind to $address:$port: ${e.getMessage}")
        system.terminate()
    }
  }
}

trait ByteFragment[T] {
  final type Extractor = PartialFunction[Seq[Byte], (T, Seq[Byte])]

  def fromBytes: Extractor

  def toBytes(value: T): ByteString

  final def unapply(bytes: Seq[Byte]): Option[(T, Seq[Byte])] = {
    bytes match {
      case bs if fromBytes.isDefinedAt(bs) =>
        Some(fromBytes(bs))
      case _ => None
    }
  }
}

object ByteFragment {
  def wrap(f: => ByteBuffer): ByteString = {
    val buffer = f
    buffer.flip()
    ByteString(buffer)
  }

  def create(size: Int)(f: ByteBuffer => ByteBuffer): ByteString = {
    wrap(f(ByteBuffer.allocate(size)))
  }

  implicit def bytesToByteString(b: Seq[Byte]): ByteString = ByteString(b.toArray)
}

final class ProxyServerStage extends GraphStage[FlowShape[ByteString, ByteString]] {
  val in = Inlet[ByteString]("ProxyServer.in")
  val out = Outlet[ByteString]("ProxyServer.out")

  val maxBufferSize = 4096
  var buffer = ByteString.empty

  @scala.throws[Exception](classOf[Exception])
  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {
    setHandler(in, new InHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPush(): Unit = ???

    })
    setHandler(out, new OutHandler {
      @scala.throws[Exception](classOf[Exception])
      override def onPull(): Unit = ???
    })
  }

  override def shape: FlowShape[ByteString, ByteString] = FlowShape(in, out)
}
