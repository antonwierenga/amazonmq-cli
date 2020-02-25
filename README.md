# amazonmq-cli
Command-line tool (Windows/macOS/Linux) to interact with the Amazon MQ message broker.

amazonmq-cli requires access to the [ActiveMQ Web Console](https://activemq.apache.org/web-console) of the Amazon MQ message broker.

![screenshot](/amazonmq_screenshot.jpg)

## Installation
Download amazonmq-cli in the [release section](https://github.com/antonwierenga/amazonmq-cli/releases) of this repository.

Unzip the amazonmq-cli-x.x.x.zip file and configure the broker you want to connect to in `amazonmq-cli-x.x.x/conf/amazonmq-cli.config`:

```scala
broker {
  my-aws-broker {
    web-console = "https://your-amazon-mq-broker-host:8162/admin/"
    amqurl = "ssl://your-amazon-mq-broker-host:61617"
    username = "admin"
    password = ""
    prompt-color = "light-blue" // Possible values: "gray", "red", "light-red", "light-green", "green", "light-yellow", "yellow", "light-blue", "blue", "light-purple", "purple", "light-cyan", "cyan", "light-white", "white"
  }

  // add additional brokers here
  test {
    web-console = "https://your-amazon-mq-test-broker-host:8162/admin/"
    amqurl = "ssl://your-amazon-mq-test-broker-host:61617"
    username = "admin"
    password = "secret"
    prompt-color = "light-blue" // Possible values: "gray", "red", "light-red", "light-green", "green", "light-yellow", "yellow", "light-blue", "blue", "light-purple", "purple", "light-cyan", "cyan", "light-white", "white"
  } 
}
```

## Usage
To enter the amazonmq-cli shell run `amazonmq-cli-x.x.x/bin/amazonmq-cli` or `amazonmq-cli-x.x.x/bin/amazonmq-cli.bat` if you are using Windows.

amazonmq-cli provides tab completion to speed up typing commands, to see which commands are available and what parameters are supported.

*In addition to executing commands in the shell, amazonmq-cli also supports executing a file containing commands:*
`amazonmq-cli --cmdfile my_commands.txt`

To connect to a broker that is configured in `amazonmq-cli-x.x.x/conf/amazonmq-cli.config`: `connect --broker dev`

Below is a list of commands that amazonmq-cli supports.

### add-queue
Adds a queue.

##### Parameters:
  - name

Example:`add-queue --name foo`

### add-topic
Adds a topic.

##### Parameters:
  - name

Example:`add-topic --name foo`

### browse-messages
Browse messages. The browse operation may not return all messages due to limitations of broker configuration and system resources. In contrast to list-messages the browse-messages command also takes in-flight messages into consideration.

##### Parameters:
  - queue
  - selector (browse messages that match the (JMS) selector)
  - regex (browse messages whose body match the regex)

Example 1:`browse-messages --queue foo`

Example 2:`browse-messages --queue foo --selector "JMSCorrelationID = '12345'"`

Example 3:`browse-messages --queue foo --regex bar`

### connect
Connects amazonmq-cli to a broker.

##### Parameters:
  - broker (broker must be defined in `amazonmq-cli-x.x.x/conf/amazonmq-cli.config`)

Example:`connect --broker my-aws-broker`

### copy-messages
Copies messages from a queue to another queue.

##### Parameters:
  - from
  - to 
  - selector (copy messages that match the (JMS) selector)
  - regex (copy messages whose body match the regex)
  
Example:`copy-messages --from foo --to bar`

*For this command amazonmq-cli consumes all messages from the --from queue and sends the messages back to the --from and --to queues. In flight messages are not in scope of this command.*

### disconnect
Disconnects amazonmq-cli from the broker.

Example:`disconnect`

### export-messages
Exports messages to file.

##### Parameters:
  - file
  - queue
  - selector (export messages that match the (JMS) selector)
  - regex (export messages whose body match the regex)
 
Example:`export-messages --queue foo`

*For this command amazonmq-cli consumes all messages from the --from queue and sends the messages back to the --from queue. In flight messages are not in scope of this command.*

### info
Displays information (e.g. version, uptime, total number of queues/topics/messages) about the broker 

Example:`info`

### list-messages
Lists messages.

##### Parameters:
  - queue
  - selector (lists messages that match the (JMS) selector)
  - regex (lists messages whose body match the regex)
 
Example 1:`list-messages --queue foo`

Example 2:`list-messages --queue foo --selector "JMSCorrelationID = '12345'"`

Example 3:`list-messages --queue foo --regex bar`

*For this command amazonmq-cli consumes all messages from the --from queue and sends the messages back to the --from queue. In flight messages are not in scope of this command.*

### list-queues
Lists queues.

##### Parameters:
  - filter (list queues with the specified filter in the name)
  - pending (list queues for which the number of pending messages meets the pending filter)
  - enqueued (list queues for which the number of enqueued messages meets the enqueued filter)
  - dequeued (list queues for which the number of dequeued messages meets the dequeued filter)
  - consumers (list queues for which the number of consumers meets the consumers filter)
  
Example 1:`queues --filter foo`

Example 2:`queues --pending >0 --consumers =0`

### list-topics
Lists topics.

##### Parameters:
  - filter (list topics with the specified filter in the name)
  - enqueued (remove topics for which the number of enqueued messages meets the enqueued filter)
  - dequeued (remove topics for which the number of dequeued messages meets the dequeued filter)   

Example 1:`topics --filter foo`

Example 2:`topics --enqueued >0`

### move-messages
Moves messages from a queue to another queue.

##### Parameters:
  - from
  - to 
  - selector (move messages that match the (JMS) selector)
  - regex (move messages whose body match the regex)
  
*For this command amazonmq-cli consumes all messages from the --from queue and sends the messages to the --to queue. In flight messages are not in scope of this command.*

### purge-all-queues
Purges all queues.

##### Parameters:
  - force (no prompt for confirmation)
  - dry-run (use this to test what is going to be purged, no queues are actually purged)
  - filter (queues with the specified filter in the name)
  - pending (purge queues for which the number of pending messages meets the pending filter)
  - enqueued (purge queues for which the number of enqueued messages meets the enqueued filter)
  - dequeued (purge queues for which the number of dequeued messages meets the dequeued filter)
  - consumers (purge queues for which the number of consumers meets the consumers filter)
  
Example 1:`purge-all-queues`

Example 2:`purge-all-queues --filter foo --consumers =0 --dry-run`

### purge-queue
Purges a queues.

##### Parameters:
  - name 
  - force (no prompt for confirmation)

Example:`purge-queue --name foo`

### release-notes
Displays the release notes.

Example:`release-notes`

### remove-all-queues
Removes all queues.

##### Parameters:
  - force (no prompt for confirmation)
  - dry-run (use this to test what is going to be removed, no queues are actually removed)
  - filter (queues with the specified filter in the name)
  - pending (remove queues for which the number of pending messages meets the pending filter)
  - enqueued (remove queues for which the number of enqueued messages meets the enqueued filter)
  - dequeued (remove queues for which the number of dequeued messages meets the dequeued filter)
  - consumers (remove queues for which the number of consumers meets the consumers filter)
  
Example 1:`remove-all-queues`

Example 2:`remove-all-queues --filter foo --consumers =0 --dry-run`

### remove-all-topics
Removes all topics.

##### Parameters:
  - force (no prompt for confirmation)
  - dry-run (use this to test what is going to be removed, no queues are actually removed)
  - filter (queues with the specified filter in the name)
  - enqueued (remove topics for which the number of enqueued messages meets the enqueued filter)
  - dequeued (remove topics for which the number of dequeued messages meets the dequeued filter)  

Example 1:`remove-all-topics`

Example 2:`remove-all-topics --filter foo --enqueued =0 --dry-run`

### remove-queue
Removes a queue.

##### Parameters:
  - name 
  - force (no prompt for confirmation)

Example:`remove-queue --name foo`

### remove-topic
Removes a topic.

##### Parameters:
  - name 
  - force (no prompt for confirmation)

Example:`remove-topic --name foo`

### send-message
Sends a message or file of messages to a queue or topic.

##### Parameters:
  - body     
  - queue 
  - topic
  - priority (not applicable if -file is specified)                    
  - correlation-id (not applicable if -file is specified) 
  - reply-to (not applicable if -file is specified)  
  - delivery-mode (not applicable if -file is specified) 
  - time-to-live (not applicable if -file is specified) 
  - times (number of times the message is sent)
  - file

Example file:
```xml
<jms-messages>
  <jms-message>
    <body>Message 1</body>
  </jms-message>
  <jms-message>
    <body>Message 2</body>
  </jms-message>  
  <jms-message>
    <header>
      <priority>0</priority>
      <correlation-id>12345</correlation-id>
      <reply-to>myRepliesQueue</reply-to>
      <delivery-mode>2</delivery-mode>
      <time-to-live>1000</time-to-live>
    </header>
    <properties>
      <property>
        <name>my_custom_property</name>
        <value>1</value>
      </property>        
    </properties>      
    <body><![CDATA[<?xml version="1.0"?>
<catalog>
   <book id="1">
      <author>Basil, Matthew</author>
      <title>XML Developer's Guide</title>
      <genre>Computer</genre>
      <price>44.95</price>
      <publish_date>2002-10-01</publish_date>
      <description>An in-depth look at creating applications with XML.</description>
   </book>
</catalog>]]></body>
  </jms-message>  
</jms-messages>
```

Example 1:`send-message --body foo --queue bar`

Example 2:`send-message --file foo.xml --topic bar`

