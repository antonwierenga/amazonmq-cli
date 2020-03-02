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

import java.text.SimpleDateFormat
import java.util.{Date, UUID}

import amazonmq.cli.AmazonMQCLI
import amazonmq.cli.util.Console.{info, prompt, warn}
import amazonmq.cli.util.Implicits._
import amazonmq.cli.util.PrintStackTraceExecutionProcessor
import com.gargoylesoftware.htmlunit.html.HtmlPage
import com.gargoylesoftware.htmlunit.{BrowserVersion, WebClient}
import com.gargoylesoftware.htmlunit.xml.XmlPage
import javax.jms.{Connection, Message, Session}
import javax.xml.bind.DatatypeConverter._
import org.apache.activemq.ActiveMQConnectionFactory
import org.springframework.shell.support.table.{Table, TableHeader}

import scala.collection.JavaConversions._
import scala.collection.mutable.ListBuffer
import scala.tools.jline.console.ConsoleReader

abstract class Commands extends PrintStackTraceExecutionProcessor {

  val numberFormatter = java.text.NumberFormat.getIntegerInstance

  def format(number: Long): String = {
    numberFormatter.format(number)
  }

  def formatDuration(duration: Long): String = {
    val formatTimeUnit = (l: Long, timeUnit: String) ⇒ if (l == 0) None else if (l == 1) s"$l $timeUnit" else s"$l ${timeUnit}s"
    List(
      formatTimeUnit((duration / (1000 * 60 * 60)) % 24, "hour"),
      formatTimeUnit((duration / (1000 * 60)) % 60, "minute"),
      formatTimeUnit((duration / 1000) % 60, "second")
    ).filter(_ != None).mkString(" ")
  }

  def confirm(force: String = "no"): Unit = {
    force match {
      case "yes" ⇒ // skip confirmation
      case _ ⇒
        if (!List("Y", "y").contains(new ConsoleReader().readLine(prompt("Are you sure? (Y/N): ")))) {
          throw new IllegalArgumentException("Command aborted")
        }
    }
  }

  def renderTable(rows: Seq[Seq[Any]], headers: Seq[String]): String = {
    val table = new Table()
    headers.zipWithIndex.map {
      case (header, i) ⇒
        table.addHeader(i + 1, new TableHeader(header))
    }
    rows.map(row ⇒ row.map(_.toString)).map(row ⇒ table.addRow(row: _*))
    table.calculateColumnWidths
    table.toString
  }

  def validateTopicExists(webClient: WebClient, topic: String): Unit = {
    val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/topics.jsp")
    val topics = (scala.xml.XML.loadString(page.asXml()) \ "topic").filter(xmlTopic ⇒ (xmlTopic \@ "name").toString.trim == topic)
    if (topics.isEmpty) {
      throw new IllegalArgumentException(s"Topic '$topic' does not exist")
    }
  }

  def validateTopicNotExists(webClient: WebClient, topic: String): Unit = {
    val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/topics.jsp")
    val topics = (scala.xml.XML.loadString(page.asXml()) \ "topic").filter(xmlTopic ⇒ (xmlTopic \@ "name").toString.trim == topic)
    if (!topics.isEmpty) {
      throw new IllegalArgumentException(s"Topic '$topic' already exists")
    }
  }

  def validateQueueExists(webClient: WebClient, queue: String): Unit = {
    val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/queues.jsp")
    val queues = (scala.xml.XML.loadString(page.asXml()) \ "queue").filter(xmlQueue ⇒ (xmlQueue \@ "name").toString.trim == queue)
    if (queues.isEmpty) {
      throw new IllegalArgumentException(s"Queue '$queue' does not exist")
    }
  }

  def validateQueueNotExists(webClient: WebClient, queue: String): Unit = {
    val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/queues.jsp")
    val queues = (scala.xml.XML.loadString(page.asXml()) \ "queue").filter(xmlQueue ⇒ (xmlQueue \@ "name").toString.trim == queue)
    if (!queues.isEmpty) {
      throw new IllegalArgumentException(s"Queue '$queue' already exists")
    }
  }

  def withSession(callback: (Session) ⇒ Unit): Unit = {
    var connection: Option[Connection] = None
    var session: Option[Session] = None
    try {
      connection = Some(new ActiveMQConnectionFactory(AmazonMQCLI.broker.get.username, AmazonMQCLI.broker.get.password,
        AmazonMQCLI.broker.get.amqurl).createConnection)
      connection.get.start
      session = Some(connection.get.createSession(true, Session.AUTO_ACKNOWLEDGE))
      callback(session.get)
    } catch {
      case illegalArgumentException: IllegalArgumentException ⇒ {
        warn(illegalArgumentException.getMessage)
      }
    } finally {
      if (session.isDefined) {
        session.get.commit
        session.get.close
      }
      if (connection.isDefined) {
        connection.get.close
      }
    }
  }

  def withWebClient(callback: (WebClient) ⇒ String): String = {
    val webClient = new WebClient(BrowserVersion.BEST_SUPPORTED);
    webClient.addRequestHeader("Authorization", "Basic " + printBase64Binary(s"${AmazonMQCLI.broker.get.username}:${AmazonMQCLI.broker.get.password}".getBytes))
    webClient.getOptions.setJavaScriptEnabled(false)
    try {
      callback(webClient)
    } catch {
      case illegalArgumentException: IllegalArgumentException ⇒ {
        warn(illegalArgumentException.getMessage)
      }
    } finally {
      webClient.close()
    }
  }

  def moveMessages(from: String, to: String, selector: Option[String]): Int = {
    var messagesMoved = 0
    var timeoutReached = false
    while (!timeoutReached) {
      withSession((session: Session) ⇒ {
        val fromConsumer = session.createConsumer(session.createQueue(from), selector.getOrElse(null)) //scalastyle:ignore
        val toProducer = session.createProducer(session.createQueue(to))
        do {
          val message = fromConsumer.receive(AmazonMQCLI.Config.getLong("messages.receive.timeout"))
          if (Option(message).isDefined) {
            messagesMoved = messagesMoved + 1
            toProducer.send(message)
          } else {
            timeoutReached = true
          }
        } while (!timeoutReached && messagesMoved % AmazonMQCLI.Config.getInt("messages.receive.batch-size") != 0)
      })
    }
    messagesMoved
  }

  def withEveryMessage(queue: String, selector: Option[String], destinationQueues: Seq[String], resultMessage: String, callback: (Message) ⇒ Unit): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateQueueExists(webClient, queue)
      val tempQueue = s"amazonmq-cli.$queue.temp.${new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date())}.${UUID.randomUUID().toString()}"
      val totalMessages = moveMessages(queue, tempQueue, selector)
      var messagesProcessed = 0
      while (messagesProcessed < totalMessages) {
        withSession(callback = (session: Session) ⇒ {
          val consumer = session.createConsumer(session.createQueue(tempQueue))
          val destinationProducers = destinationQueues.map(destinationQueue ⇒ session.createProducer(session.createQueue(destinationQueue)))
          do {
            val message = consumer.receive(AmazonMQCLI.Config.getLong("messages.receive.timeout"))
            messagesProcessed = messagesProcessed + 1
            destinationProducers.map(producer ⇒ producer.send(message))
            callback(message)
          } while (messagesProcessed < totalMessages && messagesProcessed % AmazonMQCLI.Config.getInt("messages.receive.batch-size") != 0)
        })
      }
      if (messagesProcessed > 0) {
        removeQueue(webClient, tempQueue)
      }
      info(s"$resultMessage: $totalMessages")
    })
  }

  def removeQueue(webClient: WebClient, name: String): Unit = {
    val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/queues.jsp")
    val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("deleteDestination.action") &&
      a.getHrefAttribute.contains(s"JMSDestination=$name&JMSDestinationType=queue"))
    try {
      anchor.get.click()
    } catch {
      case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
    }
  }

  def applyFilterParameter(parameter: String, value: Long, parameterValue: Long): Boolean = {
    if (parameter) {
      parameter.substring(0, 1) match {
        case "<" ⇒ value < parameterValue
        case ">" ⇒ value > parameterValue
        case "=" ⇒ value == parameterValue
      }
    } else {
      true
    }
  }

  def parseFilterParameter(parameter: String, parameterName: String): Long = {
    if (parameter) {
      val errorMessage = s"The --${parameterName} filter must start with <, > or = followed by a number (example: --${parameterName} <10)"
      if (!parameter.startsWith("<") && !parameter.startsWith(">") && !parameter.startsWith("=")) throw new IllegalArgumentException(errorMessage)
      try {
        parameter.substring(1).trim.toLong
      } catch {
        case e: Exception ⇒ throw new IllegalArgumentException(errorMessage)
      }
    } else {
      0
    }
  }
}
