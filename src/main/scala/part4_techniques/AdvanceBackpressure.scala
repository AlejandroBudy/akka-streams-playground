package part4_techniques

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{OverflowStrategy, SystemMaterializer}

import java.util.Date
import scala.concurrent.duration.DurationInt

object AdvanceBackpressure extends App {
  implicit val system       = ActorSystem("AdvanceBackpressure")
  implicit val materializer = SystemMaterializer(system)

  // Controlled backpressure
  val controlledFlow = Flow[Int].map(_ * 2).buffer(10, OverflowStrategy.dropHead)

  case class PagerEvent(description: String, date: Date, nInstances: Int = 1)
  case class Notification(email: String, pagerEvent: PagerEvent)

  val events = List(
    PagerEvent("Service discovery failed", new Date),
    PagerEvent("Illegal elements in the data pipeline", new Date),
    PagerEvent("Number of HTTP 500 spiked", new Date),
    PagerEvent("A service stopped responding", new Date)
  )

  val eventSource    = Source(events)
  val onCallEngineer = "alex"

  def sendEmail(notification: Notification): Unit = println(
    s"Dear ${notification.email} you have an event: ${notification.pagerEvent}"
  )

  val notificationSink =
    Flow[PagerEvent].map(Notification(onCallEngineer, _)).to(Sink.foreach[Notification](sendEmail))

  // standard
  // eventSource.to(notificationSink).run()

  def sendEmailSlow(notification: Notification) = {
    Thread.sleep(1000)
    sendEmail(notification)
  }

  val aggregateNotificationFlow = Flow[PagerEvent]
    .conflate { (event1, event2) =>
      val nInstances = event1.nInstances + event2.nInstances
      PagerEvent(s"You have $nInstances events that require you attention", new Date, nInstances)
    }
    .map(Notification(onCallEngineer, _))

  // Alternative to backpressure
  eventSource
    .via(aggregateNotificationFlow)
    .async // Run in separate actor
    .to(Sink.foreach[Notification](sendEmailSlow))
    .run()

  /*
  Slow producers: extrapolate/expand
   */
  val slowCounter = Source(LazyList.from(1)).throttle(1, 1.second)
  val fastSink    = Sink.foreach[Int](println)

  val extrapolator = Flow[Int].extrapolate(element => Iterator.from(element))

  // slowCounter.via(extrapolator).to(fastSink).run()

  val expander = Flow[Int].expand(element => Iterator.from(element))
}
