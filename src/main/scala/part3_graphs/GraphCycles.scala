package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, GraphDSL, Merge, MergePreferred, RunnableGraph, Source}
import akka.stream.{ClosedShape, OverflowStrategy, SystemMaterializer}

object GraphCycles extends App {

  implicit val system       = ActorSystem("GraphCycles")
  implicit val materializer = SystemMaterializer(system)

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape  = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(accelerator).run() // Graph cycle deadlock! -> Backpressure

  // Solutions
  /*
  Solution 1: MergePreferred
   */

  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape  = builder.add(MergePreferred[Int](1))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape.preferred <~ incrementerShape

    ClosedShape
  }

  // RunnableGraph.fromGraph(actualAccelerator).run()

  /*
  Solution 2: Buffers
   */

  val bufferedAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val sourceShape = builder.add(Source(1 to 100))
    val mergeShape  = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      x
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape
  }

  RunnableGraph.fromGraph(bufferedAccelerator).run()
}
