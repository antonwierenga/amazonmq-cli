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

package amazonmq.cli.plugin

import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.shell.plugin.support.DefaultBannerProvider
import org.springframework.stereotype.Component
import amazonmq.cli.AmazonMQCLI

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
class BannerProvider extends DefaultBannerProvider {

  override def getBanner: String = {
    """   ___                          __  _______     _______   ____
      |  / _ | __ _ ___ _______  ___  /  |/  / __ \   / ___/ /  /  _/
      | / __ |/  ' / _ `/_ / _ \/ _ \/ /|_/ / /_/ /  / /__/ /___/ /
      |/_/ |_/_/_/_\_,_//__\___/_//_/_/  /_/\___\_\  \___/____/___/
      |                                                               """.stripMargin
  }

  override def getVersion: String = AmazonMQCLI.ReleaseNotes.keySet.toSeq.sorted.reverse.head

  override def getWelcomeMessage: String = s"Welcome to AmazonMQ CLI $getVersion"

  override def getProviderName: String = "AmazonMQ CLI"
}
