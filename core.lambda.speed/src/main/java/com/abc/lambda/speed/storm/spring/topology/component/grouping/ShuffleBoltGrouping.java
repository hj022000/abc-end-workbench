package com.abc.lambda.speed.storm.spring.topology.component.grouping;

import org.apache.storm.topology.BoltDeclarer;

/**
 * [Class Description]
 *
 * @author Grant Henke
 * @since 12/4/12
 */
public class ShuffleBoltGrouping extends GenericBoltGrouping implements IBoltGrouping {

    public ShuffleBoltGrouping(final String componentId, final String streamId) {
        super(componentId, streamId);
    }

    public ShuffleBoltGrouping(final String componentId) {
        super(componentId);
    }

    public void addToBolt(final BoltDeclarer boltDeclarer) {
        if (streamId == null) {
            boltDeclarer.shuffleGrouping(componentId);
        } else {
            boltDeclarer.shuffleGrouping(componentId, streamId);
        }
    }
}
