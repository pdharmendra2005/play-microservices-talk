name         := "order-service"
version      := "1.0-SNAPSHOT"
scalaVersion := "2.13.16"

lazy val root = (project in file("."))
  .enablePlugins(PlayScala)

// default port when you just type `sbt run`
PlayKeys.playDefaultPort := 9000

libraryDependencies ++= Seq(
  guice,                    // dependency injection (@Inject)
  ws,                       // Play WS client -- GUIDE Part 2, Step 2
  // -- testing --
  "org.scalatestplus.play" %% "scalatestplus-play" % "7.0.1" % Test
)

scalacOptions ++= Seq("-deprecation", "-feature", "-unchecked")
