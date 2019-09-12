lazy val oscarUtil = ProjectRef(uri("https://github.com/bil-racso/oscar-util.git"), "oscar-util")

lazy val oscarVisual = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "oscarlib",
      scalaVersion := "2.13.0"
	)),
    name := "oscar-visual").
  dependsOn(oscarUtil)

libraryDependencies ++= Seq(
  "org.jfree" % "jfreechart" % "1.5.0",
  "org.swinglabs" % "swingx" % "1.6.1",
  "org.swinglabs" % "swingx-ws" % "1.0"
)