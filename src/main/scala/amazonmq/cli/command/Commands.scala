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
import java.util.{ Date, UUID }

import amazonmq.cli.AmazonMQCLI
import amazonmq.cli.util.Console.{ info, prompt, warn }
import amazonmq.cli.util.Implicits._
import amazonmq.cli.util.PrintStackTraceExecutionProcessor
import com.gargoylesoftware.htmlunit.{ BrowserVersion, WebClient }
import com.gargoylesoftware.htmlunit.xml.XmlPage
import javax.jms.{ Connection, Message, Session }
import javax.xml.bind.DatatypeConverter._
import org.apache.activemq.ActiveMQConnectionFactory
import org.springframework.shell.support.table.{ Table, TableHeader }

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

  def withSession(callback: (Session) ⇒ String): String = {
    var connection: Option[Connection] = None
    var session: Option[Session] = None
    try {
      connection = Some(new ActiveMQConnectionFactory(AmazonMQCLI.broker.get.username, AmazonMQCLI.broker.get.password,
        AmazonMQCLI.broker.get.amqurl).createConnection)
      connection.get.start
      session = Some(connection.get.createSession(true, Session.SESSION_TRANSACTED))
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

  def withEveryMessage(queue: String, selector: Option[String], regex: Option[String], responseMessage: String, firstDestinationQueue: String,
    secondDestinationQueue: Option[String], receiveTimeout: Long, callback: (Message) ⇒ Unit): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateQueueExists(webClient, queue)
      withSession((session: Session) ⇒ {
        var callbacks = 0
        val consumer = session.createConsumer(session.createQueue(queue), selector.getOrElse(null)) //scalastyle:ignore
        val queueDestinationProducer = session.createProducer(session.createQueue(queue))
        val firstDestinationProducer = session.createProducer(session.createQueue(firstDestinationQueue))
        val secondDestinationProducer = if (secondDestinationQueue.isDefined) {
          Some(session.createProducer(session.createQueue(secondDestinationQueue.get)))
        } else {
          None
        }
        var message = consumer.receive(receiveTimeout)
        while (Option(message).isDefined) {
          if (message.textMatches(regex.getOrElse(null))) { //scalastyle:ignore
            firstDestinationProducer.send(message)
            if (secondDestinationProducer.isDefined) {
              secondDestinationProducer.get.send(message)
            }
            callback(message)
            callbacks = callbacks + 1
          } else {
            queueDestinationProducer.send(message) // put back on original queue
          }
          message = consumer.receive(receiveTimeout)

        }
        info(s"\n$responseMessage: $callbacks")
      })
    })
  }

  def getNewMirrorQueue(queue: String): String = {
    s"activemq-cli.$queue.mirror.${new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date())}.${UUID.randomUUID().toString()}"
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
