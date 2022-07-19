package part4_techniques

import akka.actor.{Actor, ActorLogging, ActorSystem, Props}
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.util.Timeout

import java.util.Date
import scala.concurrent.Future

object IntegratingWithExternalServices extends App {

  implicit val system       = ActorSystem("IntegratingWithExternalServices")
  implicit val materializer = SystemMaterializer(system)
  // import system.dispatcher // use custom execution context
  implicit val dispatcher = system.dispatchers.lookup("dedicated-dispatcher")

  def genericExtService[A, B](element: A): Future[B] = ???

  // example: Simplified PagerDuty
  case class PagerEvent(application: String, description: String, date: Date)
  val eventSource = Source(
    List(
      PagerEvent("AkkaInfra", "broken infra", new Date),
      PagerEvent("AkkaCluster", "cluster infra", new Date),
      PagerEvent("AkkaInfra", "type level broken infra", new Date),
      PagerEvent("KotlinMS", "kotlin ms infra", new Date),
      PagerEvent("frontend", " frontend broken infra", new Date)
    )
  )

  object PagerService {
    private val engineers = List("John", "Cobra", "Batu")
    private val emails = Map(
      "John"  -> "john@cobra.com",
      "Cobra" -> "cobra@cobra.com",
      "Batu"  -> "batu@batu.com"
    )

    def processEvent(pagerEvent: PagerEvent): Future[String] = Future {
      val index    = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(index.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a notification $pagerEvent")

      Thread.sleep(1000)

      engineerEmail
    }
  }

  val infraEvents          = eventSource.filter(_.application == "AkkaInfra")
  val pagedEngineersEmails = infraEvents.mapAsync(parallelism = 1)(PagerService.processEvent)
  // mapAsync the relative order of elements
  val pagedEmailSink =
    Sink.foreach[String](email => println(s"Successfully sent notification to $email"))

 // pagedEngineersEmails.to(pagedEmailSink).run()

  class PagerActor extends Actor with ActorLogging {
    private val engineers = List("John", "Cobra", "Batu")
    private val emails = Map(
      "John"  -> "john@cobra.com",
      "Cobra" -> "cobra@cobra.com",
      "Batu"  -> "batu@batu.com"
    )

    def processEvent(pagerEvent: PagerEvent): String =  {
      val index    = (pagerEvent.date.toInstant.getEpochSecond / (24 * 3600)) % engineers.length
      val engineer = engineers(index.toInt)
      val engineerEmail = emails(engineer)

      // page the engineer
      println(s"Sending engineer $engineerEmail a notification $pagerEvent")

      Thread.sleep(1000)

      engineerEmail
    }

    override def receive: Receive = { case pagerEvent: PagerEvent =>
      sender() ! processEvent(pagerEvent)
    }
  }

  import akka.pattern.ask

  import scala.concurrent.duration._
  implicit val timeout = Timeout(5.seconds)
  val pagerActor       = system.actorOf(Props[PagerActor], "pagerActor")
  val alternativePagedEngineerEmails =
    infraEvents.mapAsync(parallelism = 4)(event => (pagerActor ? event).mapTo[String])
  alternativePagedEngineerEmails.to(pagedEmailSink).run()
}
