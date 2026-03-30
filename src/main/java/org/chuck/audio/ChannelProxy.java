package org.chuck.audio;

import org.chuck.core.ChuckType;

/**
 * A proxy UGen representing a single channel of a MultiChannelUGen.
 * Ticking this proxy returns the sample from the parent's channel.
 */
public class ChannelProxy extends ChuckUGen {
    private final MultiChannelUGen parent;
    private final int channelIndex;

    public ChannelProxy(MultiChannelUGen parent, int channelIndex) {
        super(new ChuckType("ChannelProxy", ChuckType.OBJECT, 0, 0));
        this.parent = parent;
        this.channelIndex = channelIndex;
    }

    @Override
    public float tick(long systemTime) {
        // Ensure parent is ticked
        parent.tick(systemTime);
        lastOut = parent.getChannelLastOut(channelIndex);
        lastTickTime = systemTime;
        return lastOut;
    }

    @Override
    protected float compute(float input, long systemTime) {
        return parent.getChannelLastOut(channelIndex);
    }
}
