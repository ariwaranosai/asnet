package example

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl._
import akka.stream.scaladsl.Tcp._
import akka.util.ByteString

import scala.concurrent.Future
import scala.io.StdIn._
import scala.util.{Failure, Success}

/**
  * Created by sai on 2016/9/30.
  */
object EchoServer {

  def main(args: Array[String]): Unit = {
    val system = ActorSystem("EchoServer")
    val (address, port) = ("127.0.0.1", 6000)

    server(system, address, port)
  }

  def server(system: ActorSystem, address: String, port: Int): Unit = {
    implicit val sys = system
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val connections: Source[IncomingConnection, Future[ServerBinding]] =
      Tcp().bind(address, port)


    val echo = Flow[ByteString]
      .via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true))
      .map(_.utf8String)
      .map(_ + "!!!\n")
      .map(ByteString(_))

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

object EchoClient {

  def main(args: Array[String]): Unit = {
    implicit val system = ActorSystem("EchoClient")
    import system.dispatcher
    implicit val materializer = ActorMaterializer()

    val connection = Tcp().outgoingConnection("127.0.0.1", 6000)

    val replParser =
      Flow[String].takeWhile(_ != "q")
      .concat(Source.single("BYE"))
      .map(elem => ByteString(s"$elem\n"))

    val repl = Flow[ByteString]
      .via(Framing.delimiter(
        ByteString("\n"),
        maximumFrameLength = 256,
        allowTruncation = true))
      .map(_.utf8String)
      .map(text => println("Server: " + text))
      .map(_ => readLine("> "))
      .via(replParser)

    connection.join(repl).run()
  }
}
