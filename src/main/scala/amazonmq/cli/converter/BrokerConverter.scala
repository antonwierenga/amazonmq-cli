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

package amazonmq.cli.converter

import java.util.List

import amazonmq.cli.AmazonMQCLI
import amazonmq.cli.domain.Broker
import org.springframework.shell.core.{ Completion, Converter, MethodTarget }
import org.springframework.stereotype.Component

import scala.collection.JavaConversions._

@Component
class BrokerConverter extends Converter[Broker] {

  @Override
  def convertFromText(text: String, requiredType: Class[_], optionContext: String): Broker = {
    new Broker(text, null, null, null, null) //scalastyle:ignore
  }

  @Override
  def supports(requiredType: Class[_], optionContext: String): Boolean = {
    classOf[Broker].isAssignableFrom(requiredType)
  }

  @Override
  def getAllPossibleValues(completions: List[Completion], requiredType: Class[_],
    existingData: String, optionContext: String, target: MethodTarget): Boolean = {
    AmazonMQCLI.Config.getObject("broker").foreach({ case (k: String, v) ⇒ completions.add(new Completion(k)) })
    true
  }
}
