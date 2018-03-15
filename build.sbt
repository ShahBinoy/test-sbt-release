import org.eclipse.jgit.lib.Repository
import org.gitective.core.filter.commit.{AndCommitFilter, BugSetFilter, BugFilter, CommitMessageFindFilter}
import org.gitective.core.{CommitFinder, CommitUtils}
import sbt.Keys._
import sbt._

import scala.sys.process.Process

//import sbtdocker.DockerPlugin.autoImport._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._

name := """test-sbt-release"""
organization := "com.bs"

scalaVersion := Versions.scala212


lazy val commonSettings = Defaults.coreDefaultSettings ++ Seq(
  publishTo := {
    if (version.value.trim.endsWith("SNAPSHOT"))
      Some("Artifactory Realm" at "https://washingtonpost.jfrog.io/washingtonpost/libs-snapshot-local")
    else
      Some("Artifactory Realm" at "https://washingtonpost.jfrog.io/washingtonpost/libs-release-local")
  },
  scalaVersion := Versions.scala212,
  credentials += Credentials(Path.userHome / ".ivy2" / ".credentials"),
  javacOptions ++= Seq("-Xlint:unchecked"),
  javacOptions ++= Seq("-Xlint:deprecation"),
  javacOptions ++= Seq("-encoding", "UTF-8"),
  javacOptions ++= Seq("-source", "1.8"),
  unmanagedResourceDirectories in Compile += (sourceDirectory in Compile) (_ / "resources").value,
  unmanagedResourceDirectories in Test += (sourceDirectory in Compile) (_ / "resources").value,
  publishArtifact in(Compile, packageDoc) := false,
  publishArtifact in packageDoc := false,
  sources in(Compile, doc) := Seq.empty,
  resolvers += "Local Maven Repository" at "file://" + Path.userHome.absolutePath + "/.m2/repository",
  resolvers += "Jongo-Early" at "http://repository-jongo.forge.cloudbees.com/release/",
  resolvers += DefaultMavenRepository,
  resolvers += "Artima Maven Repository" at "http://repo.artima.com/releases",
  resolvers += "scalaz-bintray" at "http://dl.bintray.com/scalaz/releases",
  resolvers += "buildinfo-bintray" at "https://dl.bintray.com/eed3si9n/sbt-plugins",
  resolvers += Resolver.sonatypeRepo("snapshots"),
  resolvers += Resolver.url("Typesafe Ivy releases", url("https://repo.typesafe.com/typesafe/ivy-releases"))(Resolver.ivyStylePatterns),
  resolvers += "Artifactory Snapshot" at "https://washingtonpost.jfrog.io/washingtonpost/libs-snapshot-local/",
  resolvers += "Artifactory Release" at "https://washingtonpost.jfrog.io/washingtonpost/libs-release-local/",
  resolvers += Resolver.url("buildreleases-bintray", url("https://dl.bintray.com/sbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)
)


lazy val `my-lib` = (project in file("my-lib"))
  .settings(commonSettings)
lazy val `my-web` = (project in file("my-web"))
  .settings(commonSettings)
  .enablePlugins(PlayJava)
  .dependsOn(`my-lib`)

lazy val `test-sbt-release` = (project in file("."))
  .settings(commonSettings)
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
  //  val dockerLogin = Seq("aws", "ecr", "get-login", "--no-include-email", "--region", "us-east-1").!!
  //  dockerLogin.replaceAll("\n", "").split(" ").toSeq.!
  val log = streams.value.log
  log.info("Logging into ECR")
}


lazy val publishDocker = ReleaseStep(action = st => {
  val extracted = Project.extract(st)
  val ref: ProjectRef = extracted.get(thisProjectRef)
  st.log.info("Trying to publish docker Level 1")
  extracted.runAggregated(loginAwsEcr in ref, st)
  //  extracted.runAggregated(
  //    sbtdocker.DockerKeys.dockerBuildAndPush in sbtdocker.DockerPlugin.autoImport.docker in ref,
  //    st)
  st.log.info("Trying to publish docker Level 2")
  st
})

lazy val makeReleaseNotes = ReleaseStep(action = st => {
  val extracted = Project.extract(st)
  val ref: ProjectRef = extracted.get(thisProjectRef)
  val value = Process("git tag -l")
  val tags: Stream[String] = value.lineStream
  val bestPrevTag = tags.filter(p => !p.contains("SNAPSHOT") && !p.contains("snapshot")).max

  st.log.info(s"Previous tag deployed was [$bestPrevTag]")

  val repo: Repository = new org.eclipse.jgit.internal.storage.file.FileRepository(".git")
  val commitFinder: CommitFinder = new CommitFinder(repo)
  val lastTagRef = repo.getTags.get(bestPrevTag)
  import org.gitective.core.filter.commit.CommitListFilter
  val commits = new CommitListFilter
  val pattern = "\\{JIRA:[0-9A-Z]{4,}\\}"
  val andFilter = new AndCommitFilter(new CommitMessageFindFilter(".*"),commits)

  val finder = commitFinder.setFilter(andFilter).findFrom(lastTagRef.getObjectId)

  commits.getCommits.forEach( x => {
    st.log.info(x.getFullMessage)
  }
  )

  st
})

publishTo := {
  if (version.value.trim.endsWith("SNAPSHOT"))
    Some("Artifactory Realm" at "https://washingtonpost.jfrog.io/washingtonpost/libs-snapshot-local")
  else
    Some("Artifactory Realm" at "https://washingtonpost.jfrog.io/washingtonpost/libs-release-local")
}

//val releaseTagComment    : TaskKey[String]
//val releaseCommitMessage : TaskKey[String]

// defaults
releaseTagComment    := s"Just for fun Releasing ${(version in ThisBuild).value}\n Second line for clarity"
releaseCommitMessage := s"Setting this commit version to meh... ${(version in ThisBuild).value}"

releaseProcess := Seq[ReleaseStep](
//  checkSnapshotDependencies,
//  inquireVersions,
//  runTest,
//  setReleaseVersion,
//  commitReleaseVersion,
    tagRelease,
//  publishDocker,
//  setNextVersion,
//  commitNextVersion,
//  pushChanges
    makeReleaseNotes
)