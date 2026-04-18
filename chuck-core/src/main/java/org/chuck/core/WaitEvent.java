package org.chuck.core;

/** Instruction to wait on a ChucK Event (e.g., myEvent => now). */
public class WaitEvent implements ChuckInstr {
  @Override
  public void execute(ChuckVM vm, ChuckShred shred) {
    // The event object should be on the stack
    Object raw = shred.reg.popObject();
    if (raw instanceof ChuckEvent event) {
      event.waitOn(shred, vm);
    } else if (raw instanceof UserObject uo && uo.eventDelegate != null) {
      uo.eventDelegate.waitOn(shred, vm);
    } else {
      throw new org.chuck.core.ChuckRuntimeException("Chucked non-event object to now");
    }
  }
}
