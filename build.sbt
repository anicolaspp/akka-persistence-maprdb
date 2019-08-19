import ReleaseTransformations._

name := "akka-persistence-maprdb"

scalaVersion := "2.12.8"

organization in ThisBuild := "com.github.anicolaspp"

val AkkaVersion = "2.5.23"
val ScalaTestVersion = "3.0.8"

lazy val akkaPersistenceMapRDB = project.in(file("."))
  .settings(
    homepage := Some(url("https://github.com/anicolaspp/akka-persistence-maprdb")),

    scmInfo := Some(ScmInfo(url("https://github.com/anicolaspp/akka-persistence-maprdb"), "git@github.com:anicolaspp/akka-persistence-maprdb.git")),

    pomExtra := <developers>
      <developer>
        <name>Nicolas A Perez</name>
        <email>anicolaspp@gmail.com</email>
        <organization>anicolaspp</organization>
        <organizationUrl>https://github.com/anicolaspp</organizationUrl>
      </developer>
    </developers>,

    licenses += ("MIT License", url("https://opensource.org/licenses/MIT")),

    publishMavenStyle := true,

    publishTo in ThisBuild := Some(
      if (isSnapshot.value)
        Opts.resolver.sonatypeSnapshots
      else
        Opts.resolver.sonatypeStaging
    ),

    publishArtifact in Test := false,

    pomIncludeRepository := { _ => true },

    releasePublishArtifactsAction := PgpKeys.publishSigned.value,

    releaseProcess := Seq[ReleaseStep](
      checkSnapshotDependencies, // : ReleaseStep
      inquireVersions, // : ReleaseStep
      runClean, // : ReleaseStep
      runTest, // : ReleaseStep
      setReleaseVersion, // : ReleaseStep
      commitReleaseVersion, // : ReleaseStep, performs the initial git checks
      tagRelease, // : ReleaseStep
      publishArtifacts, // : ReleaseStep, checks whether `publishTo` is properly set up
      setNextVersion, // : ReleaseStep
      commitNextVersion, // : ReleaseStep
      pushChanges // : ReleaseStep, also checks that an upstream branch is properly configured
    ),

    resolvers += "MapR Releases" at "http://repository.mapr.com/maven/",

    libraryDependencies ++= Seq(
      "com.typesafe.akka" %% "akka-actor" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence" % AkkaVersion,
      "com.typesafe.akka" %% "akka-persistence-query" % AkkaVersion,
      "com.typesafe.akka" %% "akka-stream" % AkkaVersion,

      "com.typesafe.akka" %% "akka-slf4j" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-persistence-tck" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-stream-testkit" % AkkaVersion % Test,
      "com.typesafe.akka" %% "akka-testkit" % AkkaVersion % Test,
      "org.scalatest" %% "scalatest" % ScalaTestVersion % Test,

      "com.mapr.ojai" % "mapr-ojai-driver" % "6.1.0-mapr",
      "org.apache.hadoop" % "hadoop-client" % "2.7.0-mapr-1808",
      "org.ojai" % "ojai" % "3.0-mapr-1808",
      "org.ojai" % "ojai-scala" % "3.0-mapr-1808",

      "com.mapr.db" % "maprdb" % "6.1.0-mapr",
      "xerces" % "xercesImpl" % "2.11.0",

      "com.github.anicolaspp" % "ojai-testing_2.12" % "1.0.8"
    )
      .map(_.exclude("org.slf4j", "slf4j-log4j12")),

    parallelExecution in Test := false
  )


addCompilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)

assemblyMergeStrategy in assembly := {
  case PathList("org", "apache", "spark", "unused", xs@_*) => MergeStrategy.last
  case x =>
    val oldStrategy = (assemblyMergeStrategy in assembly).value
    oldStrategy(x)
}

assemblyJarName := s"${name.value}-${version.value}.jar"
