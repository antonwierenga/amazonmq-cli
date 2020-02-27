organization := "com.antonwierenga"

name := "amazonmq-cli"

version := "0.1.1"

scalaVersion := "2.11.6"

libraryDependencies += "org.springframework.shell" % "spring-shell" % "1.2.0.RELEASE"
libraryDependencies += "org.apache.activemq" % "activemq-all" % "5.15.9"
libraryDependencies += "com.typesafe" % "config" % "1.2.1"
libraryDependencies += "org.scala-lang" % "jline" % "2.11.0-M3"
libraryDependencies += "org.scala-lang.modules" %% "scala-xml" % "1.0.3"
libraryDependencies += "junit" % "junit" % "4.8" % "test"
libraryDependencies += "com.novocode" % "junit-interface" % "0.11" % "test->default"
libraryDependencies += "net.sourceforge.htmlunit" % "htmlunit" % "2.36.0"
libraryDependencies += "javax.xml.bind" % "jaxb-api" % "2.3.1"

import scalariform.formatter.preferences._

scalariformPreferences := scalariformPreferences.value
  .setPreference(AlignSingleLineCaseStatements, true)
  .setPreference(DoubleIndentConstructorArguments, true)
  .setPreference(DanglingCloseParenthesis, Preserve)

organizationName := "Anton Wierenga"
startYear := Some(2020)
licenses += ("Apache-2.0", new URL("https://www.apache.org/licenses/LICENSE-2.0.txt"))

enablePlugins(AutomateHeaderPlugin) 
enablePlugins(JavaAppPackaging)

resolvers += "sonatype-releases" at "https://oss.sonatype.org/content/repositories/releases/"

scalacOptions += "-target:jvm-1.7"

parallelExecution in Test := false
