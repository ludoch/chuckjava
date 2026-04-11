package org.chuck.core;

/** Instruction to set a global integer variable. */
public class SetGlobalInt implements ChuckInstr {
  private final String name;

  public SetGlobalInt(String name) {
    this.name = name;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    long val = shred.reg.popLong();
    vm.setGlobalInt(name, val);
  }
}
