scalaVersion := "2.13.8"

val akkaVersion = "2.6.19"
libraryDependencies ++= Seq(
  "com.typesafe.akka"  %% "akka-stream"                              % akkaVersion,
  "com.typesafe.akka"  %% "akka-slf4j"                               % akkaVersion,
  "ch.qos.logback"      % "logback-classic"                          % "1.2.11",
  "com.typesafe.akka"  %% "akka-stream-testkit"                      % akkaVersion % Test,
  "org.scalatest"      %% "scalatest"                                % "3.2.12"    % Test,
  "com.lightbend.akka" %% "akka-stream-alpakka-google-cloud-pub-sub" % "3.0.4"
)

scalafmtOnCompile := true
fork              := true
