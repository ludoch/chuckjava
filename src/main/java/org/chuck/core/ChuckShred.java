package org.chuck.core;

import java.lang.invoke.MethodHandle;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.instr.*;

/** Represents a ChucK Shred (unit of execution). */
public class ChuckShred implements Comparable<ChuckShred> {
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);

  /** The current shred for the current thread (ScopedValue). */
  public static final ScopedValue<ChuckShred> CURRENT_SHRED = ScopedValue.newInstance();

  private int id;
  private long wakeTime = 0;
  public volatile boolean isDone = false;
  public boolean isRunning = false;
  private boolean isWaiting = false; // Waiting on an event
  private String name = "";
  private String[] args = new String[0];
  private String lastExceptionMessage = null;

  // Virtual Machine stacks
  public final ChuckStack reg = new ChuckStack(1024 * 1024);
  public final ChuckStack mem = new ChuckStack(1024 * 1024);

  /** Stack of 'this' references for active user-defined method calls. */
  public final Deque<UserObject> thisStack = new ArrayDeque<>();

  /** UGens created by this shred, so they can be disconnected on stop. */
  private final Set<ChuckUGen> ownedUGens = ConcurrentHashMap.newKeySet();

  /** UserObjects created by this shred that have a @destruct method. */
  private final List<UserObject> destructibles = new ArrayList<>();

  /** Closeables to be closed when the shred is cleaned up. */
  private final List<AutoCloseable> closeables = new ArrayList<>();

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();
  private final ReentrantLock eventLock = new ReentrantLock();
  private final List<ChuckShred> waitingShreds = new ArrayList<>();

  private ChuckEvent eventWaitingOn = null;
  private ChuckShred parentShred = null;

  // Execution state
  private ChuckCode code;
  public int pc = 0;
  private int framePointer = 0; // Index in mem stack where current frame starts

  private final ChuckObjectPool.ShredAllocator allocator = ChuckObjectPool.acquireAllocator();

  public ChuckObjectPool.ShredAllocator getAllocator() {
    return allocator;
  }

  public ChuckShred(ChuckCode code) {
    this.id = ID_GENERATOR.getAndIncrement();
    this.code = code;
    if (code != null) this.name = code.getName();
  }

  public ChuckShred getParentShred() {
    return parentShred;
  }

  public void setParentShred(ChuckShred parent) {
    this.parentShred = parent;
  }

  // Identity and Arguments
  public int id() {
    return id;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String[] args() {
    return args;
  }

  public String[] getArgs() {
    return args;
  }

  public void setArgs(String[] args) {
    this.args = args;
  }

  public String arg(int i) {
    return (i >= 0 && i < args.length) ? args[i] : "";
  }

  // Source and Path information
  public String path() {
    return code != null ? code.getName() : "eval";
  }

  public String source() {
    return path();
  }

  public String sourcePath() {
    return path();
  }

  public String sourceDir() {
    return dir(0);
  }

  public String dir(int level) {
    String p = path();
    if (p == null || p.equals("eval")) return "";
    try {
      java.nio.file.Path path = java.nio.file.Paths.get(p).toAbsolutePath();
      java.nio.file.Path parent = path.getParent();
      if (parent == null) return "/";

      int absLevel = Math.abs(level);
      for (int i = 0; i < absLevel; i++) {
        java.nio.file.Path nextParent = parent.getParent();
        if (nextParent == null) break;
        parent = nextParent;
      }
      String dirStr = parent.toString();
      if (!dirStr.endsWith("/") && !dirStr.endsWith("\\")) {
        dirStr += "/";
      }
      return dirStr;
    } catch (Exception e) {
      return "";
    }
  }

  // Status accessors
  public long getWakeTime() {
    return wakeTime;
  }

  public void setWakeTime(long wakeTime) {
    this.wakeTime = wakeTime;
  }

  public boolean isDone() {
    return isDone;
  }

  public void setDone(boolean done) {
    isDone = done;
  }

  public boolean isRunning() {
    return isRunning;
  }

  public int running() {
    return isRunning ? 1 : 0;
  }

  public boolean isWaiting() {
    return isWaiting;
  }

  public void setWaiting(boolean waiting) {
    isWaiting = waiting;
  }

  public String getLastExceptionMessage() {
    return lastExceptionMessage;
  }

  // Registration for resources
  public void registerUGen(ChuckUGen ugen) {
    ownedUGens.add(ugen);
  }

  public void registerDestructible(UserObject uo) {
    if (uo.methods.containsKey("@destruct:0")) destructibles.add(uo);
  }

  public void registerCloseable(AutoCloseable c) {
    closeables.add(c);
  }

  public void cleanup() {
    for (ChuckUGen ugen : ownedUGens) {
      try {
        ugen.disconnectAll();
      } catch (Exception ignored) {
      }
    }
    ownedUGens.clear();
    for (AutoCloseable c : closeables) {
      try {
        c.close();
      } catch (Exception ignored) {
      }
    }
    closeables.clear();
    ChuckObjectPool.releaseAllocator(allocator);
    // Signal completion to anything waiting on this shred
    abort();
  }

  public void abort() {
    isDone = true;
    isRunning = false;
    lock.lock();
    try {
      condition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public void broadcast(ChuckVM vm) {
    List<ChuckShred> toWake = new ArrayList<>();
    eventLock.lock();
    try {
      toWake.addAll(waitingShreds);
      waitingShreds.clear();
    } finally {
      eventLock.unlock();
    }
    for (ChuckShred s : toWake) {
      s.setWakeTime(vm.getCurrentTime());
      s.resume(vm);
    }
  }

  /** Called by other shreds wishing to wait for this shred to finish. */
  public void waitOn(ChuckShred shred) {
    eventLock.lock();
    try {
      waitingShreds.add(shred);
    } finally {
      eventLock.unlock();
    }
  }

  // Execution state accessors
  public ChuckCode getCode() {
    return code;
  }

  public void setCode(ChuckCode code) {
    this.code = code;
  }

  public int getPc() {
    return pc;
  }

  public void setPc(int pc) {
    this.pc = pc;
  }

  public int getFramePointer() {
    return framePointer;
  }

  public void setFramePointer(int fp) {
    this.framePointer = fp;
  }

  // Event synchronization methods
  public void suspendOnEvent() {
    lock.lock();
    try {
      this.instructionCount = 0;
      this.isRunning = false;
      this.isWaiting = true;
      condition.signal();
      while (isWaiting && !isDone) condition.await();
    } catch (InterruptedException e) {
      isDone = true;
    } finally {
      lock.unlock();
    }
  }

  public boolean notifyTriggered(ChuckEvent e, ChuckVM vm) {
    if (eventWaitingOn == null || eventWaitingOn == e) {
      this.eventWaitingOn = null;
      return true;
    }
    return false;
  }

  public void setEventWaitingOn(ChuckEvent e) {
    this.eventWaitingOn = e;
  }

  public ChuckEvent getEventWaitingOn() {
    return eventWaitingOn;
  }

  // VM Control: Synchronous Resume/Yield Logic
  public void resume(ChuckVM vm) {
    lock.lock();
    try {
      isRunning = true;
      isWaiting = false;
      condition.signal();
      while (isRunning && !isDone) {
        condition.await();
      }
    } catch (InterruptedException e) {
      isDone = true;
      isRunning = false;
    } finally {
      lock.unlock();
    }
  }

  public void waitForResume() {
    lock.lock();
    try {
      while (!isRunning && !isDone) condition.await();
    } catch (InterruptedException e) {
      isDone = true;
    } finally {
      lock.unlock();
    }
  }

  public void yield(long samples) {
    lock.lock();
    try {
      this.instructionCount = 0;
      this.wakeTime += samples;
      this.isRunning = false;
      condition.signal();
    } finally {
      lock.unlock();
    }

    ChuckVM currentVm = ChuckVM.CURRENT_VM.isBound() ? ChuckVM.CURRENT_VM.get() : null;
    if (currentVm != null && !isDone) {
      currentVm.schedule(this);
    }

    lock.lock();
    try {
      while (!isRunning && !isDone) {
        condition.await();
      }
    } catch (InterruptedException e) {
      isDone = true;
      isRunning = false;
    } finally {
      lock.unlock();
    }
  }

  // Main Interpreter Loop with JIT support
  private long instructionCount = 0;
  private static final long MAX_INSTRUCTIONS_BEFORE_YIELD = 10000;

  public void execute(ChuckVM vm) {
    lock.lock();
    try {
      while (!isDone && code != null) {
        while (!isRunning && !isDone) {
          condition.await();
        }
        if (isDone) break;

        // JIT Trigger and Execution
        org.chuck.compiler.JitExecutable jit = code.getJitExecutable();
        if (jit != null) {
          try {
            int oldP = pc;
            ChuckCode oldC = code;
            jit.execute(vm, this);
            if (isDone || !isRunning) continue;
            if (code == oldC && pc == oldP) pc++;
            continue;
          } catch (Throwable t) {
            vm.print("[shred]: JIT error: " + t.getMessage() + "\n");
          }
        }

        if (code.incrementHotness() == 1000) {
          final ChuckCode targetCode = code;
          Thread.ofVirtual()
              .start(
                  () -> {
                    try {
                      Class<?> clz =
                          org.chuck.compiler.JitCompiler.compile(
                              targetCode, "Jit_" + targetCode.hashCode());
                      targetCode.setJitExecutable(
                          (org.chuck.compiler.JitExecutable)
                              clz.getDeclaredConstructor().newInstance());
                    } catch (Exception ignored) {
                    }
                  });
        }

        // Standard Interpreter Loop
        while (!isDone && isRunning && code != null && pc < code.getNumInstructions()) {
          MethodHandle handle = code.getHandle(pc);
          if (handle == null) break;

          if (++instructionCount > MAX_INSTRUCTIONS_BEFORE_YIELD) {
            instructionCount = 0;
            this.yield(0);
            break;
          }

          int oldPc = pc;
          ChuckCode oldCode = code;
          try {
            handle.invokeExact(vm, this);
          } catch (Throwable e) {
            int line = code.getLineNumber(pc);
            String rawMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            lastExceptionMessage =
                String.format("%s on line[%d] in %s", rawMsg, line, code.getName());
            vm.print("[chuck]:(EXCEPTION) " + lastExceptionMessage + "\n");
            isDone = true;
            isRunning = false;
            return;
          }
          if (code == oldCode && pc == oldPc) pc++;
        }

        if (isRunning && !isDone && code != null && pc >= code.getNumInstructions()) {
          if (framePointer >= 4) {
            new ControlInstrs.ReturnMethod().execute(vm, this);
          } else {
            isDone = true;
            isRunning = false;
          }
        }
      }
    } catch (InterruptedException e) {
      isDone = true;
      isRunning = false;
    } finally {
      isDone = true;
      isRunning = false;
      condition.signal();
      lock.unlock();
    }
  }

  /** Synchronous execution of a code block (e.g. for ChuGen.tick). */
  public void executeSynchronous(ChuckVM vm, ChuckCode code) {
    if (code == null) return;
    ChuckCode savedCode = this.code;
    int savedPc = this.pc;
    int savedFp = this.framePointer;

    this.code = code;
    this.pc = 0;
    this.framePointer = mem.getSp();

    try {
      while (this.code == code && pc < code.getNumInstructions() && !isDone) {
        MethodHandle handle = code.getHandle(pc);
        if (handle == null) break;
        int oldPc = pc;
        ChuckCode oldC = this.code;
        handle.invokeExact(vm, this);
        if (this.code == oldC && pc == oldPc) pc++;
      }
    } catch (Throwable t) {
      isDone = true;
    } finally {
      this.code = savedCode;
      this.pc = savedPc;
      this.framePointer = savedFp;
    }
  }

  public static void resetIdCounter() {
    ID_GENERATOR.set(1);
  }

  @Override
  public int compareTo(ChuckShred o) {
    return Long.compare(this.wakeTime, o.wakeTime);
  }

  // Pool helper methods
  public ChuckDuration getDuration(double samples) {
    return ChuckObjectPool.getDuration(samples);
  }

  public void releaseDuration(ChuckDuration d) {
    ChuckObjectPool.releaseDuration(d);
  }

  public ChuckString getString(String val) {
    return ChuckObjectPool.getString(val);
  }

  public void releaseString(ChuckString s) {
    ChuckObjectPool.releaseString(s);
  }
}
