name := "akka-persistent-maprdb"

version := "0.1"

scalaVersion := "2.13.0"

val AkkaVersion = "2.5.23"
val ScalaTestVersion = "3.0.8"


libraryDependencies += "com.typesafe.akka" %% "akka-actor" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-persistence" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion
libraryDependencies += "com.typesafe.akka" %% "akka-stream" % AkkaVersion

libraryDependencies += "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test
libraryDependencies += "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test
libraryDependencies += "org.scalatest" %% "scalatest" % ScalaTestVersion % Test

libraryDependencies ++= Seq(
  "com.mapr.ojai" % "mapr-ojai-driver" % "6.1.0-mapr",
  "org.apache.hadoop" % "hadoop-client" % "2.7.0-mapr-1808",
  "org.ojai" % "ojai" % "3.0-mapr-1808",
  "org.ojai" % "ojai-scala" % "3.0-mapr-1808",

  "com.mapr.db" % "maprdb" % "6.1.0-mapr",
  "xerces" % "xercesImpl" % "2.11.0"
)