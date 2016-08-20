name := """bayeslib"""

version := "1.0"

scalaVersion := "2.11.6"

libraryDependencies ++= Seq(
  // Uncomment to use Akka
  //"com.typesafe.akka" % "akka-actor_2.11" % "2.3.9",
  "junit"             % "junit"           % "4.12"  % "test",
  "com.novocode"      % "junit-interface" % "0.11"  % "test",
  "com.sleepycat" % "je" % "5.0.73",
  "com.fasterxml.jackson.core" % "jackson-core" % "2.8.1",
  "com.fasterxml.jackson.core" % "jackson-databind" % "2.8.1",
  "com.fasterxml.jackson.core" % "jackson-annotations" % "2.8.1"
)
