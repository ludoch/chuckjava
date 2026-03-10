package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Gets the last computed sample value from a UGen.
 */
public class GetLastOut implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        ChuckUGen ugen = (ChuckUGen) shred.reg.popObject();
        if (ugen == null) throw new RuntimeException("GetLastOut: target is null");
        shred.reg.push(Double.doubleToRawLongBits((double) ugen.getLastOut()));
    }
}
