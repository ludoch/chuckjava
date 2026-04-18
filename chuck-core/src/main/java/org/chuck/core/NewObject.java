package org.chuck.core;

/** Instantiates a new object of the given type and pushes it onto the stack. */
public class NewObject implements ChuckInstr {
  private final ChuckType type;

  public NewObject(ChuckType type) {
    this.type = type;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    ChuckObject obj = new ChuckObject(type);
    shred.reg.pushObject(obj);
  }
}
