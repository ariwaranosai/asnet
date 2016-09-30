name := "asnet"

version := "1.0"

scalaVersion := "2.11.8"

libraryDependencies ++=  {
  val akkaVersion = "2.4.10"
  Seq(
    "com.typesafe.akka" %% "akka-actor" % akkaVersion,
    "com.typesafe.akka" %% "akka-stream" % akkaVersion
  )
}