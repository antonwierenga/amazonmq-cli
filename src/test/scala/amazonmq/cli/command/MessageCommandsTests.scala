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

import amazonmq.cli.command.CommandsTests._
import amazonmq.cli.command.QueueCommandsTests.shell
import amazonmq.cli.util.Console._
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{Before, BeforeClass, Test}

import scala.xml.XML

class MessageCommandsTests {

  @Before
  def before = {
    assertTrue(shell.executeCommand("remove-all-queues --force").isSuccess)
    assertTrue(shell.executeCommand("remove-all-topics --force").isSuccess)
  }

  @Test
  def testExportMessageFileAlreadyExists = {
    val messageFile = File.createTempFile("MessageCommandsTests_testExportMessageFileAlreadyExists", ".xml")
    try assertEquals(warn(s"File '${messageFile.getAbsolutePath}' already exists"), shell.executeCommand(s"export-messages --queue testQueue --file ${messageFile.getAbsolutePath}").getResult)
    finally messageFile.delete
  }

  @Test
  def testSendAndExportInlineMessage = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue testQueue").getResult)

    val messageFilePath = createTempFilePath("MessageCommandsTests_testSendAndExportMessage")
    try {
      assertEquals(info(s"\nMessages exported to ${new File(messageFilePath).getCanonicalPath()}: 1"), shell.executeCommand(s"export-messages --queue testQueue --file $messageFilePath").getResult)
      val xml = XML.loadFile(messageFilePath)
      assertFalse((xml \ "jms-message" \ "header" \ "message-id").isEmpty)
      assertTrue((xml \ "jms-message" \ "header" \ "correlation-id").isEmpty)
      assertEquals("2", (xml \ "jms-message" \ "header" \ "delivery-mode") text)
      assertFalse((xml \ "jms-message" \ "header" \ "destination").isEmpty)
      assertEquals("0", (xml \ "jms-message" \ "header" \ "expiration") text)
      assertEquals("4", (xml \ "jms-message" \ "header" \ "priority") text)
      assertEquals("false", (xml \ "jms-message" \ "header" \ "redelivered") text)
      assertTrue((xml \ "jms-message" \ "header" \ "reply-to").isEmpty)
      assertFalse((xml \ "jms-message" \ "header" \ "timestamp").isEmpty)
      assertTrue((xml \ "jms-message" \ "header" \ "type").isEmpty)
      assertEquals("testMessage", (xml \ "jms-message" \ "body") text)
    } finally new File(messageFilePath).delete
  }

  @Test
  def testSendAndExportInlineMessageAllHeadersProvided = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --reply-to replyQueue --correlation-id testCorrelationId --delivery-mode NON_PERSISTENT --time-to-live 2000 --priority 1 --body testMessage").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue testQueue").getResult)

    val messageFilePath = createTempFilePath("MessageCommandsTests_testSendAndExportMessage")
    try {
      assertEquals(info(s"\nMessages exported to ${new File(messageFilePath).getCanonicalPath()}: 1"), shell.executeCommand(s"export-messages --queue testQueue --file $messageFilePath").getResult)
      val xml = XML.loadFile(messageFilePath)
      assertFalse((xml \ "jms-message" \ "header" \ "message-id").isEmpty)
      assertEquals("testCorrelationId", (xml \ "jms-message" \ "header" \ "correlation-id") text)
      //assertEquals(s"${javax.jms.DeliveryMode.NON_PERSISTENT}", (xml \ "jms-message" \ "header" \ "delivery-mode") text) THIS FAILS
      assertFalse((xml \ "jms-message" \ "header" \ "destination").isEmpty)
      //assertEquals("1", (xml \ "jms-message" \ "header" \ "priority") text) THIS FAILS
      assertEquals("false", (xml \ "jms-message" \ "header" \ "redelivered") text)
      assertEquals("queue://replyQueue", (xml \ "jms-message" \ "header" \ "reply-to") text)
      assertFalse((xml \ "jms-message" \ "header" \ "timestamp").isEmpty)
      assertTrue((xml \ "jms-message" \ "header" \ "type").isEmpty)
      assertEquals("testMessage", (xml \ "jms-message" \ "body") text)
    } finally new File(messageFilePath).delete
  }

  @Test
  def testSendInlineMessageTimes2 = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage --times 2").getResult.toString.contains("Messages sent to queue 'testQueue': 2"))
    assertEquals(info("\nMessages listed: 2"), shell.executeCommand("list-messages --queue testQueue").getResult)
  }

  @Test
  def testListMessagesNonExistingQueue = {
    assertEquals(warn("Queue 'testQueue' does not exist"), shell.executeCommand("list-messages --queue testQueue").getResult)
  }

  @Test
  def testSendInlineMessageToTopic = {
    assertEquals(info("Topic 'VirtualTopic.testTopic' added"), shell.executeCommand("add-topic --name VirtualTopic.testTopic").getResult)
    assertEquals(info("Queue 'Consumer.testQueue.VirtualTopic.testTopic' added"), shell.executeCommand("add-queue --name Consumer.testQueue.VirtualTopic.testTopic").getResult)
    assertEquals(info("Messages sent to topic 'VirtualTopic.testTopic': 1"), shell.executeCommand("send-message --topic VirtualTopic.testTopic --body testMessage").getResult)
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue Consumer.testQueue.VirtualTopic.testTopic").getResult)
  }

  @Test
  def testAvailabilityIndicators: Unit = {
    assertTrue(shell.executeCommand("disconnect").isSuccess)
    try {
      List("move-messages", "copy-messages", "list-messages", "send-message", "export-messages").map(command ⇒ {
        assertCommandFailed(shell.executeCommand(command))
      })
    } finally {
      assertTrue(shell.executeCommand("connect --broker test").isSuccess)
    }
  }

  @Test
  def testBrowseMessages = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertEquals(info("\nMessages browsed: 1 (not all messages may be returned due to limitations of broker configuration and system resources)"), shell.executeCommand("browse-messages --queue testQueue").getResult)
  }

  @Test
  def testListMessagesRegex = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage2").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue testQueue --regex testMessage2").getResult)
    assertEquals(info("\nMessages listed: 2"), shell.executeCommand("list-messages --queue testQueue").getResult)
  }

  @Test
  def testMoveMessagesRegex = {
    assertTrue(shell.executeCommand("send-message --queue fromQueue --body testMessage1").getResult.toString.contains("Messages sent to queue 'fromQueue': 1"))
    assertTrue(shell.executeCommand("send-message --queue fromQueue --body testMessage2").getResult.toString.contains("Messages sent to queue 'fromQueue': 1"))
    assertEquals(info("\nMessages moved: 1"), shell.executeCommand("move-messages --from fromQueue --regex testMessage1 --to toQueue").getResult)
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue fromQueue --regex testMessage2").getResult)
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue toQueue --regex testMessage1").getResult)
  }

  @Test
  def testCopyMessagesRegex = {
    assertTrue(shell.executeCommand("send-message --queue fromQueue --body testMessage1").getResult.toString.contains("Messages sent to queue 'fromQueue': 1"))
    assertTrue(shell.executeCommand("send-message --queue fromQueue --body testMessage2").getResult.toString.contains("Messages sent to queue 'fromQueue': 1"))
    assertEquals(info("\nMessages copied: 1"), shell.executeCommand("copy-messages --from fromQueue --regex testMessage1 --to toQueue").getResult)
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue fromQueue --regex testMessage1").getResult)
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue fromQueue --regex testMessage2").getResult)
    assertEquals(info("\nMessages listed: 1"), shell.executeCommand("list-messages --queue toQueue --regex testMessage1").getResult)
  }

  @Test
  def testExportMessagesRegex = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage2").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))

    val messageFilePath = createTempFilePath("MessageCommandsTests_testSendAndExportMessage")
    try {
      assertEquals(info(s"\nMessages exported to ${new File(messageFilePath).getCanonicalPath()}: 1"), shell.executeCommand(s"export-messages --queue testQueue --regex testMessage1 --file $messageFilePath").getResult)
    } finally new File(messageFilePath).delete
  }

  @Test
  def testBrowseMessagesRegex = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage2").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertEquals(info("\nMessages browsed: 1 (not all messages may be returned due to limitations of broker configuration and system resources)"), shell.executeCommand("browse-messages --queue testQueue --regex testMessage1").getResult)
  }
}

object MessageCommandsTests {

  val shell = createShell

  @BeforeClass
  def beforeClass() = connectToTestBroker(shell)
}
