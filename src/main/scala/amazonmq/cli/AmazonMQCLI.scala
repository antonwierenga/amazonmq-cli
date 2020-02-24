/*
 * Copyright 2020 Anton Wierenga
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package amazonmq.cli

import java.io.File
import java.nio.file.Paths

import amazonmq.cli.domain.Broker
import com.typesafe.config.{Config, ConfigFactory}
import org.springframework.shell.Bootstrap
import org.springframework.shell.core.CommandMarker
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.stereotype.Component

@Component
class AmazonMQCLI extends CommandMarker {

  @CliCommand(value = Array("release-notes"), help = "Displays release notes")
  def releaseNotes: String = AmazonMQCLI.ReleaseNotes.keySet.toSeq.sorted.map(x ⇒ s"$x\n" + AmazonMQCLI.ReleaseNotes(x)
    .map(y ⇒ s"    - $y").mkString("\n")).mkString("\n")
}

object AmazonMQCLI extends App {

  lazy val ReleaseNotes = Map(
    "v0.0.0" → List(
      "New shell command 'add-queue'",
      "New shell command 'add-topic'",
      "New shell command 'connect'",
      "New shell command 'copy-messages '",
      "New shell command 'disconnect'",
      "New shell command 'export-broker'",
      "New shell command 'export-messages'",
      "New shell command 'info'",
      "New shell command 'list-messages'",
      "New shell command 'move-messages'",
      "New shell command 'purge-all-queues'",
      "New shell command 'purge-queue'",
      "New shell command 'queues'",
      "New shell command 'release-notes'",
      "New shell command 'remove-all-queues'",
      "New shell command 'remove-all-topics'",
      "New shell command 'remove-queue'",
      "New shell command 'remove-topic'",
      "New shell command 'send-message'",
      "New shell command 'topics'"
    )
  )

  lazy val ApplicationPath: File = new File(s"${
    Paths.get(classOf[AmazonMQCLI].getProtectionDomain.getCodeSource.getLocation.toURI).toFile.getParentFile
      .getParentFile
  }")

  lazy val ApplicationOutputPath: File = new File(ApplicationPath, "output")

  if (!ApplicationOutputPath.exists()) ApplicationOutputPath.mkdir()

  System.setProperty("config.file", new File(ApplicationPath, "conf/amazonmq-cli.config").getPath)

  lazy val Config: Config = ConfigFactory.load

  var broker: Option[Broker] = None

  Bootstrap.main(args)

}
