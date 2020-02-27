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

import amazonmq.cli.AmazonMQCLI
import amazonmq.cli.util.Console._
import amazonmq.cli.util.Implicits._
import com.gargoylesoftware.htmlunit.html.{ DomElement, HtmlInput, HtmlPage }
import org.springframework.shell.core.annotation.CliAvailabilityIndicator
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.shell.core.annotation.CliOption
import org.springframework.stereotype.Component
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.xml.XmlPage

import scala.collection.JavaConversions._

@Component
class QueueCommands extends Commands {

  @CliAvailabilityIndicator(Array("add-queue", "purge-queue", "purge-all-queues", "remove-queue", "remove-all-queues", "list-queues"))
  def isBrokerAvailable: Boolean = AmazonMQCLI.broker.isDefined

  @CliCommand(value = Array("add-queue"), help = "Adds a queue")
  def addQueue(@CliOption(key = Array("name"), mandatory = true, help = "The name of the queue") name: String): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateQueueNotExists(webClient, name)
      val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/queues.jsp")
      val form = page.getForms.find(_.getActionAttribute == "createDestination.action").get
      val inputField: HtmlInput = form.getInputByName("JMSDestination")
      inputField.setValueAttribute(name)
      val submitButton: DomElement = form.getFirstByXPath("//input[@value='Create']")
      try {
        submitButton.click()
      } catch {
        case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
      }
      info(s"Queue '$name' added")
    })
  }

  @CliCommand(value = Array("purge-queue"), help = "Purges a queue")
  def purgeQueue(
    @CliOption(key = Array("name"), mandatory = true, help = "The name of the queue") name: String,
    @CliOption(key = Array("force"), specifiedDefaultValue = "yes", mandatory = false, help = "No prompt") force: String
  ): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateQueueExists(webClient, name)
      confirm(force)
      val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/queues.jsp")
      val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("purgeDestination.action") &&
        a.getHrefAttribute.contains(s"JMSDestination=$name&JMSDestinationType=queue"))

      try {
        anchor.get.click()
      } catch {
        case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
      }
      info(s"Queue '$name' purged")
    })
  }

  @CliCommand(value = Array("remove-queue"), help = "Removes a queue")
  def removeQueue(
    @CliOption(key = Array("name"), mandatory = true, help = "The name of the queue") name: String,
    @CliOption(key = Array("force"), specifiedDefaultValue = "yes", mandatory = false, help = "No prompt") force: String
  ): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateQueueExists(webClient, name)
      confirm(force)
      val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/queues.jsp")
      val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("deleteDestination.action") &&
        a.getHrefAttribute.contains(s"JMSDestination=$name&JMSDestinationType=queue"))
      try {
        anchor.get.click()
      } catch {
        case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
      }
      info(s"Queue '$name' removed")
    })
  }

  @CliCommand(value = Array("remove-all-queues"), help = "Removes all queues")
  def removeAllQueues(
    @CliOption(key = Array("force"), specifiedDefaultValue = "yes", mandatory = false, help = "No prompt") force: String,
    @CliOption(key = Array("filter"), mandatory = false, help = "The query") filter: String,
    @CliOption(key = Array("dry-run"), specifiedDefaultValue = "yes", mandatory = false, help = "Dry run") dryRun: String,
    @CliOption(key = Array("pending"), mandatory = false, help = "Only queues that meet the pending filter are listed") pending: String,
    @CliOption(key = Array("enqueued"), mandatory = false, help = "Only queues that meet the enqueued filter are listed") enqueued: String,
    @CliOption(key = Array("dequeued"), mandatory = false, help = "Only queues that meet the dequeued filter are listed") dequeued: String,
    @CliOption(key = Array("consumers"), mandatory = false, help = "Only queues that meet the consumers filter are listed") consumers: String
  ): String = {
    withFilteredQueues("removed", force, filter, dryRun, pending, enqueued, dequeued, consumers,
      (webClient: WebClient, queue: String) ⇒ {
        val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/queues.jsp")
        val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("deleteDestination.action") &&
          a.getHrefAttribute.contains(s"JMSDestination=$queue&JMSDestinationType=queue"))
        try {
          anchor.get.click()
        } catch {
          case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
        }
        Thread.sleep(AmazonMQCLI.Config.getInt("command.remove-all-queues.web-console-pause"))
      })
  }

  @CliCommand(value = Array("purge-all-queues"), help = "Purges all queues")
  def purgeAllQueues(
    @CliOption(key = Array("force"), specifiedDefaultValue = "yes", mandatory = false, help = "No prompt") force: String,
    @CliOption(key = Array("filter"), mandatory = false, help = "The query") filter: String,
    @CliOption(key = Array("dry-run"), specifiedDefaultValue = "yes", mandatory = false, help = "Dry run") dryRun: String,
    @CliOption(key = Array("pending"), mandatory = false, help = "Only queues that meet the pending filter are listed") pending: String,
    @CliOption(key = Array("enqueued"), mandatory = false, help = "Only queues that meet the enqueued filter are listed") enqueued: String,
    @CliOption(key = Array("dequeued"), mandatory = false, help = "Only queues that meet the dequeued filter are listed") dequeued: String,
    @CliOption(key = Array("consumers"), mandatory = false, help = "Only queues that meet the consumers filter are listed") consumers: String
  ): String = {
    withFilteredQueues("purged", force, filter, dryRun, if (Option(pending).isDefined) { pending } else { "> 0" }, enqueued, dequeued, consumers,
      (webClient: WebClient, queue: String) ⇒ {
        val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/queues.jsp")
        val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("purgeDestination.action") &&
          a.getHrefAttribute.contains(s"JMSDestination=$queue&JMSDestinationType=queue"))
        try {
          anchor.get.click()
        } catch {
          case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
        }
        Thread.sleep(AmazonMQCLI.Config.getInt("command.purge-all-queues.web-console-pause"))
      })
  }

  @CliCommand(value = Array("list-queues"), help = "Displays queues")
  def listQueues( //scalastyle:ignore
    @CliOption(key = Array("filter"), mandatory = false, help = "Only queues with a name that contains the value specified by filter are listed") filter: String, //scalastyle:ignore
    @CliOption(key = Array("pending"), mandatory = false, help = "Only queues that meet the pending filter are listed") pending: String,
    @CliOption(key = Array("enqueued"), mandatory = false, help = "Only queues that meet the enqueued filter are listed") enqueued: String,
    @CliOption(key = Array("dequeued"), mandatory = false, help = "Only queues that meet the dequeued filter are listed") dequeued: String,
    @CliOption(key = Array("consumers"), mandatory = false, help = "Only queues that meet the consumers filter are listed") consumers: String
  ): String = {

    val headers = List("Queue Name", "Pending", "Consumers", "Enqueued", "Dequeued")

    val pendingCount = parseFilterParameter(pending, "pending")
    val enqueuedCount = parseFilterParameter(enqueued, "enqueued")
    val dequeuedCount = parseFilterParameter(dequeued, "dequeued")
    val consumersCount = parseFilterParameter(consumers, "consumers")

    withWebClient((webClient: WebClient) ⇒ {
      val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/queues.jsp")
      val document = scala.xml.XML.loadString(page.asXml())
      val queues: List[Map[String, Any]] = (document \ "queue").map(queue ⇒
        Map(
          "name" → (queue \@ "name"),
          "size" → (queue \ "stats" \@ "size").toLong,
          "consumerCount" → (queue \ "stats" \@ "consumerCount").toLong,
          "enqueueCount" → (queue \ "stats" \@ "enqueueCount").toLong,
          "dequeueCount" → (queue \ "stats" \@ "dequeueCount").toLong
        )).toList.filter(queue ⇒
        if (filter) {
          queue.get("name").toString.toLowerCase.contains(Option(filter).getOrElse("").toLowerCase)
        } else {
          true
        }).filter(queue ⇒ applyFilterParameter(pending, queue.get("size").get.asInstanceOf[Long], pendingCount) &&
        applyFilterParameter(consumers, queue.get("consumerCount").get.asInstanceOf[Long], consumersCount) &&
        applyFilterParameter(enqueued, queue.get("enqueueCount").get.asInstanceOf[Long], enqueuedCount) &&
        applyFilterParameter(dequeued, queue.get("dequeueCount").get.asInstanceOf[Long], dequeuedCount))

      val rows = queues.map(map ⇒ Seq(map.get("name").get, map.get("size").get, map.get("consumerCount").get,
        map.get("enqueueCount").get, map.get("dequeueCount").get))
        .seq.sortBy(AmazonMQCLI.Config.getOptionalString(s"command.list-queues.order.field") match {
          case Some("Pending") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Pending"))).asInstanceOf[String] }
          case Some("Consumers") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Consumers"))).asInstanceOf[String] }
          case Some("Enqueued") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Enqueued"))).asInstanceOf[String] }
          case Some("Dequeued") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Dequeued"))).asInstanceOf[String] }
          case _ ⇒ (row: Seq[Any]) ⇒ { row(headers.indexOf("Queue Name" + 1)).asInstanceOf[String] }
        })(AmazonMQCLI.Config.getOptionalString(s"command.queues.order.direction") match {
          case Some("reverse") ⇒ Ordering[String].reverse
          case _               ⇒ Ordering[String]
        })

      if (rows.size > 0) {
        renderTable(rows, headers) + s"\nTotal queues: ${rows.size}"
      } else {
        warn(s"No queues found")
      }
    })
  }

  def withFilteredQueues(action: String, force: String, filter: String, dryRun: Boolean, pending: String, enqueued: String, dequeued: String, //scalastyle:ignore
    consumers: String, callback: (WebClient, String) ⇒ Unit): String = {
    val pendingCount = parseFilterParameter(pending, "pending")
    val enqueuedCount = parseFilterParameter(enqueued, "enqueued")
    val dequeuedCount = parseFilterParameter(dequeued, "dequeued")
    val consumersCount = parseFilterParameter(consumers, "consumers")

    withWebClient((webClient: WebClient) ⇒ {
      if (!dryRun) confirm(force)
      val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/queues.jsp")
      val document = scala.xml.XML.loadString(page.asXml())
      val queues: List[String] = (document \ "queue").map(queue ⇒
        Map(
          "name" → (queue \@ "name").trim,
          "size" → (queue \ "stats" \@ "size").toLong,
          "consumerCount" → (queue \ "stats" \@ "consumerCount").toLong,
          "enqueueCount" → (queue \ "stats" \@ "enqueueCount").toLong,
          "dequeueCount" → (queue \ "stats" \@ "dequeueCount").toLong
        )).toList.filter(queue ⇒
        if (filter) {
          queue.get("name").toString.toLowerCase.contains(Option(filter).getOrElse("").toLowerCase)
        } else {
          true
        }).filter(queue ⇒ applyFilterParameter(pending, queue.get("size").get.asInstanceOf[Long], pendingCount) &&
        applyFilterParameter(consumers, queue.get("consumerCount").get.asInstanceOf[Long], consumersCount) &&
        applyFilterParameter(enqueued, queue.get("enqueueCount").get.asInstanceOf[Long], enqueuedCount) &&
        applyFilterParameter(dequeued, queue.get("dequeueCount").get.asInstanceOf[Long], dequeuedCount)).map(queue ⇒ {
        val queueName = queue.get("name").get.toString
        if (dryRun) {
          s"Queue to be ${action}: '${queueName}'"
        } else {
          callback(webClient, queueName)
          s"Queue ${action}: '${queueName}'"
        }
      })
      if (queues.size > 0) {
        val dryRunText = if (dryRun) "to be " else ""
        (queues.seq.sorted :+ s"Total queues ${dryRunText}${action}: ${queues.size}").mkString("\n")
      } else {
        if (action == "purged" && Option(pending).isDefined && pending == "> 0") {
          warn("No queues with pending messages found")
        } else {
          warn("No queues found")
        }
      }
    })
  }
}
