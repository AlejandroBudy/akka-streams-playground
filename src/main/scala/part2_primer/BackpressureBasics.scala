package part2_primer

import akka.actor.ActorSystem
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{OverflowStrategy, SystemMaterializer}

object BackpressureBasics extends App {

  implicit val system       = ActorSystem("BackpressureBasics")
  implicit val materializer = SystemMaterializer(system)

  val fastSource = Source(1 to 1000)
  val slowSink = Sink.foreach[Int] { x =>
    Thread.sleep(1000)
    println(s"Sink: $x")
  }

  // Not backpressure, each element is printed one by one in same actor
  // fastSource.to(slowSink).run()
  // Backpressure
  // fastSource.async.to(slowSink).run()

  val simpleFlow = Flow[Int].map { x =>
    println(s"Incoming $x")
    x + 1
  }

//  fastSource.async
//    .via(simpleFlow)
//    .async
//    .to(slowSink)
//    .run()

  /*
    reactions to backpressure (in order):
    - try to slow down if possible
    - buffer elements until there's more demand
    - drop down elements from the buffer if it overflows
    - tear down/kill  the whole stream (failure)
   */

  val bufferedFlow = simpleFlow.buffer(10, overflowStrategy = OverflowStrategy.dropHead)

  /*
  1-16: Nobody is backpressured
  17-26: flow will buffer, flow will start dropping at the next element
  26-1000: flow will always drop the oldest element
   */
  fastSource.async
    .via(bufferedFlow)
    .async
    .to(slowSink)
    .run()

  /*
  Sends elements downstream with speed limited to elements/per.
  In other words, this operator set the maximum rate for emitting messages.
  This operator works for streams where all elements have the same cost or length.
   */
  import scala.concurrent.duration._
  fastSource.throttle(2, 1.seconds).runWith(Sink.foreach(println))
}
