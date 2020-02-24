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

import java.io.{BufferedWriter, File, FileWriter}
import java.text.SimpleDateFormat
import java.util.Date

import amazonmq.cli.AmazonMQCLI
import amazonmq.cli.AmazonMQCLI._
import amazonmq.cli.util.Console._
import amazonmq.cli.util.Implicits._
import com.gargoylesoftware.htmlunit.WebClient
import javax.jms.{Message, Session, TextMessage}
import org.springframework.shell.core.annotation.{CliAvailabilityIndicator, CliCommand, CliOption}
import org.springframework.stereotype.Component

import scala.xml.XML

@Component
class MessageCommands extends Commands {

  val JMSPriorityDefault = 4
  val JMSTimeToLiveDefault = 0L

  val JMSCorrelationID = ("JMSCorrelationID", "correlation-id")
  val JMSPriority = ("JMSPriority", "priority")
  val JMSDeliveryMode = ("JMSDeliveryMode", "delivery-mode")
  val JMSReplyTo = ("JMSReplyTo", "reply-to")
  val TimeToLive = ("timeToLive", "time-to-live")

  @CliAvailabilityIndicator(Array("move-messages", "copy-messages", "list-messages", "send-message", "export-messages"))
  def isBrokerAvailable: Boolean = AmazonMQCLI.broker.isDefined

  @CliCommand(value = Array("move-messages"), help = "Moves messages between queues")
  def moveMessages(
    @CliOption(key = Array("from"), mandatory = true, help = "The source queue") from: String,
    @CliOption(key = Array("to"), mandatory = true, help = "The target queue") to: String,
    @CliOption(key = Array("selector"), mandatory = false, help = "The message selector") selector: String
  ): String = {
    withEveryMessage(from, Option(selector), None, "Messages moved", to, None,
      AmazonMQCLI.Config.getLong("command.move-messages.receive-timeout"),
      (message: Message) ⇒ {})
  }

  @CliCommand(value = Array("copy-messages"), help = "Copies messages between queues")
  def copyMessages(
    @CliOption(key = Array("from"), mandatory = true, help = "The source queue") from: String,
    @CliOption(key = Array("to"), mandatory = true, help = "The target queue") to: String,
    @CliOption(key = Array("selector"), mandatory = false, help = "The message selector") selector: String
  ): String = {
    withEveryMessage(from, Option(selector), None, "Messages copied", to, Some(from),
      AmazonMQCLI.Config.getLong("command.copy-messages.receive-timeout"),
      (message: Message) ⇒ {})
  }

  @CliCommand(value = Array("send-message"), help = "Sends a message to a queue or topic")
  def sendMessage( //scalastyle:ignore
    @CliOption(key = Array("queue"), mandatory = false, help = "The name of the queue") queue: String,
    @CliOption(key = Array("topic"), mandatory = false, help = "The name of the topic") topic: String,
    @CliOption(key = Array("body"), mandatory = false, help = "The body of the message") body: String,
    @CliOption(key = Array("correlation-id"), mandatory = false, help = "The correlation id of the message") correlationId: String,
    @CliOption(key = Array("reply-to"), mandatory = false, help = "Name of the destination (topic or queue) the message replies should be sent to") replyTo: String, //scalastyle:ignore
    @CliOption(key = Array("delivery-mode"), mandatory = false, help = "The delivery mode of the message") deliveryMode: DeliveryMode,
    @CliOption(key = Array("time-to-live"), mandatory = false, help = "The time to live (in milliseconds) of the message") timeToLive: String,
    @CliOption(key = Array("priority"), mandatory = false, help = "The priority of the message") priority: String,
    @CliOption(key = Array("times"), mandatory = false, unspecifiedDefaultValue = "1", help = "The number of times the message is send") times: Int,
    @CliOption(key = Array("file"), mandatory = false, help = "The file containing messages to send") file: String
  ): String = {
    val start = System.currentTimeMillis
    val pFile = if (file) file.replaceFirst("^~", System.getProperty("user.home")) else file

    withSession((session: Session) ⇒ {
      if (!file && !body) throw new IllegalArgumentException("Either --body or --file must be specified, but not both")
      if ((!queue && !topic) || (queue && topic)) throw new IllegalArgumentException("Either --queue or --topic must be specified, but not both")
      if (file) {
        if (!new File(pFile).exists) throw new IllegalArgumentException(s"File '$file' does not exist")
        try {
          if ((XML.loadFile(pFile) \ "jms-message").isEmpty) throw new IllegalArgumentException(s"No message found in '$file'")
        } catch {
          case spe: org.xml.sax.SAXParseException ⇒ throw new IllegalArgumentException(
            s"Error in $file line: ${spe.getLineNumber}, column: ${spe.getColumnNumber}, error: ${spe.getMessage}"
          )
        }
        if (replyTo || correlationId || Option(deliveryMode).isDefined || Option(timeToLive).isDefined || Option(priority).isDefined
          || Option(timeToLive).isDefined) {
          throw new IllegalArgumentException("When --file is specified only --queue or --topic is allowed")
        }
      }

      val producer = if (queue) {
        session.createProducer(session.createQueue(queue))
      } else {
        session.createProducer(session.createTopic(topic))
      }

      try {
        var totalSent = 0
        if (body) {
          val message = session.createTextMessage(body)
          if (replyTo) {
            message.setJMSReplyTo(session.createQueue(replyTo))
          }
          message.setJMSCorrelationID(correlationId)

          for (i ← (1 to times)) yield {
            producer.send(message, Option(deliveryMode).getOrElse(DeliveryMode.PERSISTENT).getJMSDeliveryMode,
              Option(priority).getOrElse(JMSPriorityDefault.toString).toInt, Option(timeToLive).getOrElse(JMSTimeToLiveDefault.toString).toLong)
            totalSent += 1
          }
        } else { // file
          for (i ← (1 to times)) yield {
            (XML.loadFile(pFile) \ "jms-message").map(xmlMessage ⇒ {
              val message = session.createTextMessage((xmlMessage \ "body").text)

              if (!(xmlMessage \ "header" \ JMSReplyTo._2).isEmpty) {
                message.setJMSReplyTo(session.createQueue((xmlMessage \ "header" \ JMSReplyTo._2).text))
              }

              if (!(xmlMessage \ "header" \ JMSCorrelationID._2).text.isEmpty) {
                message.setJMSCorrelationID((xmlMessage \ "header" \ JMSCorrelationID._2).text)
              }

              val deliverMode = if (!(xmlMessage \ "header" \ JMSDeliveryMode._2).text.isEmpty) {
                (xmlMessage \ "header" \ JMSDeliveryMode._2).text.toInt
              } else {
                DeliveryMode.PERSISTENT.getJMSDeliveryMode
              }

              val priority = if (!(xmlMessage \ "header" \ JMSPriority._2).text.isEmpty) {
                (xmlMessage \ "header" \ JMSPriority._2).text.toInt
              } else {
                JMSPriorityDefault
              }

              val timeToLive = if (!(xmlMessage \ "header" \ TimeToLive._2).text.isEmpty) {
                (xmlMessage \ "header" \ TimeToLive._2).text.toLong
              } else {
                JMSTimeToLiveDefault
              }

              producer.send(session.createQueue(queue), message, deliverMode, 3, timeToLive)
              totalSent += 1
            })
          }
        }
        val duration = System.currentTimeMillis - start
        formatDuration(duration)
        info(s"Messages sent to ${if (queue) s"queue '$queue'" else s"topic '$topic'"}: $totalSent${if (duration > 1000) s" (${formatDuration(duration)})" else ""}") //scalastyle:ignore
      } finally {
        producer.close()
      }
    })
  }

  @CliCommand(value = Array("export-messages"), help = "Exports messages to file")
  def exportMessages(
    @CliOption(key = Array("queue"), mandatory = true, help = "The name of the queue") queue: String,
    @CliOption(key = Array("selector"), mandatory = false, help = "the jms message selector") selector: String,
    @CliOption(key = Array("regex"), mandatory = false, help = "The regular expression the JMS text message must match") regex: String,
    @CliOption(key = Array("file"), mandatory = false, help = "The file that is used to save the messages in") file: String
  ): String = {
    val pFile = if (file) file.replaceFirst("^~", System.getProperty("user.home")) else file
    if (file && new File(pFile).exists()) {
      warn(s"File '$file' already exists")
    } else {
      val messageFile = Option(pFile).getOrElse(s"${queue}_${new SimpleDateFormat("ddMMyyyy_HHmmss").format(new Date())}.xml")
      val outputFile: File = Option(new File(messageFile).getParent) match {
        case Some(parent) ⇒ new File(messageFile)
        case _            ⇒ new File(ApplicationOutputPath, messageFile)
      }

      val bufferedWriter = new BufferedWriter(new FileWriter(outputFile))
      try {
        bufferedWriter.write("<jms-messages>\n")
        val result = withEveryMessage(queue, Option(selector), Option(regex), s"Messages exported to ${outputFile.getCanonicalPath()}", queue, None,
          AmazonMQCLI.Config.getLong("command.export-messages.receive-timeout"),
          (message: Message) ⇒ {
            bufferedWriter.write(s"${message.toXML(AmazonMQCLI.Config.getOptionalString("command.list-messages.timestamp-format"))}\n"
              .replaceAll("(?m)^", "  "))
          })
        bufferedWriter.write("</jms-messages>\n")
        result
      } finally {
        bufferedWriter.close
      }
    }
  }

  @CliCommand(value = Array("list-messages"), help = "Displays messages")
  def listMessages(
    @CliOption(key = Array("queue"), mandatory = true, help = "The name of the queue") queue: String,
    @CliOption(key = Array("selector"), mandatory = false, help = "the JMS message selector") selector: String,
    @CliOption(key = Array("regex"), mandatory = false, help = "The regular expression the JMS text message must match") regex: String
  ): String = {
    withEveryMessage(queue, Option(selector), Option(regex), "Messages listed", queue, None,
      AmazonMQCLI.Config.getLong("command.list-messages.receive-timeout"),
      (message: Message) ⇒ {
        println(info(s"${message.toXML(AmazonMQCLI.Config.getOptionalString("command.list-messages.timestamp-format"))}")) //scalastyle:ignore
      })
  }

  @CliCommand(value = Array("browse-messages"), help = "Displays messages")
  def browseMessages(
    @CliOption(key = Array("queue"), mandatory = true, help = "The name of the queue") queue: String,
    @CliOption(key = Array("selector"), mandatory = false, help = "the JMS message selector") selector: String,
    @CliOption(key = Array("regex"), mandatory = false, help = "The regular expression the JMS text message must match") regex: String
  ): String = withSession((session: Session) ⇒ {
    withWebClient((webClient: WebClient) ⇒ {
      validateQueueExists(webClient, queue)
      var numberOfMessages = 0
      val enumeration = session.createBrowser(session.createQueue(queue), selector).getEnumeration
      while (enumeration.hasMoreElements) {
        println(info(s"${enumeration.nextElement.asInstanceOf[TextMessage].toXML(AmazonMQCLI.Config.getOptionalString("command.list-messages.timestamp-format"))}")) //scalastyle:ignore
        numberOfMessages = numberOfMessages + 1
      }
      println(info(s"\nMessages browsed: $numberOfMessages")) //scalastyle:ignore
      warn("The browse-messages command may not return all messages due to limitations of broker configuration and system resources.")
    })
  })
}
