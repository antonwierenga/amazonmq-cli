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
import com.gargoylesoftware.htmlunit.html.{DomElement, HtmlAnchor, HtmlInput, HtmlPage, HtmlSubmitInput}
import org.springframework.shell.core.annotation.CliAvailabilityIndicator
import org.springframework.shell.core.annotation.CliCommand
import org.springframework.shell.core.annotation.CliOption
import org.springframework.stereotype.Component
import com.gargoylesoftware.htmlunit.WebClient
import com.gargoylesoftware.htmlunit.xml.XmlPage
import scala.collection.JavaConversions._

@Component
class TopicCommands extends Commands {

  @CliAvailabilityIndicator(Array("add-topic", "remove-topic", "list-topics", "remove-all-topics"))
  def isBrokerAvailable: Boolean = AmazonMQCLI.broker.isDefined

  @CliCommand(value = Array("add-topic"), help = "Adds a topic")
  def addTopic(@CliOption(key = Array("name"), mandatory = true, help = "The Name of the Topic") name: String): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateTopicNotExists(webClient, name)
      val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/topics.jsp")
      val form = page.getForms().find(_.getActionAttribute == "createDestination.action").get
      val inputField: HtmlInput = form.getInputByName("JMSDestination")
      inputField.setValueAttribute(name)
      val submitButton: DomElement = form.getFirstByXPath("//input[@value='Create']")
      try {
        submitButton.click()
      } catch {
        case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
      }
      info(s"Topic '$name' added")
    })
  }

  @CliCommand(value = Array("remove-topic"), help = "Removes a topic")
  def removeTopic(
    @CliOption(key = Array("name"), mandatory = true, help = "The Name of the Topic") name: String,
    @CliOption(key = Array("force"), specifiedDefaultValue = "yes", mandatory = false, help = "No prompt") force: String
  ): String = {
    withWebClient((webClient: WebClient) ⇒ {
      validateTopicExists(webClient, name)
      confirm(force)
      val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/topics.jsp")
      val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("deleteDestination.action") &&
        a.getHrefAttribute.contains(s"JMSDestination=$name&JMSDestinationType=topic"))
      try {
        anchor.get.click()
      } catch {
        case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
      }
      info(s"Topic '$name' removed")
    })
  }

  @CliCommand(value = Array("remove-all-topics"), help = "Removes all topics")
  def removeAllTopics(
    @CliOption(key = Array("force"), specifiedDefaultValue = "yes", mandatory = false, help = "No prompt") force: String,
    @CliOption(key = Array("filter"), mandatory = false, help = "The topic") filter: String,
    @CliOption(key = Array("dry-run"), specifiedDefaultValue = "yes", mandatory = false, help = "Dry run") dryRun: String,
    @CliOption(key = Array("enqueued"), mandatory = false, help = "Only topics that meet the enqueued filter are listed") enqueued: String,
    @CliOption(key = Array("dequeued"), mandatory = false, help = "Only topics that meet the dequeued filter are listed") dequeued: String,
    @CliOption(key = Array("consumers"), mandatory = false, help = "Only topics that meet the consumers filter are listed") consumers: String
  ): String = {
    withFilteredTopics("removed", force, filter, dryRun, enqueued, dequeued, consumers,
      (webClient: WebClient, topic: String) ⇒ {
        val page: HtmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/topics.jsp")

        val anchor = page.getAnchors().find(a ⇒ a.getHrefAttribute.startsWith("deleteDestination.action") &&
          a.getHrefAttribute.contains(s"JMSDestination=$topic&JMSDestinationType=topic"))

        if (anchor.isEmpty && topic.startsWith("ActiveMQ.Advisory")) {
          // do nothing, the removal of a previous topic may have resulted that topics starting with 'ActiveMQ.Advisory' no longer exist
        } else {
          try {
            anchor.get.click()
          } catch {
            case arrayIndexOutOfBoundsException: ArrayIndexOutOfBoundsException ⇒ // do nothing
          }
        }
        Thread.sleep(AmazonMQCLI.Config.getInt("web-console.pause"))
      })
  }

  @CliCommand(value = Array("list-topics"), help = "Displays topics")
  def listTopics(
    @CliOption(key = Array("filter"), mandatory = false, help = "The topic") filter: String,
    @CliOption(key = Array("enqueued"), mandatory = false, help = "Only topics that meet the enqueued filter are listed") enqueued: String,
    @CliOption(key = Array("dequeued"), mandatory = false, help = "Only topics that meet the dequeued filter are listed") dequeued: String,
    @CliOption(key = Array("consumers"), mandatory = false, help = "Only topics that meet the consumers filter are listed") consumers: String
  ): String = {

    val headers = List("Topic Name", "Consumers", "Enqueued", "Dequeued")

    val consumersCount = parseFilterParameter(consumers, "consumers")
    val enqueuedCount = parseFilterParameter(enqueued, "enqueued")
    val dequeuedCount = parseFilterParameter(dequeued, "dequeued")

    withWebClient((webClient: WebClient) ⇒ {
      val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/topics.jsp")
      val document = scala.xml.XML.loadString(page.asXml())
      val topics: List[Map[String, Any]] = (document \ "topic").map(topic ⇒
        Map(
          "name" → (topic \@ "name"),
          "consumerCount" → (topic \ "stats" \@ "consumerCount").toLong,
          "enqueueCount" → (topic \ "stats" \@ "enqueueCount").toLong,
          "dequeueCount" → (topic \ "stats" \@ "dequeueCount").toLong
        )).toList.filter(topic ⇒
        if (filter) {
          topic.get("name").toString.toLowerCase.contains(Option(filter).getOrElse("").toLowerCase)
        } else {
          true
        }).filter(topic ⇒ applyFilterParameter(consumers, topic.get("consumerCount").get.asInstanceOf[Long], consumersCount) &&
        applyFilterParameter(enqueued, topic.get("enqueueCount").get.asInstanceOf[Long], enqueuedCount) &&
        applyFilterParameter(dequeued, topic.get("dequeueCount").get.asInstanceOf[Long], dequeuedCount))

      val rows = topics.map(map ⇒ Seq(map.get("name").get.toString.trim, map.get("consumerCount").get, map.get("enqueueCount").get, map.get("dequeueCount").get))
        .seq.sortBy(AmazonMQCLI.Config.getOptionalString(s"command.list-topics.order.field") match {
          case Some("Consumers") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Consumers"))).asInstanceOf[String] }
          case Some("Enqueued") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Enqueued"))).asInstanceOf[String] }
          case Some("Dequeued") ⇒ (row: Seq[Any]) ⇒ { "%015d".format(row(headers.indexOf("Dequeued"))).asInstanceOf[String] }
          case _ ⇒ (row: Seq[Any]) ⇒ { row(0).asInstanceOf[String] }
        })

      if (rows.size > 0) {
        renderTable(rows, headers) + s"\nTotal topics: ${rows.size}"
      } else {
        warn(s"No topics found")
      }
    })
  }

  def withFilteredTopics(action: String, force: String, filter: String, dryRun: Boolean, enqueued: String, dequeued: String,
    consumers: String, callback: (WebClient, String) ⇒ Unit): String = {
    val consumersCount = parseFilterParameter(consumers, "consumers")
    val enqueuedCount = parseFilterParameter(enqueued, "enqueued")
    val dequeuedCount = parseFilterParameter(dequeued, "dequeued")

    withWebClient((webClient: WebClient) ⇒ {
      val page: XmlPage = webClient.getPage(s"${AmazonMQCLI.broker.get.webConsole}/xml/topics.jsp")
      val document = scala.xml.XML.loadString(page.asXml())
      val topics: List[String] = (document \ "topic").map(topic ⇒
        Map(
          "name" → (topic \@ "name"),
          "consumerCount" → (topic \ "stats" \@ "consumerCount").toLong,
          "enqueueCount" → (topic \ "stats" \@ "enqueueCount").toLong,
          "dequeueCount" → (topic \ "stats" \@ "dequeueCount").toLong
        )).toList.filter(topic ⇒
        if (filter) {
          topic.get("name").toString.toLowerCase.contains(Option(filter).getOrElse("").toLowerCase)
        } else {
          true
        }).filter(topic ⇒ applyFilterParameter(consumers, topic.get("consumerCount").get.asInstanceOf[Long], consumersCount) &&
        applyFilterParameter(enqueued, topic.get("enqueueCount").get.asInstanceOf[Long], enqueuedCount) &&
        applyFilterParameter(dequeued, topic.get("dequeueCount").get.asInstanceOf[Long], dequeuedCount)).map(topic ⇒ {
        val topicName = topic.get("name").get.toString.trim
        if (dryRun) {
          s"Topic to be ${action}: '${topicName}'"
        } else {
          callback(webClient, topicName)
          s"Topic ${action}: '${topicName}'"
        }
      })
      if (topics.size > 0) {
        val dryRunText = if (dryRun) "to be " else ""
        (topics.seq.sorted :+ s"Total topics ${dryRunText}${action}: ${topics.size}").mkString("\n")
      } else {
        warn(s"No topics found")
      }
    })
  }
}
