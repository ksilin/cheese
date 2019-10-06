/*
 * Copyright (c) 2019, konstantin.silin
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors
 *    may be used to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.example

import java.util.Properties

import com.typesafe.scalalogging.StrictLogging
import org.apache.kafka.clients.admin.{ AdminClient, AdminClientConfig, NewTopic }
import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.{
  KafkaProducer,
  ProducerConfig,
  ProducerRecord,
  RecordMetadata
}
import org.apache.kafka.streams.kstream.{ Consumed, KStream, KTable }
import org.apache.kafka.streams.{ KafkaStreams, KeyValue, StreamsBuilder, StreamsConfig }
import org.scalatest.{ FreeSpec, MustMatchers }

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration._
// import scala.jdk.CollectionConverters._ // 2.13
import scala.collection.JavaConverters._
import scala.util.Random
import scala.concurrent.ExecutionContext.Implicits.global

import org.apache.kafka.streams.scala.ImplicitConversions._
import org.apache.kafka.streams.scala._
import org.apache.kafka.streams.{ KafkaStreams, Topology }

class SimpleStreamsSpec extends FreeSpec with MustMatchers with StrictLogging {
  import FutureConverter._

  val bootstrap = "localhost:9091"

  val appId = this.suiteName

  val streamConfigs = new Properties()
  streamConfigs.put(StreamsConfig.APPLICATION_ID_CONFIG, appId)
  streamConfigs.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
  streamConfigs.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest")

  val stringSerde       = Serdes.String
  val builder           = new StreamsBuilder()
  private val topicName = appId

  val simpleStream: KStream[String, String] =
    builder.stream(topicName, Consumed.`with`(stringSerde, stringSerde))

  var processed = 0

  simpleStream.map[String, String] { (k, v) =>
    logger.info(s"mapping record: $k, $v")
   (k, v.toUpperCase())
  } foreach((k, v) => processed = processed + 1)

  // why cannot I reduce an ungrouped stream?
  // overloaded method value aggregate with alternatives:
  //[error]        (x$1: org.apache.kafka.streams.kstream.Initializer[Int],x$2: org.apache.kafka.streams.kstream.Aggregator[_ >: String, _ >: String, Int],x$3: org.apache.kafka.streams.kstream.Materialized[String,Int,org.apache.kafka.streams.state.KeyValueStore[org.apache.kafka.common.utils.Bytes,Array[Byte]]])org.apache.kafka.streams.kstream.KTable[String,Int] <and>
  //[error]        (x$1: org.apache.kafka.streams.kstream.Initializer[Int],x$2: org.apache.kafka.streams.kstream.Aggregator[_ >: String, _ >: String, Int])org.apache.kafka.streams.kstream.KTable[String,Int]
  //[error]       cannot be applied to (Int)
   // val gStream = simpleStream.groupByKey().aggregate[Int](0, (k: String, v: String, agg: Int) => agg + 1)

  val topo = builder.build() // TODO - what properties can be passed at build time?

  createTestTopic(topicName)
  val testDataCount = 10
  produceTestData(topicName, testDataCount)

  val streams = new KafkaStreams(topo, streamConfigs)

  "run appp" in {
    logger.info("running")
    streams.start()
    Thread.sleep(3000)
    streams.close()
    logger.info("stopped")
    processed mustBe testDataCount
  }

  def createTestTopic(topicName: String): Unit = {

    val adminProps = new Properties()
    adminProps.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
    val adminClient = AdminClient.create(adminProps)
    addShutdownHook(adminClient)

    val topic              = new NewTopic(topicName, 1, 1)
    val topicCreate        = adminClient.createTopics(List(topic).asJava)
    val topicCreated: Void = Await.result(topicCreate.all(), 100.seconds)
    logger.info(s"topic created: ${topicCreated}")
  }

  def prodConfig(): Properties = {
    val props = new Properties()
    props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrap)
    props.put(
      ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
      stringSerde.serializer().getClass
    )
    props.put(
      ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
      stringSerde.serializer().getClass // "org.apache.kafka.common.serialization.StringSerializer"
    )
    props
  }

  def produceTestData(topicName: String, count: Int = 1): Seq[RecordMetadata] = {

    val producer: KafkaProducer[String, String] = new KafkaProducer(prodConfig())
    addShutdownHook(producer)

    val recs = (1 to count) map { i =>
        new ProducerRecord(
          topicName,
          Random.alphanumeric.take(1).mkString,
          Random.alphanumeric.take(10).mkString
        )
      }

    //logger.info("writing record: " + rec.key() + ", " + rec.value())
    val sentAllRecords = Future.traverse(recs)(rec => producer.send(rec))

    val metadata: Seq[RecordMetadata] = Await.result(sentAllRecords, 10.seconds)
    logger.info(s"record metadata:")
    metadata.foreach(println)
    metadata
  }

}
