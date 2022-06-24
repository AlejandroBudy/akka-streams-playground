package part2_primer

import akka.Done
import akka.actor.ActorSystem
import akka.stream.SystemMaterializer
import akka.stream.scaladsl.{Flow, Keep, RunnableGraph, Sink, Source}

import scala.concurrent.Future
import scala.util.{Failure, Success}

object MaterializingStream extends App {

  implicit val system       = ActorSystem("MaterializingStream")
  implicit val materializer = SystemMaterializer(system)
  import system.dispatcher

  val simpleGraph = Source(1 to 10).to(Sink.foreach(println))
  // val simpleMaterializedValue = simpleGraph.run()

  val source = Source(1 to 10)
  val sink   = Sink.reduce[Int]((a, b) => a + b)

  // Materialize Future
//  val sumFuture = source.runWith(sink)
//  sumFuture.onComplete {
//    case Failure(exception) => println(s"Failed")
//    case Success(value)     => println(s"Success sum $value")
//  }

  // choosing materialized values
  val simpleSource = Source(1 to 10)
  val simpleFlow   = Flow[Int].map(x => x + 1)
  val simpleSink   = Sink.foreach[Int](println)
  // simpleSource.viaMat(simpleFlow)((sourceMat, flowMat) => flowMat)
  val graph: RunnableGraph[Future[Done]] =
    simpleSource.viaMat(simpleFlow)(Keep.right).toMat(simpleSink)(Keep.right)

  graph.run().onComplete {
    case Failure(exception) => println("Stream processing failed")
    case Success(value)     => println("Stream processing finished")
  }

  // Sugars
  val sum: Future[Int] =
    Source(1 to 10).runWith(Sink.reduce[Int](_ + _)) // source.to(Sink.reduce)(Keep.right)
  Source(1 to 10).runReduce(_ + _)

  // backwards
  Sink
    .foreach[Int](println)
    .runWith(Source.single(42)) // source(...)to(sink..).run() <- Always Source materializer

  // both ways
  Flow[Int].map(x => 2 * x).runWith(simpleSource, simpleSink)

  /**
   *   - return the last element out of a source (use Sink.last)
   *   - compute the total word count out of a stream of sentence
   *   - map, fold, reduce
   */

  val lastElem = Source(1 to 10).runWith(Sink.last[Int])
  val wordCount = Source(List("I am learning Akka", "No idea what i am doing"))
    .map(_.split(" ").length)
    .runFold(0)(_ + _)
}
