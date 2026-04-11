package org.chuck.core;

/** Instruction to get a global object variable. */
public class GetGlobalObject implements ChuckInstr {
  private final String name;

  public GetGlobalObject(String name) {
    this.name = name;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    shred.reg.pushObject(vm.getGlobalObject(name));
  }
}
