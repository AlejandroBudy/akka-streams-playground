package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Broadcast, Concat, Flow, GraphDSL, Sink, Source}
import akka.stream.{FlowShape, SinkShape, SourceShape, SystemMaterializer}

object OpenGraphs extends App {

  implicit val system       = ActorSystem("OpenGraphs")
  implicit val materializer = SystemMaterializer(system)

  /*
  A composite source that concatenates 2 sources
  - emits ALL elements from the first source
  - then ALL the elements from the second
   */

  val firstSource  = Source(1 to 10)
  val secondSource = Source(42 to 1000)

  val sourceGraph = Source.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val concat = builder.add(Concat[Int](2))

      firstSource ~> concat
      secondSource ~> concat

      SourceShape(concat.out)
    }
  )

  // 1 to 10 first and then 42 to 1000
  // sourceGraph.to(Sink.foreach(println)).run()

  /*
  Complex sink
   */
  val sink1 = Sink.foreach[Int](x => println(s"Complex 1: $x"))
  val sink2 = Sink.foreach[Int](x => println(s"Complex 2: $x"))

  val sinkGraph = Sink.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))

      broadcast ~> sink1
      broadcast ~> sink2

      SinkShape(broadcast.in)
    }
  )

  // firstSource.to(sinkGraph).run()

  /**
   * Challenge - complex flow Write your own flow that is composed of two other flows
   *   - one that adds 1 to a number
   *   - one that does number * 10
   */
  val flow1 = Flow[Int].map(x => x + 1)
  val flow2 = Flow[Int].map(x => x * 10)
  val flowGraph = Flow.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      // everything operates on shapes
      val incrementerShape = builder.add(flow1)
      val multiplierShape  = builder.add(flow2)

      incrementerShape ~> multiplierShape

      FlowShape(incrementerShape.in, multiplierShape.out)

    }
  )

  firstSource.via(flowGraph).to(Sink.foreach(println)).run()

  def fromSinkAndSource[A, B](sink: Sink[A, _], source: Source[B, _]): Flow[A, B, _] =
    Flow.fromGraph(
      GraphDSL.create() { implicit builder =>
        val sourceShape = builder.add(source)
        val sinkShape   = builder.add(sink)

    //    FlowShape(sourceShape.in, sinkShape.out)
        ???
      }
    )

  val f = Flow.fromSinkAndSourceCoupled(Sink.foreach[String](println), Source(1 to 10))
}
