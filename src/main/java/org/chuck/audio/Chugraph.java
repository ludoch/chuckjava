package org.chuck.audio;

import org.chuck.core.ChuckType;

/**
 * Chugraph: Unit Generator graph wrapper.
 * Allows grouping multiple UGens into a single component with an inlet and outlet.
 */
public class Chugraph extends ChuckUGen {
    public final Gain inlet = new Gain();
    public final Gain outlet = new Gain();

    public Chugraph() {
        super(new ChuckType("Chugraph", ChuckType.OBJECT, 0, 0));
        // Ensure inlet and outlet are registered with the VM if needed, 
        // but here they are internal components.
    }

    @Override
    protected float compute(float input, long systemTime) {
        inlet.tick(input, systemTime);
        return outlet.tick(systemTime);
    }
}
