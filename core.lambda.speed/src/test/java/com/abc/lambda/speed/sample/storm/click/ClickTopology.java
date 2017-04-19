package com.abc.lambda.speed.sample.storm.click;

import org.apache.storm.Config;
import org.apache.storm.LocalCluster;
import org.apache.storm.StormSubmitter;
import org.apache.storm.generated.AlreadyAliveException;
import org.apache.storm.generated.AuthorizationException;
import org.apache.storm.generated.InvalidTopologyException;
import org.apache.storm.topology.TopologyBuilder;
import org.apache.storm.tuple.*;
import org.apache.storm.tuple.Fields;
import org.apache.storm.utils.Utils;

public class ClickTopology {

    private TopologyBuilder builder = new TopologyBuilder();
    private Config conf = new Config();
    private LocalCluster cluster;

    public static final String DEFAULT_JEDIS_PORT = "6379";

    public ClickTopology(){
        builder.setSpout("clickSpout", new ClickSpout(), 10);

        //First layer of bolts
        builder.setBolt("repeatsBolt", new RepeatVisitBolt(), 10)
                .shuffleGrouping("clickSpout");
        builder.setBolt("geographyBolt", new GeographyBolt(new HttpIPResolver()), 10)
                .shuffleGrouping("clickSpout");

        //second layer of bolts, commutative in nature
        builder.setBolt("totalStats", new VisitStatsBolt(), 1).globalGrouping("repeatsBolt");
        builder.setBolt("geoStats", new GeoStatsBolt(), 10).fieldsGrouping("geographyBolt", new Fields(ClientFields.COUNTRY));
        conf.put(Conf.REDIS_PORT_KEY, DEFAULT_JEDIS_PORT);
    }

    public TopologyBuilder getBuilder() {
        return builder;
    }

    public LocalCluster getLocalCluster(){
        return cluster;
    }

    public Config getConf() {
        return conf;
    }

    public void runLocal(int runTime){
        conf.setDebug(true);
        conf.put(Conf.REDIS_HOST_KEY, "localhost");
        cluster = new LocalCluster();
        cluster.submitTopology("test", conf, builder.createTopology());
        if(runTime > 0){
            Utils.sleep(runTime);
            shutDownLocal();
        }
    }

    public void shutDownLocal(){
        if(cluster != null){
            cluster.killTopology("test");
            cluster.shutdown();
        }
    }

    public void runCluster(String name, String redisHost) throws AlreadyAliveException, InvalidTopologyException, AuthorizationException {
        conf.setNumWorkers(20);
        conf.put(Conf.REDIS_HOST_KEY, redisHost);
        StormSubmitter.submitTopology(name, conf, builder.createTopology());
    }



    public static void main(String[] args) throws Exception {

        ClickTopology topology = new ClickTopology();


        if(args!=null && args.length > 1) {
            topology.runCluster(args[0], args[1]);
        } else {
            if(args!=null && args.length == 1)
                System.out.println("Running in local mode, redis ip missing for cluster run");
            topology.runLocal(10000);
        }

    }

}
