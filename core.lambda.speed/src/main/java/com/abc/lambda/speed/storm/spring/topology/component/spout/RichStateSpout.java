package com.abc.lambda.speed.storm.spring.topology.component.spout;

import org.apache.storm.topology.IRichStateSpout;
import org.apache.storm.topology.TopologyBuilder;

/**
 * [Class Description]
 *
 * @author Grant Henke
 * @since 12/6/12
 */
public class RichStateSpout extends GenericSpout {

    private final IRichStateSpout stormSpout;

    public RichStateSpout(final String componentId, final IRichStateSpout stormSpout) {
        super(componentId);
        this.stormSpout = stormSpout;
    }

    public IRichStateSpout getStormSpout() {
        return stormSpout;
    }

    public void addToTopology(final TopologyBuilder builder) {
        if (parallelismHint == null) {
            builder.setStateSpout(componentId, stormSpout);
        } else {
            builder.setStateSpout(componentId, stormSpout, parallelismHint);
        }
    }
}
