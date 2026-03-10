package org.chuck.core;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Represents a concurrent execution context in ChucK.
 */
public class ChuckShred implements Comparable<ChuckShred> {
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);
    
    private final int id;
    private long wakeTime = 0;
    private boolean isDone = false;
    private boolean isRunning = false;
    private boolean isWaiting = false; // Waiting on an event
    
    // Virtual Machine stacks
    public final ChuckStack reg = new ChuckStack(65536);
    public final ChuckStack mem = new ChuckStack(65536);
    /** Stack of 'this' references for active user-defined method calls. */
    public final Deque<UserObject> thisStack = new ArrayDeque<>();
    
    // Execution state
    private ChuckCode code;
    private int pc = 0;
    
    public ChuckCode getCode() { return code; }
    public void setCode(ChuckCode code) { this.code = code; }
    public int getPc() { return pc; }
    public void setPc(int pc) { this.pc = pc; }

    public long getResult() {
        if (reg.getSp() > 0) return reg.popLong();
        return 0;
    }
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    
    public ChuckShred(ChuckCode code) {
        this.id = ID_GENERATOR.getAndIncrement();
        this.code = code;
    }
    
    public int getId() { return id; }
    public long getWakeTime() { return wakeTime; }
    public void setWakeTime(long time) { this.wakeTime = time; }
    public boolean isDone() { return isDone; }
    public boolean isWaiting() { return isWaiting; }

    public void abort() {
        this.isDone = true;
        this.isRunning = false;
        // Signal to wake up from any await/yield and immediately hit the loop exit
        lock.lock();
        try {
            condition.signal();
        } finally {
            lock.unlock();
        }
    }
    
    // Called by VM to resume this shred
    void resume(ChuckVM vm) {
        lock.lock();
        try {
            isRunning = true;
            isWaiting = false;
            condition.signal();
            while (isRunning && !isDone) {
                condition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    // Called by instructions to advance time (yield)
    public void yield(long samples) {
        lock.lock();
        try {
            this.wakeTime += samples;
            isRunning = false;
            condition.signal();
            while (!isRunning && !isDone) {   // exit if abort() was called
                condition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }

    // Called when shred chucks an Event to now
    public void suspendOnEvent() {
        lock.lock();
        try {
            isRunning = false;
            isWaiting = true;
            condition.signal(); // Tell VM we are parked
            while (!isRunning && !isDone) {   // exit if abort() was called
                condition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    // The main interpreter loop for the Virtual Thread
    public void execute(ChuckVM vm) {
        lock.lock();
        try {
            while (!isRunning) {
                condition.await();
            }
            
            // Interpreter Loop
            while (!isDone && pc < code.getNumInstructions()) {
                ChuckInstr instr = code.getInstruction(pc);
                if (instr == null) break;
                
                // Execute instruction
                instr.execute(vm, this);
                
                // Advance PC
                pc++;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            isDone = true;
            isRunning = false;
            condition.signal();
            lock.unlock();
        }
    }
    
    @Override
    public int compareTo(ChuckShred other) {
        return Long.compare(this.wakeTime, other.wakeTime);
    }
}
