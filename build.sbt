lazy val oscarUtil = ProjectRef(uri("https://github.com/bil-racso/oscar-util.git"), "oscar-util")

lazy val oscarVisual = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "oscarlib",
      scalaVersion := "2.12.10"
	)),
    name := "oscar-visual").
  dependsOn(oscarUtil)

scalacOptions ++= Seq(
  "-encoding", "UTF-8",
  "-unchecked",
  "-deprecation",
  "-feature",
  "-Xdisable-assertions",
  "-language:implicitConversions",
  "-language:postfixOps"
)

libraryDependencies ++= Seq(
  "org.jfree" % "jfreechart" % "1.5.0",
  "org.swinglabs" % "swingx" % "1.6.1",
  "org.swinglabs" % "swingx-ws" % "1.0"
)