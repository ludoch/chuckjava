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
public class ChuckShred extends ChuckObject implements Comparable<ChuckShred> {
    /** The ScopedValue holding the current shred for this thread. */
    public static final ScopedValue<ChuckShred> CURRENT_SHRED = ScopedValue.newInstance();
    
    private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);
    
    private final int id;
    private long wakeTime = 0;
    public volatile boolean isDone = false;
    private boolean isRunning = false;
    private boolean isWaiting = false; // Waiting on an event
    private String name = "";
    private String[] args = new String[0];
    
    // Virtual Machine stacks
    public final ChuckStack reg = new ChuckStack(1024 * 1024);
    public final ChuckStack mem = new ChuckStack(1024 * 1024);
    /** Stack of 'this' references for active user-defined method calls. */
    public final Deque<UserObject> thisStack = new ArrayDeque<>();
    /** UGens created by this shred, so they can be disconnected on stop. */
    private final Set<org.chuck.audio.ChuckUGen> ownedUGens = ConcurrentHashMap.newKeySet();
    public void registerUGen(org.chuck.audio.ChuckUGen ugen) { ownedUGens.add(ugen); }
    public Set<org.chuck.audio.ChuckUGen> getOwnedUGens() { return ownedUGens; }
    /** Closeable resources (e.g. OscIn sockets) to close when shred exits. */
    private final java.util.List<AutoCloseable> ownedCloseables = new java.util.ArrayList<>();
    public void registerCloseable(AutoCloseable c) { ownedCloseables.add(c); }
    
    // Execution state
    private ChuckCode code;
    private int pc = 0;
    private int framePointer = 0; // Index in mem stack where current frame starts
    
    public ChuckCode getCode() { return code; }
    public void setCode(ChuckCode code) { this.code = code; }
    public int getPc() { return pc; }
    public void setPc(int pc) { this.pc = pc; }
    public int getFramePointer() { return framePointer; }
    public void setFramePointer(int fp) { this.framePointer = fp; }

    public long getResult() {
        if (reg.getSp() > 0) return reg.popLong();
        return 0;
    }
    
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition condition = lock.newCondition();
    
    /** The shred that sporked this one, or null for the top-level shred. */
    private ChuckShred parentShred = null;

    public ChuckShred(ChuckCode code) {
        super(new ChuckType("Shred", ChuckType.OBJECT, 0, 0));
        this.id = ID_GENERATOR.getAndIncrement();
        this.code = code;
    }

    public void setParentShred(ChuckShred parent) { this.parentShred = parent; }

    /** Returns the parent shred (the shred that sporked this one), or null if top-level. */
    public ChuckShred parent() { return parentShred; }

    /** Returns the top-level ancestor shred. For top-level shreds, returns this. */
    public ChuckShred ancestor() {
        ChuckShred cur = this;
        while (cur.parentShred != null) {
            cur = cur.parentShred;
        }
        return cur;
    }
    
    private ChuckEvent eventWaitingOn = null;
    private final Set<ChuckEvent> conjunctionTriggered = new java.util.HashSet<>();

    public void setEventWaitingOn(ChuckEvent e) {
        this.eventWaitingOn = e;
        this.conjunctionTriggered.clear();
    }

    /**
     * Called by an event when it signals this shred.
     * @return true if the shred should be woken up and removed from the event's waiting list.
     */
    public boolean notifyTriggered(ChuckEvent e, ChuckVM vm) {
        if (eventWaitingOn == null) return true;
        if (eventWaitingOn instanceof ChuckEventDisjunction) return true;
        if (eventWaitingOn instanceof ChuckEventConjunction conj) {
            conjunctionTriggered.add(e);
            for (ChuckEvent ce : conj.getEvents()) {
                if (!conjunctionTriggered.contains(ce)) return false;
            }
            return true;
        }
        return true;
    }

    public int getId() { return id; }
    public static void resetIdCounter() { ID_GENERATOR.set(1); }
    public long getWakeTime() { return wakeTime; }
    public void setWakeTime(long time) { this.wakeTime = time; }
    public boolean isDone() { return isDone; }
    /** ChucK-style: s.done() */
    public int done() { return isDone ? 1 : 0; }
    public boolean isWaiting() { return isWaiting; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String[] getArgs() { return args; }
    public void setArgs(String[] args) { this.args = args; }

    // ChucK 'me' methods
    public int id() { return id; }
    public void exit() { abort(); }
    public String arg(int i) { return (i >= 0 && i < args.length) ? args[i] : ""; }
    public int args() { return args.length; }
    public int numArgs() { return args.length; }

    public String dir() {
        return dir(0);
    }
    
    public String path() { return (code != null && code.getName() != null) ? code.getName() : ""; }
    public String source() { return path(); }
    public String sourcePath() { return path(); }
    public int running() { return isRunning ? 1 : 0; }

    public String dir(int n) {
        if (code == null || code.getName() == null) return "./";
        java.io.File f = new java.io.File(code.getName());
        java.io.File parent = f.getParentFile();
        for (int i = 0; i < n && parent != null; i++) {
            parent = parent.getParentFile();
        }
        if (parent != null) {
            String p = parent.getAbsolutePath().replace('\\', '/');
            if (!p.endsWith("/")) p += "/";
            return p;
        }
        return "./";
    }

    public String sourceDir() { return dir(); }

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
            this.instructionCount = 0;
            this.wakeTime += samples;
            this.isRunning = false;
            condition.signal(); // Signal execute() loop that we are done for now
            while (!isRunning && !isDone) {
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
            this.instructionCount = 0;
            this.isRunning = false;
            this.isWaiting = true;
            condition.signal(); // Signal execute() loop that we are parking
            while (!isRunning && !isDone) {
                condition.await();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            lock.unlock();
        }
    }
    
    // The main interpreter loop for the Virtual Thread
    private long instructionCount = 0;
    private static final long MAX_INSTRUCTIONS_BEFORE_YIELD = 1000000;

    public void execute(ChuckVM vm) {
        lock.lock();
        try {
            while (!isDone && code != null && pc < code.getNumInstructions()) {
                while (!isRunning && !isDone) {
                    condition.await();
                }
                if (isDone) break;
                
                // Interpreter Loop
                while (!isDone && isRunning && code != null && pc < code.getNumInstructions()) {
                    ChuckInstr instr = code.getInstruction(pc);
                    if (instr == null) break;

                    // Execute instruction
                    try {
                        System.out.println("EXE [" + pc + "] " + instr.getClass().getSimpleName() + " stack=" + reg.getSp());
                        instr.execute(vm, this);
                    } catch (RuntimeException e) {
                        e.printStackTrace(); // Print to stderr for debugging
                        int line = code.getLineNumber(pc);
                        String rawMsg = e.getMessage();
                        if (rawMsg == null) rawMsg = e.getClass().getSimpleName();
                        String type = rawMsg.contains(":") ? rawMsg.split(":")[0] : rawMsg;
                        String msg = String.format("[chuck]:(EXCEPTION) %s: on line[%d] in shred[id=%d:%s]",
                                type, line, id, code.getName());
                        if (rawMsg.contains("index[")) {
                            msg += " " + rawMsg.substring(rawMsg.indexOf("index["));
                        }
                        vm.print(msg + "\n");
                        isDone = true;
                        isRunning = false;
                        return;
                    }

                    // Advance PC
                    pc++;

                    // Safety check: prevent infinite tight loops from hanging the VM
                    if (++instructionCount > MAX_INSTRUCTIONS_BEFORE_YIELD) {
                        instructionCount = 0;
                        this.yield(0); // Force yield to allow other shreds/audio to run
                        break; 
                    }
                }
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

    /**
     * Executes the given code synchronously in the current thread.
     * Used by ChuGen.tick() which runs in the audio thread.
     */
    public void executeSynchronous(ChuckVM vm, ChuckCode tickCode) {
        if (tickCode == null) return;
        ChuckCode oldCode = this.code;
        int oldPc = this.pc;
        int oldFp = this.framePointer;
        int oldMemSp = this.mem.getSp();
        
        // Push a fake return frame to the mem stack so ReturnMethod works
        mem.pushObject(null); // savedCode
        mem.push(-1L);        // savedPc
        mem.push((long)oldFp); // savedFp
        mem.push((long)reg.getSp()); // savedSp
        
        this.framePointer = mem.getSp();
        this.code = tickCode;
        this.pc = 0;
        
        while (code != null && pc >= 0 && pc < code.getNumInstructions()) {
            ChuckInstr instr = code.getInstruction(pc);
            if (instr == null) break;
            instr.execute(vm, this);
            pc++;
        }
        
        this.code = oldCode;
        this.pc = oldPc;
        this.framePointer = oldFp;
        this.mem.setSp(oldMemSp);
    }
    
    public void cleanup() {
        // Acquire the lock so resume() sees the updated state and wakes up.
        // Without this, Java DSL shreds (spork(Runnable)) would leave resume()
        // deadlocked on condition.await() after the shred finishes.
        lock.lock();
        try {
            isRunning = false;
            isDone = true;
            condition.signalAll();
        } finally {
            lock.unlock();
        }
        // Disconnect all UGens created by this shred
        for (org.chuck.audio.ChuckUGen ugen : ownedUGens) {
            ugen.unchuckAll();
        }
        ownedUGens.clear();
        // Close resources (OSC sockets, etc)
        for (AutoCloseable c : ownedCloseables) {
            try { c.close(); } catch (Exception ignored) {}
        }
        ownedCloseables.clear();
    }

    @Override
    public int compareTo(ChuckShred other) {
        return Long.compare(this.wakeTime, other.wakeTime);
    }
}
