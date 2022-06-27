package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Flow, GraphDSL, Keep, Sink, Source}
import akka.stream.{FlowShape, SinkShape, SystemMaterializer}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object GraphMaterializedValues extends App {

  implicit val system       = ActorSystem("MoreOpenGraphs")
  implicit val materializer = SystemMaterializer(system)

  val wordSource = Source(List("Akka", "is", "awesome"))
  val printer    = Sink.foreach[String](println)
  val counter    = Sink.fold[Int, String](0)((count, _) => count + 1)

  /*
  A composite component (sink):
  - prints out all strings which are lowercase
  - counts the strings that are short (<5 chars)
   */

  val complexWordSink = Sink.fromGraph(
    // Exposing materializer from counter => Keep.right
    GraphDSL.createGraph(counter) { implicit builder => counterShape =>
      import GraphDSL.Implicits._

      val broadcast         = builder.add(Broadcast[String](2))
      val lowerCase         = builder.add(Flow[String].filter(x => x == x.toLowerCase))
      val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

      broadcast ~> lowerCase ~> printer
      broadcast ~> shortStringFilter ~> counterShape

      SinkShape(broadcast.in)

    }
  )

  import system.dispatcher

  val future: Future[Int] = wordSource.toMat(complexWordSink)(Keep.right).run()
  future.onComplete {
    case Failure(exception) => println(s"Counting failed $exception")
    case Success(value)     => println(s"Count is $value")
  }

  val mixedMat = Sink.fromGraph(
    // Exposing both Materializer and return the compose materializer
    GraphDSL.createGraph(printer, counter)((pMat, cMat) => cMat) {
      implicit builder => (printerShape, counterShape) =>
        import GraphDSL.Implicits._

        val broadcast         = builder.add(Broadcast[String](2))
        val lowerCase         = builder.add(Flow[String].filter(x => x == x.toLowerCase))
        val shortStringFilter = builder.add(Flow[String].filter(_.length < 5))

        broadcast ~> lowerCase ~> printerShape
        broadcast ~> shortStringFilter ~> counterShape

        SinkShape(broadcast.in)

    }
  )

  /**
   * Exercise
   */
  def enhanceFlow[A, B](flow: Flow[A, B, _]): Flow[A, B, Future[Int]] = {
    val folder = Sink.fold[Int, B](0)((counter, _) => counter + 1)
    Flow.fromGraph(
      GraphDSL.createGraph(folder) { implicit builder => foldShape =>
        import GraphDSL.Implicits._

        val broadcast = builder.add(Broadcast[B](2))
        val flowShape = builder.add(flow)

        flowShape ~> broadcast ~> foldShape

        FlowShape(flowShape.in, broadcast.out(1))
      }
    )
  }

  val simpleSource = Source(1 to 28)
  val simpleFlow   = Flow[Int].map(x => x)
  val simpleSink   = Sink.ignore

  val enhanceFlowCountFuture: Future[Int] =
    simpleSource.viaMat(enhanceFlow(simpleFlow))(Keep.right).toMat(simpleSink)(Keep.left).run()

  enhanceFlowCountFuture.onComplete {
    case Failure(exception) => println(s"enhance Counting failed $exception")
    case Success(value)     => println(s"enhance Count is $value")
  }
}
