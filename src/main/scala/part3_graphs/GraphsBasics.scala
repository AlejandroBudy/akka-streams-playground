package part3_graphs

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Balance,
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  RunnableGraph,
  Sink,
  Source,
  Zip
}
import akka.stream.{ClosedShape, SystemMaterializer}

object GraphsBasics extends App {

  implicit val system       = ActorSystem("GraphsBasics")
  implicit val materializer = SystemMaterializer(system)

  val input       = Source(1 to 1000)
  val incrementer = Flow[Int].map(_ + 1)
  val multiplier  = Flow[Int].map(_ * 10)

  val output = Sink.foreach[(Int, Int)](println)

  // step 1 -setting up fundamentals for the graph
  val graph = RunnableGraph.fromGraph(
    GraphDSL.create() {
      implicit builder: GraphDSL.Builder[NotUsed] => // builder = MUTABLE data structure
        import GraphDSL.Implicits._                  // brings somme nice operators into scope

        // step 2 add the necessary components of this graph
        val broadcast = builder.add(Broadcast[Int](2)) // fan-out operator: 1 input 2 output
        val zip       = builder.add(Zip[Int, Int])     // fan-in operator

        // step 3 - tying up the components
        input ~> broadcast // input feeds into broadcast
        broadcast.out(0) ~> incrementer ~> zip.in0
        broadcast.out(1) ~> multiplier ~> zip.in1
        zip.out ~> output

        // step 4 - return a closed shape
        ClosedShape // Freeze the builder's shape

    }
  )

  // graph.run()

  /**
   * Exercise 1: feed a source into 2 sink at the same time (hint: use broadcast)
   */

  val output1 = Sink.foreach[Int](x => println(s"Output1: $x"))
  val output2 = Sink.foreach[Int](x => println(s"Output2: $x"))
  val graphExercise1 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder: GraphDSL.Builder[NotUsed] =>
      import GraphDSL.Implicits._
      val broadcast = builder.add(Broadcast[Int](2))
      // input ~> broadcast
      // broadcast.out(0) ~> output1
      // broadcast.out(1) ~> output2
      input ~> broadcast ~> output1 // implicit port numbering
      broadcast ~> output2

      ClosedShape
    }
  )

  // graphExercise1.run()

  /**
   * Exercise 2: Build graph from specification
   */

  import scala.concurrent.duration._
  val fastSource = input.throttle(5, 1.second)
  val slowSource = input.throttle(2, 1.second)

  val graphExercise2 = RunnableGraph.fromGraph(
    GraphDSL.create() { implicit builder =>
      import GraphDSL.Implicits._

      val merge    = builder.add(Merge[Int](2))
      val balancer = builder.add(Balance[Int](2))

      fastSource ~> merge
      slowSource ~> merge

      merge ~> balancer

      balancer ~> output1
      balancer ~> output2

      ClosedShape

    }
  )

  graphExercise2.run()
}
