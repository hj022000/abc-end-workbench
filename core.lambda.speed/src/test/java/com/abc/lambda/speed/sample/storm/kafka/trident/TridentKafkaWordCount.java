/*
 * Licensed to the Apache Software Foundation (ASF) under one
 *   or more contributor license agreements.  See the NOTICE file
 *   distributed with this work for additional information
 *   regarding copyright ownership.  The ASF licenses this file
 *   to you under the Apache License, Version 2.0 (the
 *   "License"); you may not use this file except in compliance
 *   with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.abc.lambda.speed.sample.storm.kafka.trident;


import org.apache.storm.Config;
import org.apache.storm.StormSubmitter;
import org.apache.storm.kafka.StringScheme;
import org.apache.storm.kafka.ZkHosts;
import org.apache.storm.kafka.bolt.KafkaBolt;
import org.apache.storm.kafka.trident.TransactionalTridentKafkaSpout;
import org.apache.storm.kafka.trident.TridentKafkaConfig;
import org.apache.storm.spout.SchemeAsMultiScheme;
import org.apache.storm.trident.testing.MemoryMapState;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

public class TridentKafkaWordCount implements Serializable {

    public static void main(String[] args) throws Exception {
        final String[] zkBrokerUrl = parseUrl(args);
        final String topicName = "test";
        Config tpConf = LocalSubmitter.defaultConfig();

        if (args.length == 3)  { //Submit Remote
            // Producer
            StormSubmitter.submitTopology(args[2] + "-producer", tpConf, KafkaProducerTopology.newTopology(zkBrokerUrl[1], topicName));
            // Consumer
            StormSubmitter.submitTopology(args[2] + "-consumer", tpConf, TridentKafkaConsumerTopology.newTopology(
                    new TransactionalTridentKafkaSpout(newTridentKafkaConfig(zkBrokerUrl[0]))));

            // Print results to console, which also causes the print filter in the consumer topology to print the results in the worker log
            Thread.sleep(2000);
            DrpcResultsPrinter.remoteClient().printResults(60, 1, TimeUnit.SECONDS);
        } else { //Submit Local
            final LocalSubmitter localSubmitter = LocalSubmitter.newInstance();
            final String prodTpName = "kafkaBolt";
            final String consTpName = "wordCounter";

            try {
                // Producer
                localSubmitter.submit(prodTpName, tpConf, KafkaProducerTopology.newTopology(zkBrokerUrl[1], topicName));
                // Consumer
                localSubmitter.submit(consTpName, tpConf, TridentKafkaConsumerTopology.newTopology(localSubmitter.getDrpc(),
                        new TransactionalTridentKafkaSpout(newTridentKafkaConfig(zkBrokerUrl[0]))));

                // print
                new DrpcResultsPrinter(localSubmitter.getDrpc()).printResults(60, 1, TimeUnit.SECONDS);
            } finally {
                // kill
                localSubmitter.kill(prodTpName);
                localSubmitter.kill(consTpName);
                // shutdown
                localSubmitter.shutdown();
            }
        }
    }

    private static String[] parseUrl(String[] args) {
        String zkUrl = "localhost:2181";        // the defaults.
        String brokerUrl = "localhost:9092";

        if (args.length > 3 || (args.length == 1 && args[0].matches("^-h|--help$"))) {
            System.out.println("Usage: TridentKafkaWordCount [kafka zookeeper url] [kafka broker url] [topology name]");
            System.out.println("   E.g TridentKafkaWordCount [" + zkUrl + "]" + " [" + brokerUrl + "] [wordcount]");
            System.exit(1);
        } else if (args.length == 1) {
            zkUrl = args[0];
        } else if (args.length == 2) {
            zkUrl = args[0];
            brokerUrl = args[1];
        }

        System.out.println("Using Kafka zookeeper uHrl: " + zkUrl + " broker url: " + brokerUrl);
        return new String[]{zkUrl, brokerUrl};
    }

    private static TridentKafkaConfig newTridentKafkaConfig(String zkUrl) {
        ZkHosts hosts = new ZkHosts(zkUrl);
        TridentKafkaConfig config = new TridentKafkaConfig(hosts, "test");
        config.scheme = new SchemeAsMultiScheme(new StringScheme());

        // Consume new data from the topic
//        config.startOffsetTime = kafka.api.OffsetRequest.LatestTime();
        return config;
    }
}
