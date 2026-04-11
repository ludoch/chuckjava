package org.chuck.core;

/** Pushes a 64-bit integer constant onto the operand stack. */
public class PushInt implements ChuckInstr {
  private final long value;

  public PushInt(long value) {
    this.value = value;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    shred.reg.push(value);
  }
}
