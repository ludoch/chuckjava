package org.chuck.core;

/** Represents the Machine object in ChucK. */
public class MachineObject extends ChuckObject {
  public MachineObject() {
    super(new ChuckType("Machine", ChuckType.OBJECT, 0, 0));
  }
}
