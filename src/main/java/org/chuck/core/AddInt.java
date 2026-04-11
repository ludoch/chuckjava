package org.chuck.core;

/** Adds two integers from the operand stack and pushes the result. */
public class AddInt implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    long rhs = shred.reg.popLong();
    long lhs = shred.reg.popLong();
    shred.reg.push(lhs + rhs);
  }
}
