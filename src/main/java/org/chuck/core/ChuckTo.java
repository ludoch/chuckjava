package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/**
 * Instruction to connect two UGens (lhs => rhs).
 */
public class ChuckTo implements ChuckInstr {
    @Override
    public void execute(ChuckVM vm, ChuckShred shred) {
        ChuckUGen rhs = (ChuckUGen) shred.reg.popObject();
        ChuckUGen lhs = (ChuckUGen) shred.reg.popObject();
        lhs.chuckTo(rhs);
        // Leave rhs on stack for chaining (e.g. a => b => c)
        shred.reg.pushObject(rhs);
    }
}
