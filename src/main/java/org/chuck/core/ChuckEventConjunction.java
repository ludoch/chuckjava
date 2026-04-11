package org.chuck.core;

/** Implements e1 && e2 (wait until ALL trigger). */
public class ChuckEventConjunction extends ChuckEventCompound {
  @Override
  public void waitOn(ChuckShred shred, ChuckVM vm) {
    shred.setEventWaitingOn(this);
    for (ChuckEvent e : events) {
      e.waitOnNoSuspend(shred, vm);
    }
    shred.suspendOnEvent();
    // Cleanup
    shred.setEventWaitingOn(null);
    for (ChuckEvent e : events) {
      e.removeWaitingShred(shred);
    }
  }
}
