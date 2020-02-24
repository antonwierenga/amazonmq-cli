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

import CommandsTests._
import MessageCommandsTests._
import amazonmq.cli.util.Console._
import java.io.File

import amazonmq.cli.AmazonMQCLI
import org.junit.After
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import org.springframework.shell.Bootstrap
import org.springframework.shell.core.CommandResult
import org.springframework.shell.core.JLineShellComponent

import scala.xml.XML

class BrokerCommandsTests {

  @Before
  def before = {
    assertTrue(shell.executeCommand("remove-all-queues --force").isSuccess)
    assertTrue(shell.executeCommand("remove-all-topics --force").isSuccess)
  }

  @Test
  def testAvailabilityIndicators: Unit = {
    assertTrue(shell.executeCommand("disconnect").isSuccess)
    try {
      List("info", "disconnect", "web-console").map(command ⇒ {
        assertCommandFailed(shell.executeCommand(command))
      })
    } finally {
      assertTrue(shell.executeCommand("connect --broker test").isSuccess)
    }
  }
}

object BrokerCommandsTests {

  val shell = createShell

  @BeforeClass
  def beforeClass() = connectToTestBroker(shell)
}
