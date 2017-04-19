package com.abc.lambda.speed.storm.spring.topology.component.bolt;

import org.apache.storm.topology.BoltDeclarer;
import org.apache.storm.topology.IBasicBolt;
import org.apache.storm.topology.TopologyBuilder;

/**
 * [Class Description]
 *
 * @author Grant Henke
 * @since 12/3/12
 */
public class BasicBolt extends GenericBolt {

    private IBasicBolt stormBolt;

    public BasicBolt(final String componentId, final IBasicBolt stormBolt) {
        super(componentId);
        this.stormBolt = stormBolt;
    }

    public IBasicBolt getStormBolt() {
        return stormBolt;
    }

    public void addToTopology(final TopologyBuilder builder) {
        BoltDeclarer boltDeclarer;
        if (parallelismHint == null) {
            boltDeclarer = builder.setBolt(componentId, stormBolt);
        } else {
            boltDeclarer = builder.setBolt(componentId, stormBolt, parallelismHint);
        }
        addBoltGroupingsToBolt(boltDeclarer);
        addConfigToComponent(boltDeclarer);
    }
}
