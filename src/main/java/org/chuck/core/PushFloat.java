package org.chuck.core;

/** Pushes a 64-bit float (double) constant onto the operand stack. */
public class PushFloat implements ChuckInstr {
  private final double value;

  public PushFloat(double value) {
    this.value = value;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    shred.reg.push(value);
  }
}
