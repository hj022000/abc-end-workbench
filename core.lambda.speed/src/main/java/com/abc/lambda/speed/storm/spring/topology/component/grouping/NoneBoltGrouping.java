package com.abc.lambda.speed.storm.spring.topology.component.grouping;

import org.apache.storm.topology.BoltDeclarer;

/**
 * [Class Description]
 *
 * @author Grant Henke
 * @since 12/4/12
 */
public class NoneBoltGrouping extends GenericBoltGrouping implements IBoltGrouping {

    public NoneBoltGrouping(final String componentId, String streamId) {
        super(componentId, streamId);
    }

    public NoneBoltGrouping(final String componentId) {
        super(componentId);
    }

    public void addToBolt(final BoltDeclarer boltDeclarer) {
        if (streamId == null) {
            boltDeclarer.noneGrouping(componentId);
        } else {
            boltDeclarer.noneGrouping(componentId, streamId);
        }
    }
}
