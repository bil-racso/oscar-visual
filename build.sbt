lazy val oscarUtil = ProjectRef(uri("https://github.com/bil-racso/oscar-util.git"), "oscar-util")

lazy val oscarVisual = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "oscarlib",
      scalaVersion := "2.13.2",
      version := "5.0.0"
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
  "org.jdesktop.swingx" % "jxmapviewer2" % "1.3.1",
  "org.jfree" % "jfreechart" % "1.5.0",
  "org.scala-lang.modules" %% "scala-xml" % "1.3.0"
)
