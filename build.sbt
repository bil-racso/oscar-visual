scalaVersion := "2.13.0"

lazy val oscarUtil = ProjectRef(uri("https://github.com/bil-racso/oscar-util.git"), "oscar-util")

lazy val root = (project in file(".")).dependsOn(oscarUtil)

name := "oscar-visual"
organization := "oscarlib"
version := "4.1.0"

libraryDependencies ++= Seq(
  "org.jfree" % "jfreechart" % "1.5.0",
  "org.swinglabs" % "swingx" % "1.6.1",
  "org.swinglabs" % "swingx-ws" % "1.0"
)