package playground

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.alpakka.google.{GoogleAttributes, GoogleSettings}
import akka.stream.alpakka.googlecloud.pubsub.scaladsl.GooglePubSub
import akka.stream.alpakka.googlecloud.pubsub.{PubSubConfig, PublishMessage, PublishRequest}
import akka.stream.scaladsl.{Flow, Sink, Source}

import java.util.Base64
import scala.concurrent.Future

object PubSubGoogleStream extends App {

  implicit val system = ActorSystem("PubSubGoogleStream")
  import system.dispatcher

  sys.props.put("PUBSUB_EMULATOR_HOST", "localhost")
  sys.props.put("PUBSUB_EMULATOR_PORT", "8538")

  val config         = PubSubConfig()
  val googleSettings = GoogleSettings().withProjectId("alpakka")

  val publishMessage =
    PublishMessage(new String(Base64.getEncoder.encode("Hello Google 2!".getBytes)))

  val publishRequest = PublishRequest(Seq(publishMessage))

  val source: Source[PublishRequest, NotUsed] = Source.single(publishRequest)

  val publishFlow: Flow[PublishRequest, Seq[String], NotUsed] =
    GooglePubSub.publish("simpleTopic", config)

  val publishedMessagesIds: Future[Seq[Seq[String]]] =
    source
      .via(publishFlow)
      .addAttributes(GoogleAttributes.settings(googleSettings))
      .runWith(Sink.seq)

  publishedMessagesIds.foreach(ids => println(ids))

}
