import sbt.Keys._
import sbt._
//import sbtdocker.DockerPlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

name := """test-sbt-release"""
organization := "com.bs"

lazy val `my-lib` = (project in file("my-lib"))
lazy val `my-web` = (project in file("my-web")).enablePlugins(PlayJava)

lazy val `test-sbt-release` = (project in file("."))
  .settings(
    publishArtifact := false,
    publish := ((): Unit),
    publishLocal := ((): Unit))
  .aggregate(`my-lib`,
  `my-web`)


scalaVersion := "2.12.4"

libraryDependencies += guice

//
//imageNames in Docker := Seq( ImageName( namespace = Some(s"$ecrNamespace/${organization.value}"), repository = name.value, tag = Some(version.value) ) )


lazy val loginAwsEcr = TaskKey[Unit]("loginAwsEcr", "Login AWS ECR")

loginAwsEcr := {
  //  import sys.process._
  //  val dockerLogin = Seq("aws", "ecr", "get-login", "--no-include-email", "--region", "eu-central-1").!!
  //  dockerLogin.replaceAll("\n", "").split(" ").toSeq.!
  val log = streams.value.log
  log.info("Logging into ECR")
}

lazy val publishDocker = ReleaseStep(action = st => {
  val extracted = Project.extract(st)
  val ref: ProjectRef = extracted.get(thisProjectRef)
  extracted.runAggregated(loginAwsEcr in ref, st)
  //  extracted.runAggregated(
  //    sbtdocker.DockerKeys.dockerBuildAndPush in sbtdocker.DockerPlugin.autoImport.docker in ref,
  //    st)
  st.log.info("Trying to publish docker")
  st
})

lazy val releaseSettings = Seq(
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    runTest,
    setReleaseVersion,
    commitReleaseVersion,
    publishDocker,
    setNextVersion,
    commitNextVersion
  ))