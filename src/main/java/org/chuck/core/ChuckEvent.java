package org.chuck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents an Event in ChucK that shreds can wait on. Uses a ReentrantLock to avoid deadlocks
 * during shred suspension.
 */
public class ChuckEvent extends ChuckObject {
  private final List<ChuckShred> waitingShreds = new ArrayList<>();
  private final ReentrantLock eventLock = new ReentrantLock();
  private long timeoutSamples = -1;

  public ChuckEvent() {
    super(ChuckType.EVENT);
  }

  /** Set a timeout for the NEXT wait on this event. */
  public void timeout(ChuckDuration duration) {
    this.timeoutSamples = Math.round(duration.samples());
  }

  /** Called when a shred waits on this event. */
  public void waitOn(ChuckShred shred, ChuckVM vm) {
    long t = this.timeoutSamples;
    this.timeoutSamples = -1; // Reset after one wait
    waitOn(shred, vm, t);
  }

  /**
   * Called when a shred waits on this event with a timeout (in samples). timeoutSamples <= 0 means
   * no timeout (infinite wait).
   */
  public void waitOn(ChuckShred shred, ChuckVM vm, long timeoutSamples) {
    eventLock.lock();
    try {
      waitingShreds.add(shred);
    } finally {
      eventLock.unlock();
    }

    shred.setEventWaitingOn(this);

    if (timeoutSamples > 0) {
      // Schedule a timeout task in the VM
      long wakeTime = vm.getCurrentTime() + timeoutSamples;
      vm.scheduleTimeout(shred, this, wakeTime);
    }

    // Suspend the shred AFTER releasing the event lock and scheduling timeout
    shred.suspendOnEvent();
  }

  public void removeWaitingShred(ChuckShred shred) {
    eventLock.lock();
    try {
      waitingShreds.remove(shred);
    } finally {
      eventLock.unlock();
    }
  }

  /** Internal version of waitOn that doesn't suspend the shred. Used by compound events. */
  public void waitOnNoSuspend(ChuckShred shred, ChuckVM vm) {
    eventLock.lock();
    try {
      waitingShreds.add(shred);
    } finally {
      eventLock.unlock();
    }
  }

  public void signal(ChuckVM vm) {
    ChuckShred toWake = null;
    eventLock.lock();
    try {
      while (!waitingShreds.isEmpty()) {
        ChuckShred shred = waitingShreds.get(0);
        if (shred.isWaiting()) {
          if (shred.notifyTriggered(this, vm)) {
            toWake = waitingShreds.remove(0);
            break;
          } else {
            // Conjunction: triggered THIS event, but still waiting for others.
            // We remove it from this event's list.
            waitingShreds.remove(0);
            break;
          }
        } else {
          waitingShreds.remove(0);
        }
      }
    } finally {
      eventLock.unlock();
    }

    if (toWake != null) {
      toWake.setWakeTime(vm.getCurrentTime());
      toWake.resume(vm);
      if (!toWake.isDone() && !toWake.isWaiting()) {
        vm.schedule(toWake);
      }
    }
  }

  public int getWaitingCount() {
    return waitingShreds.size();
  }

  /** Returns true — all ChucK events can be waited on. */
  public boolean can_wait() {
    return true;
  }

  public void broadcast(ChuckVM vm) {
    List<ChuckShred> toWake = new ArrayList<>();
    eventLock.lock();
    try {
      java.util.Iterator<ChuckShred> it = waitingShreds.iterator();
      while (it.hasNext()) {
        ChuckShred shred = it.next();
        if (shred.isWaiting()) {
          if (shred.notifyTriggered(this, vm)) {
            toWake.add(shred);
            it.remove();
          } else {
            // Conjunction
            it.remove();
          }
        } else {
          it.remove();
        }
      }
    } finally {
      eventLock.unlock();
    }

    for (ChuckShred s : toWake) {
      s.setWakeTime(vm.getCurrentTime());
      s.resume(vm);
      if (!s.isDone() && !s.isWaiting()) {
        vm.schedule(s);
      }
    }
  }
}
