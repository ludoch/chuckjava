package org.chuck.core;

/** Pushes a specific DAC channel summing node onto the stack. */
public class PushDacChan implements ChuckInstr {
  private final int channel;

  public PushDacChan(int channel) {
    this.channel = channel;
  }

  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    shred.reg.pushObject(vm.getDacChannel(channel));
  }
}
