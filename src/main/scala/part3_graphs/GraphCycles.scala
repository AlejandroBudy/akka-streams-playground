package part3_graphs

import akka.actor.ActorSystem
import akka.stream.scaladsl.{
  Broadcast,
  Flow,
  GraphDSL,
  Merge,
  MergePreferred,
  RunnableGraph,
  Sink,
  Source,
  Zip
}
import akka.stream.{ClosedShape, OverflowStrategy, SystemMaterializer, UniformFanInShape}

object GraphCycles extends App {

  implicit val system       = ActorSystem("GraphCycles")
  implicit val materializer = SystemMaterializer(system)

  val accelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 1000))
    val mergeShape  = builder.add(Merge[Int](2))
    val incrementerShape = builder.add(Flow[Int].map { x =>
      println(s"Accelerating $x")
      x + 1
    })

    sourceShape ~> mergeShape ~> incrementerShape
    mergeShape <~ incrementerShape

    ClosedShape

  }

  // RunnableGraph.fromGraph(accelerator).run() // graph cycle deadlock

  /*
  Solution 1: MergePreferred
   */
  val actualAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 1000))
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
  Solution 2: buffers
   */

  val bufferedAccelerator = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._
    val sourceShape = builder.add(Source(1 to 1000))
    val mergeShape  = builder.add(Merge[Int](2))
    val repeater = builder.add(Flow[Int].buffer(10, OverflowStrategy.dropHead).map { x =>
      println(s"Accelerating $x")
      Thread.sleep(100)
      x
    })

    sourceShape ~> mergeShape ~> repeater
    mergeShape <~ repeater

    ClosedShape

  }

  // RunnableGraph.fromGraph(bufferedAccelerator).run()

  /**
   * Challenge: create a fan-in shape
   *   - two inputs which will be fed with EXACTLY ONE number (1 and 1)
   *   - output will emit an INFINITE FIBONACCI SEQUENCE based of those 2 numbers 1,2,3,5,8 ...
   *
   * Hint: Use ZipWith and cycles, MergePreferred
   */

  val fibonacciGraph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val inputZip = builder.add(Zip[BigInt, BigInt])
    val merge    = builder.add(MergePreferred[(BigInt, BigInt)](1))
    val logic = builder.add(Flow[(BigInt, BigInt)].map { pair =>
      val last     = pair._1
      val previous = pair._2
      Thread.sleep(100)
      (last + previous, last)
    })

    val broadcast   = builder.add(Broadcast[(BigInt, BigInt)](2))
    val extractLast = builder.add(Flow[(BigInt, BigInt)].map(_._1))

    inputZip.out ~> merge ~> logic ~> broadcast ~> extractLast
    merge.preferred <~ broadcast

    UniformFanInShape(extractLast.out, inputZip.in0, inputZip.in1)

  }

  val graph = GraphDSL.create() { implicit builder =>
    import GraphDSL.Implicits._

    val source1 = builder.add(Source.single[BigInt](1))
    val source2 = builder.add(Source.single[BigInt](1))
    val sink    = builder.add(Sink.foreach(println))
    val fibo    = builder.add(fibonacciGraph)

    source1 ~> fibo.in(0)
    source2 ~> fibo.in(1)

    fibo.out ~> sink

    ClosedShape
  }

  RunnableGraph.fromGraph(graph).run()
}
