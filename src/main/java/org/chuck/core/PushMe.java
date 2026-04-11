package org.chuck.core;

/** Instruction to push the current shred (me) onto the stack. */
public class PushMe implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred s) {
    s.reg.pushObject(s);
  }
}
