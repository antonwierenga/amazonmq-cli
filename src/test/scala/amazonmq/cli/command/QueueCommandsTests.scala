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

import amazonmq.cli.command.CommandsTests._
import amazonmq.cli.command.QueueCommandsTests._
import amazonmq.cli.util.Console._
import org.junit.Assert.{assertEquals, assertFalse, assertTrue}
import org.junit.{Before, BeforeClass, Test}

class QueueCommandsTests {

  @Before
  def before = {
    assertTrue(shell.executeCommand("remove-all-queues --force").isSuccess)
  }

  @Test
  def testQueuesEmpty = {
    assertEquals(warn(s"No queues found"), shell.executeCommand("list-queues").getResult)
  }

  @Test
  def testAddQueue = {
    assertEquals(info("Queue 'testQueue' added"), shell.executeCommand("add-queue --name testQueue").getResult)
    assertEquals(
      """|  Queue Name  Pending  Consumers  Enqueued  Dequeued
         |  ----------  -------  ---------  --------  --------
         |  testQueue   0        0          0         0
         |
         |Total queues: 1""".stripMargin,
      shell.executeCommand("list-queues").getResult
    )
  }

  @Test
  def testRemoveQueue = {
    assertEquals(info("Queue 'testQueue' added"), shell.executeCommand("add-queue --name testQueue").getResult)
    assertEquals(info("Queue 'testQueue' removed"), shell.executeCommand("remove-queue --name testQueue --force").getResult)
    assertEquals(warn(s"No queues found"), shell.executeCommand("list-queues").getResult)
  }

  @Test
  def testPurgeQueue = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))
    assertEquals(
      """|  Queue Name  Pending  Consumers  Enqueued  Dequeued
         |  ----------  -------  ---------  --------  --------
         |  testQueue   1        0          1         0
         |
         |Total queues: 1""".stripMargin,
      shell.executeCommand("list-queues").getResult
    )

    assertEquals(info("Queue 'testQueue' purged"), shell.executeCommand("purge-queue --name testQueue --force").getResult)
    assertEquals(
      """|  Queue Name  Pending  Consumers  Enqueued  Dequeued
         |  ----------  -------  ---------  --------  --------
         |  testQueue   0        0          1         1
         |
         |Total queues: 1""".stripMargin,
      shell.executeCommand("list-queues").getResult
    )
  }

  @Test
  def testPurgeNonExistingQueue = {
    assertEquals(warn("Queue 'testQueue' does not exist"), shell.executeCommand("purge-queue --name testQueue --force").getResult)
  }

  @Test
  def testRemoveAllQueues = {
    assertEquals(info("Queue 'testQueue1' added"), shell.executeCommand("add-queue --name testQueue1").getResult)
    assertEquals(info("Queue 'testQueue2' added"), shell.executeCommand("add-queue --name testQueue2").getResult)

    val removeAllQueuesDryRunResult = shell.executeCommand("remove-all-queues --dry-run").getResult.toString
    assertTrue(removeAllQueuesDryRunResult.contains("Queue to be removed: 'testQueue1'"))
    assertTrue(removeAllQueuesDryRunResult.contains("Queue to be removed: 'testQueue2'"))
    assertTrue(removeAllQueuesDryRunResult.contains("Total queues to be removed: 2"))

    val listQueuesResultBefore = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultBefore.contains("testQueue1  0        0          0         0"))
    assertTrue(listQueuesResultBefore.contains("testQueue2  0        0          0         0"))

    val removeAllQueuesResult = shell.executeCommand("remove-all-queues --force").getResult.toString
    assertTrue(removeAllQueuesResult.contains("Queue removed: 'testQueue1'"))
    assertTrue(removeAllQueuesResult.contains("Queue removed: 'testQueue2'"))
    assertTrue(removeAllQueuesResult.contains("Total queues removed: 2"))

    assertEquals(warn(s"No queues found"), shell.executeCommand("list-queues").getResult)
  }

  @Test
  def testPurgeAllQueuesDryRun = {
    assertTrue(shell.executeCommand("send-message --queue testQueue1 --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue1': 1"))
    assertTrue(shell.executeCommand("send-message --queue testQueue2 --body testMessage2").getResult.toString.contains("Messages sent to queue 'testQueue2': 1"))

    val listQueuesResultBefore = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultBefore.contains("testQueue1  1        0          1         0"))
    assertTrue(listQueuesResultBefore.contains("testQueue2  1        0          1         0"))

    val purgeAllQueuesResult = shell.executeCommand("purge-all-queues --force --dry-run").getResult.toString
    assertTrue(purgeAllQueuesResult.contains("Queue to be purged: 'testQueue1'"))
    assertTrue(purgeAllQueuesResult.contains("Queue to be purged: 'testQueue2'"))
    assertTrue(purgeAllQueuesResult.contains("Total queues to be purged: 2"))

    val listQueuesResultAfter = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultAfter.contains("testQueue1  1        0          1         0"))
    assertTrue(listQueuesResultAfter.contains("testQueue2  1        0          1         0"))
  }

  @Test
  def testPurgeAllQueuesFilter = {
    assertTrue(shell.executeCommand("send-message --queue testQueue1 --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue1': 1"))
    assertTrue(shell.executeCommand("send-message --queue testQueue2 --body testMessage2").getResult.toString.contains("Messages sent to queue 'testQueue2': 1"))

    val listQueuesResultBefore = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultBefore.contains("testQueue1  1        0          1         0"))
    assertTrue(listQueuesResultBefore.contains("testQueue2  1        0          1         0"))

    assertEquals(
      """|Queue purged: 'testQueue2'
         |Total queues purged: 1""".stripMargin,
      shell.executeCommand("purge-all-queues --force --filter 2").getResult
    )

    val listQueuesResultAfter = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultAfter.contains("testQueue1  1        0          1         0"))
    assertTrue(listQueuesResultAfter.contains("testQueue2  0        0          1         1"))
  }

  @Test
  def testPurgeAllQueues = {
    assertTrue(shell.executeCommand("send-message --queue testQueue1 --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue1': 1"))
    assertTrue(shell.executeCommand("send-message --queue testQueue2 --body testMessage2").getResult.toString.contains("Messages sent to queue 'testQueue2': 1"))

    val listQueuesResultBefore = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultBefore.contains("testQueue1  1        0          1         0"))
    assertTrue(listQueuesResultBefore.contains("testQueue2  1        0          1         0"))

    val purgeAllQueuesResult = shell.executeCommand("purge-all-queues --force").getResult.toString
    assertTrue(purgeAllQueuesResult.contains("Queue purged: 'testQueue1'"))
    assertTrue(purgeAllQueuesResult.contains("Queue purged: 'testQueue2'"))

    val listQueuesResultAfter = shell.executeCommand("list-queues").getResult.toString
    assertTrue(listQueuesResultAfter.contains("testQueue1  0        0          1         1"))
    assertTrue(listQueuesResultAfter.contains("testQueue2  0        0          1         1"))
  }

  @Test
  def testListQueuesFilters = {
    assertTrue(shell.executeCommand("send-message --queue testQueue --body testMessage1").getResult.toString.contains("Messages sent to queue 'testQueue': 1"))

    List(
      "list-queues --pending =1",
      "list-queues --pending >0",
      "list-queues --pending <2",
      "list-queues --enqueued =1",
      "list-queues --enqueued >0",
      "list-queues --enqueued <2",
      "list-queues --dequeued =0",
      "list-queues --dequeued >-1",
      "list-queues --dequeued <1",
      "list-queues --consumers =0",
      "list-queues --consumers >-1",
      "list-queues --consumers <1",
      "list-queues --pending >0 --consumers <1",
      "list-queues --enqueued >0 --dequeued <1"

    ).map { command ⇒
        assertEquals("""|  Queue Name  Pending  Consumers  Enqueued  Dequeued
                        |  ----------  -------  ---------  --------  --------
                        |  testQueue   1        0          1         0
                        |
                        |Total queues: 1""".stripMargin, shell.executeCommand(command).getResult)
      }

    assertFalse(List(
      "list-queues --pending =0",
      "list-queues --pending >1",
      "list-queues --pending <1",
      "list-queues --enqueued =0",
      "list-queues --enqueued >1",
      "list-queues --enqueued <1",
      "list-queues --dequeued =1",
      "list-queues --dequeued >0",
      "list-queues --dequeued <0",
      "list-queues --consumers =1",
      "list-queues --consumers >0",
      "list-queues --consumers <0",
      "list-queues --pending >0 --consumers >0",
      "list-queues --enqueued >0 --dequeued >0"
    ).map { command ⇒
        assertEquals(warn("No queues found"), shell.executeCommand(command).getResult)
      }.isEmpty)
  }

  @Test
  def testRemoveNonExistingQueue = {
    assertEquals(warn("Queue 'testQueue' does not exist"), shell.executeCommand("remove-queue --name testQueue --force").getResult)
  }

  @Test
  def testAddExistingQueue = {
    assertEquals(info("Queue 'testQueue' added"), shell.executeCommand("add-queue --name testQueue").getResult)
    assertEquals(warn("Queue 'testQueue' already exists"), shell.executeCommand("add-queue --name testQueue").getResult)
  }

  @Test
  def testAvailabilityIndicators: Unit = {
    assertTrue(shell.executeCommand("disconnect").isSuccess)
    try {
      List("list-queues", "add-queue", "purge-queue", "purge-all-queues", "remove-queue", "remove-all-queues").map(command ⇒ {
        assertCommandFailed(shell.executeCommand(command))
      })
    } finally {
      assertTrue(shell.executeCommand("connect --broker test").isSuccess)
    }
  }
}

object QueueCommandsTests {

  val shell = createShell

  @BeforeClass
  def beforeClass() = connectToTestBroker(shell)
}
