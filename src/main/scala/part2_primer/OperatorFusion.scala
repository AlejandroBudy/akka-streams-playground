package part2_primer

import akka.actor.ActorSystem
import akka.stream.SystemMaterializer

object OperatorFusion extends App {

  implicit val system       = ActorSystem("OperatorFusion ")
  implicit val materializer = SystemMaterializer(system)
}
