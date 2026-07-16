package org.chuck.core;

import java.util.*;
import java.util.concurrent.locks.ReentrantLock;

/** A ChucK Event for synchronization between shreds. */
public class ChuckEvent extends UserObject {
  protected final ReentrantLock eventLock = new ReentrantLock();
  protected final List<ChuckShred> waitingShreds = new ArrayList<>();
  private final java.util.concurrent.CopyOnWriteArrayList<java.util.function.Consumer<ChuckEvent>>
      listeners = new java.util.concurrent.CopyOnWriteArrayList<>();

  public void addListener(java.util.function.Consumer<ChuckEvent> listener) {
    listeners.add(listener);
  }

  public void removeListener(java.util.function.Consumer<ChuckEvent> listener) {
    listeners.remove(listener);
  }

  protected void notifyListeners() {
    for (java.util.function.Consumer<ChuckEvent> l : listeners) {
      try {
        l.accept(this);
      } catch (Exception ignored) {
      }
    }
  }

  public ChuckEvent() {
    super("Event", null, null, false);
  }

  public void waitOn(ChuckShred shred, ChuckVM vm) {
    eventLock.lock();
    try {
      waitingShreds.add(shred);
      shred.setEventWaitingOn(this);
    } finally {
      eventLock.unlock();
    }
    shred.suspendOnEvent();
  }

  public void waitOnNoSuspend(ChuckShred shred, ChuckVM vm) {
    eventLock.lock();
    try {
      waitingShreds.add(shred);
      shred.setEventWaitingOn(this);
    } finally {
      eventLock.unlock();
    }
  }

  public void removeWaitingShred(ChuckShred shred) {
    eventLock.lock();
    try {
      waitingShreds.remove(shred);
    } finally {
      eventLock.unlock();
    }
  }

  public void timeout(ChuckDuration dur) {
    ChuckVM vm = ChuckVM.CURRENT_VM.get();
    ChuckShred s = ChuckShred.CURRENT_SHRED.get();
    vm.scheduleTimeout(s, this, (long) (vm.getCurrentTime() + dur.samples()));
  }

  public void signal() {
    ChuckVM vm = ChuckVM.CURRENT_VM.isBound() ? ChuckVM.CURRENT_VM.get() : null;
    if (vm != null) signal(vm);
  }

  public void signal(ChuckVM vm) {
    notifyListeners();
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
            waitingShreds.remove(0);
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
      vm.schedule(toWake);
    }
  }

  public void broadcast() {
    ChuckVM vm = ChuckVM.CURRENT_VM.isBound() ? ChuckVM.CURRENT_VM.get() : null;
    if (vm != null) broadcast(vm);
  }

  public void broadcast(ChuckVM vm) {
    notifyListeners();
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
      vm.schedule(s);
    }
  }

  public int getWaitingCount() {
    return waitingShreds.size();
  }

  public boolean can_wait() {
    return true;
  }
}
