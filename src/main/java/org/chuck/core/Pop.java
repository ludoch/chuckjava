package org.chuck.core;

/** Pops the top value from the operand stack. */
public class Pop implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    // We don't know if it's an object or a primitive,
    // so we try both to be safe in this simple VM.
    if (shred.reg.getSp() > 0) {
      shred.reg.pop(1);
    }
  }
}
