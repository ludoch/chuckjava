package org.chuck.core;

/** Pushes the current VM logical time (in samples) onto the operand stack. */
public class PushNow implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    shred.reg.push(vm.getCurrentTime());
  }
}
