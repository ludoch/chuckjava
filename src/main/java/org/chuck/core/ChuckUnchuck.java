package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Instruction to disconnect two UGens (lhs !=> rhs).
 * Implements the unchuck operator.
 */
public class ChuckUnchuck implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        ChuckUGen rhs = (ChuckUGen) shred.reg.popObject();
        ChuckUGen lhs = (ChuckUGen) shred.reg.popObject();
        lhs.unchuck(rhs);
        // Leave rhs on stack for chaining (e.g. a !=> b !=> c)
        shred.reg.pushObject(rhs);
    }
}
