package org.chuck.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Represents an Event in ChucK that shreds can wait on.
 * Uses a ReentrantLock to avoid deadlocks during shred suspension.
 */
public class ChuckEvent extends ChuckObject {
    private final List<ChuckShred> waitingShreds = new ArrayList<>();
    private final ReentrantLock eventLock = new ReentrantLock();
    
    public ChuckEvent() {
        super(ChuckType.EVENT);
    }

    /**
     * Called when a shred waits on this event.
     */
    public void waitOn(ChuckShred shred, ChuckVM vm) {
        eventLock.lock();
        try {
            waitingShreds.add(shred);
        } finally {
            eventLock.unlock();
        }
        // Suspend the shred AFTER releasing the event lock
        shred.suspendOnEvent();
    }

    public void signal(ChuckVM vm) {
        eventLock.lock();
        try {
            if (!waitingShreds.isEmpty()) {
                ChuckShred shred = waitingShreds.remove(0);
                shred.setWakeTime(vm.getCurrentTime());
                vm.schedule(shred);
                shred.resume(vm);
            }
        } finally {
            eventLock.unlock();
        }
    }

    public int getWaitingCount() { return waitingShreds.size(); }

    public void broadcast(ChuckVM vm) {
        eventLock.lock();
        try {
            for (ChuckShred shred : waitingShreds) {
                shred.setWakeTime(vm.getCurrentTime());
                vm.schedule(shred);
                shred.resume(vm);
            }
            waitingShreds.clear();
        } finally {
            eventLock.unlock();
        }
    }
}
