package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorRef, ActorSystem, Props}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{OverflowStrategy, SystemMaterializer}
import akka.util.Timeout

import scala.concurrent.duration.DurationInt

object IntegratingWithActors extends App {

  implicit val system       = ActorSystem("IntegratingWithActors")
  implicit val materializer = SystemMaterializer(system)

  class SimpleActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case s: String =>
        log.info(s"Just received a string : $s")
        sender() ! s"$s$s"

      case n: Int =>
        log.info(s"Just received a number: $n")
        sender() ! (2 * n)
      case _ =>
    }
  }

  val simpleActor  = system.actorOf(Props[SimpleActor], "simpleActor")
  val numberSource = Source(1 to 10)

  // actor as a flow
  implicit val timeout: Timeout = Timeout(2.seconds)

  val actorBasedFlow = Flow[Int].ask[Int](parallelism = 4)(simpleActor)

  // numberSource.via(actorBasedFlow).to(Sink.ignore).run()

  // Shorter way
  // numberSource.ask[Int](parallelism = 4)(simpleActor).to(Sink.ignore).run()

  /*
  Actor as a source
   */
  val actorPoweredSource =
    Source.actorRef[Int](bufferSize = 10, overflowStrategy = OverflowStrategy.dropHead)

  // materialized Source -> ActorRef
  val materializedValue: ActorRef = actorPoweredSource
    .to(
      Sink.foreach[Int](number => println(s"Actor powered flow got number: $number"))
    )
    .run()

  materializedValue ! 10
  // terminating
  materializedValue ! akka.actor.Status.Success("complete")

  /*
  Actor as destination/sink
  - an init message
  - an ack message to confirm the reception
  - a complete message
  - a function to generate message in case the stream throws an exception
   */

  case object StreamInit
  case object StreamAck
  case object StreamComplete
  case class StreamFailed(ex: Throwable)

  class DestinationActor extends Actor with ActorLogging {
    override def receive: Receive = {
      case StreamInit =>
        log.info(s"Stream initialized")
        sender() ! StreamAck
      case StreamComplete =>
        log.info("Stream completed")
        context.stop(self)
      case StreamFailed(ex) =>
        log.info(s"Stream failed: $ex")

      case message =>
        log.info(s"Message $message has come to its final resting point")
        //If StreamAck is not send sender will interpret as backpressure
        sender() ! StreamAck
    }
  }

  val destinationActor = system.actorOf(Props[DestinationActor], "destinationActor")
  val actorPoweredSink = Sink.actorRefWithBackpressure[Int](
    destinationActor,
    onInitMessage = StreamInit,
    ackMessage = StreamAck,
    onCompleteMessage = StreamComplete,
    onFailureMessage = ex => StreamFailed(ex)
  )

  Source(1 to 10).to(actorPoweredSink).run()

}
