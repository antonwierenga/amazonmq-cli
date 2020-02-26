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

package amazonmq.cli.command

import java.io.File

import amazonmq.cli.AmazonMQCLI
import amazonmq.cli.domain.Broker
import amazonmq.cli.util.Console._
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.html.{HtmlPage, HtmlTableRow}
import javax.jms.Session
import org.springframework.shell.core.annotation.{CliAvailabilityIndicator, CliCommand, CliOption}
import org.springframework.stereotype.Component

import scala.tools.jline.console.ConsoleReader

@Component
class BrokerCommands extends Commands {

  @CliAvailabilityIndicator(Array("info", "disconnect", "web-console"))
  def isBrokerAvailable: Boolean = AmazonMQCLI.broker.isDefined

  @CliCommand(value = Array("disconnect"), help = "Disconnect from the broker")
  def disconnect(): String = {
    AmazonMQCLI.broker = None
    info(s"Disconnected from broker")
  }

  @CliCommand(value = Array("connect"), help = "Connects to a broker")
  def connect(
    @CliOption(key = Array("broker"), mandatory = true, help = "The Broker Alias") pBroker: Broker
  ): String = {

    val brokerAlias = pBroker.alias
    try {
      if (!AmazonMQCLI.Config.hasPath(s"broker.$brokerAlias")) throw new IllegalArgumentException(s"Broker '$brokerAlias' not found in ${new File(System.getProperty("config.file")).getCanonicalPath}") //scalastyle:ignore
      if (!AmazonMQCLI.Config.hasPath(s"broker.$brokerAlias.amqurl")) throw new IllegalArgumentException(s"amqurl not found for broker '$brokerAlias' in ${new File(System.getProperty("config.file")).getCanonicalPath}") //scalastyle:ignore

      val username = if (AmazonMQCLI.Config.hasPath(s"broker.$brokerAlias.username")) {
        AmazonMQCLI.Config.getString(s"broker.$brokerAlias.username")
      } else {
        new ConsoleReader().readLine(prompt("Enter username: "))
      }

      val password = if (AmazonMQCLI.Config.hasPath(s"broker.$brokerAlias.password")) {
        AmazonMQCLI.Config.getString(s"broker.$brokerAlias.password")
      } else {
        new ConsoleReader().readLine(prompt("Enter password: "), new Character('*'))
      }

      AmazonMQCLI.broker = Option(new Broker(brokerAlias, AmazonMQCLI.Config.getString(s"broker.$brokerAlias.web-console"),
        AmazonMQCLI.Config.getString(s"broker.$brokerAlias.amqurl"), username, password))

      setSslProperties(brokerAlias)
      withSession((session: Session) ⇒ { "" })
      info(s"Connected to broker '${AmazonMQCLI.broker.get.alias}'")
    } catch {
      case iae: IllegalArgumentException ⇒ {
        AmazonMQCLI.broker = None
        warn(iae.getMessage)
      }
      case e: Exception ⇒ {
        AmazonMQCLI.broker = None
        e.printStackTrace()

        warn(s"Failed to connect to broker: ${e.getMessage}")
      }
    }
  }

  @CliCommand(value = Array("info"), help = "Displays broker info")
  def brokerInfo(): String = {
    withWebClient((webClient: WebClient) ⇒ {
      val page: HtmlPage = webClient.getPage(AmazonMQCLI.broker.get.webConsole)
      val tableRows = page.getByXPath("//td//table//tr").toArray.toList.asInstanceOf[List[HtmlTableRow]]
      val headers = tableRows.map(tableRow ⇒ tableRow.getCell(0).getTextContent)
      val row = tableRows.map(tableRow ⇒ tableRow.getCell(1).getTextContent)
      renderTable(List(row), headers)
    })
  }

  @CliCommand(value = Array("web-console"), help = "Opens the webconsole")
  def webConsole(): String = {
    java.awt.Desktop.getDesktop.browse(java.net.URI.create(AmazonMQCLI.broker.get.webConsole))
    "The browser is launched to show the web console"
  }

  def setSslProperties(pBroker: String): Unit = {
    if (AmazonMQCLI.Config.hasPath(s"broker.$pBroker.keyStore")) {
      System.setProperty("javax.net.ssl.keyStore", AmazonMQCLI.Config.getString(s"broker.$pBroker.keyStore"))
    }
    if (AmazonMQCLI.Config.hasPath(s"broker.$pBroker.keyStorePassword")) {
      System.setProperty("javax.net.ssl.keyStorePassword", AmazonMQCLI.Config.getString(s"broker.$pBroker.keyStorePassword"))
    }
    if (AmazonMQCLI.Config.hasPath(s"broker.$pBroker.trustStore")) {
      System.setProperty("javax.net.ssl.trustStore", AmazonMQCLI.Config.getString(s"broker.$pBroker.trustStore"))
    }
  }
}
