package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Instruction to disconnect two UGens (lhs !=> rhs).
 * Implements the unchuck operator.
 */
public class ChuckUnchuck implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        Object rawRhs = shred.reg.popObject();
        Object rawLhs = shred.reg.popObject();
        if (rawLhs instanceof ChuckUGen lhs && rawRhs instanceof ChuckUGen rhs) {
            lhs.unchuck(rhs);
            shred.reg.pushObject(rhs);
        } else {
            shred.reg.push(0L); // keep stack balanced
        }
    }
}
