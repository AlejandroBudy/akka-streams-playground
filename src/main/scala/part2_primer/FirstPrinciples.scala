package part2_primer

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.{Flow, RunnableGraph, Sink, Source}

import scala.concurrent.Future

object FirstPrinciples extends App {

  implicit val system       = ActorSystem("FirstPrinciples")
  implicit val materializer = SystemMaterializer(ActorSystem("FirstPrinciples"))

  // source
  val source = Source(1 to 10)
  // sink
  val sink = Sink.foreach[Int](println)

  val graph: RunnableGraph[NotUsed] = source.to(sink)
  // graph.run()

  // flows transforms elements
  val flow                                 = Flow[Int].map(x => x + 1)
  val sourceWithFlow: Source[Int, NotUsed] = source.via(flow)
  val flowWithSink                         = flow.to(sink)

  // sourceWithFlow.to(sink).run()
  // source.via(flow).to(sink).run()

  // nulls are NOT allowed
  // Use Options instead

  // various kind of sources
  val finiteSource        = Source.single(1)
  val anotherFiniteSource = Source(List(1, 2, 3, 4))
  val emptySource         = Source.empty[Int]
  val infiniteSource      = Source(LazyList.from(1))

  import scala.concurrent.ExecutionContext.Implicits.global
  val futureSource = Source.future(Future(42))

  // Sink
  val doesNothing = Sink.ignore
  val foreachSink = Sink.foreach[String](println)
  val headSink    = Sink.head[Int] // retrieves head and closes stream
  val foldSink    = Sink.fold[Int, Int](0)((a, b) => a + b)

  // flows - usually mapped to collectors operators
  val mapFlow  = Flow[Int].map(x => 2 * x)
  val takeFlow = Flow[Int].take(3)
  // drop, filter
  // NOT have flatMap

  // source -> flow -> ... -> sink
  val doubleFlowGraph = source.via(mapFlow).via(takeFlow).to(sink)

  // syntactic sugars
  val mapSource = Source(1 to 10).map(x => x * 2)

  // Runs stream directly
  mapSource.runForeach(println) // mapSource.to(Sink.foreach[Int](println)).run()

  // Operators = components
  /**
   * Exercise: Create a stream takes names then keep first two with length gt 5 chars
   */

  Source(List("Henry", "BudyBulldog", "KiraLabrador", "CapiLabrador"))
    .filter(_.length > 5)
    .take(3)
    .runForeach(println)

}
