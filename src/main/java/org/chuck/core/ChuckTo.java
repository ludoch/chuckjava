package org.chuck.core;

import org.chuck.audio.ChuckUGen;

/** Instruction to connect two UGens (lhs => rhs). */
public class ChuckTo implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    if (shred.reg.getSp() < 2) {
      if (shred.reg.getSp() > 0) {
        // leave top on stack for chaining if possible
      }
      return;
    }
    Object rawRhs = shred.reg.popObject();
    Object rawLhs = shred.reg.popObject();

    String lhsN = rawLhs == null ? "null" : rawLhs.getClass().getSimpleName();
    String rhsN = rawRhs == null ? "null" : rawRhs.getClass().getSimpleName();
    boolean rhsOk = rawRhs instanceof ChuckUGen;
    boolean lhsOk = rawLhs instanceof ChuckUGen;
    System.err.println(
        "DEBUG ChuckTo lhs=" + lhsN + " rhs=" + rhsN + " lhsOk=" + lhsOk + " rhsOk=" + rhsOk);
    if (rawRhs instanceof ChuckUGen rhs && rawLhs instanceof ChuckUGen lhs) {
      lhs.chuckTo(rhs);
      System.err.println("  -> connected " + lhsN + " => " + rhsN);
    }

    // Leave rhs on stack for chaining (e.g. a => b => c)
    shred.reg.pushObject(rawRhs);
  }
}
