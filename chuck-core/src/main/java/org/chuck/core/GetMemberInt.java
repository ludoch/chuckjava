package org.chuck.core;

/** Gets an integer member value from an object. */
public class GetMemberInt implements ChuckInstr {
  private final int offset;

  public GetMemberInt(int offset) {
    this.offset = offset;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    // Stack: [Object] -> Top is Object
    ChuckObject obj = (ChuckObject) shred.reg.popObject();
    if (obj == null) {
      throw new RuntimeException("GetMemberInt: target object is null");
    }
    shred.reg.push(obj.getData(offset));
  }
}
