package org.chuck.core;

/** Implements e1 || e2 (wait until ANY triggers). */
public class ChuckEventDisjunction extends ChuckEventCompound {
  @Override
  public void waitOn(ChuckShred shred, ChuckVM vm) {
    shred.setEventWaitingOn(this);
    for (ChuckEvent e : events) {
      e.waitOnNoSuspend(shred, vm);
    }
    shred.suspendOnEvent();
    // Cleanup: remove from all if any one woke us
    shred.setEventWaitingOn(null);
    for (ChuckEvent e : events) {
      e.removeWaitingShred(shred);
    }
  }
}
