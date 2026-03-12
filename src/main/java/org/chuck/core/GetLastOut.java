package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Gets the last computed sample value from a UGen.
 */
public class GetLastOut implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        if (shred.reg.getSp() == 0) { shred.reg.push(0L); return; }
        Object raw = shred.reg.popObject();
        if (!(raw instanceof ChuckUGen ugen)) { shred.reg.push(0L); return; }
        shred.reg.push(Double.doubleToRawLongBits((double) ugen.getLastOut()));
    }
}
