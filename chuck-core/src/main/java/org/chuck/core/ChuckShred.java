package org.chuck.core;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Logger;
import org.chuck.audio.ChuckUGen;
import org.chuck.core.instr.*;

/** A ChucK Shred - an independent thread of execution in the VM. */
public class ChuckShred implements Shred, Comparable<ChuckShred> {
  private static final Logger logger = Logger.getLogger(ChuckShred.class.getName());
  private static final AtomicInteger ID_GENERATOR = new AtomicInteger(1);
  public static final ScopedValue<ChuckShred> CURRENT_SHRED = ScopedValue.newInstance();

  @Override
  public int compareTo(ChuckShred other) {
    return Long.compare(this.wakeTime, other.wakeTime);
  }

  private int id;
  private String name = "shred";
  private String[] args = new String[0];

  private ChuckCode code;
  private int pc = 0;
  public int framePointer = 0;

  public final ChuckContext.Memory mem = new ChuckContext.Memory();
  public final ChuckContext.Registers reg = new ChuckContext.Registers();
  public final Stack<UserObject> thisStack = new Stack<>();

  private volatile boolean isRunning = false;
  public volatile boolean isDone = false;
  private long wakeTime = 0;

  private final ReentrantLock lock = new ReentrantLock();
  private final Condition condition = lock.newCondition();
  private Thread thread;

  private final List<ChuckUGen> registeredUGens = new ArrayList<>();
  private final List<UserObject> registeredDestructibles = new ArrayList<>();
  private final List<AutoCloseable> registeredCloseables = new ArrayList<>();

  private final ReentrantLock eventLock = new ReentrantLock();
  private final List<ChuckShred> waitingShreds = new ArrayList<>();
  private ChuckEvent eventWaitingOn = null;
  private boolean wasSignaled = false;
  private final ChuckObjectPool.ShredAllocator allocator = new ChuckObjectPool.ShredAllocator();

  private String lastExceptionMessage = null;
  private Runnable onNextPark = null;
  private long instructionCount = 0;
  private static final long MAX_INSTRUCTIONS_BEFORE_YIELD = 10000;

  public ChuckShred(ChuckCode code) {
    this.id = ID_GENERATOR.getAndIncrement();
    this.code = code;
    if (code != null) {
      this.name = code.getName();
      this.mem.setSp(code.getStackSize());
    }
  }

  public ChuckShred() {
    this(null);
  }

  @Override
  public void shred() {
    ChuckVM vm = ChuckVM.CURRENT_VM.isBound() ? ChuckVM.CURRENT_VM.get() : null;
    if (vm != null && code != null) {
      execute(vm);
    }
  }

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

  public void setArgs(String[] args) {
    this.args = args;
  }

  public String[] args() {
    return args;
  }

  public String arg(int i) {
    return (args != null && i >= 0 && i < args.length) ? args[i] : "";
  }

  public void setWakeTime(long time) {
    this.wakeTime = time;
  }

  public long getWakeTime() {
    return wakeTime;
  }

  public void setRunning(boolean running) {
    lock.lock();
    try {
      this.isRunning = running;
      if (running) condition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public void waitForResume() {
    lock.lock();
    try {
      while (!isRunning && !isDone) {
        condition.await();
      }
    } catch (InterruptedException e) {
      isDone = true;
    } finally {
      lock.unlock();
    }
  }

  public void setThread(Thread t) {
    this.thread = t;
  }

  public void setDone(boolean done) {
    this.isDone = done;
  }

  public boolean isDone() {
    return isDone;
  }

  public boolean running() {
    return isRunning;
  }

  public void yield(long samples) {
    suspendOnTime(samples);
  }

  public void suspendOnTime(long samples) {
    ChuckVM currentVm = ChuckVM.CURRENT_VM.isBound() ? ChuckVM.CURRENT_VM.get() : null;
    lock.lock();
    try {
      this.wakeTime += samples;
      this.isRunning = false;
      notifyParked();
    } finally {
      lock.unlock();
    }

    if (currentVm != null && !isDone && eventWaitingOn == null) {
      currentVm.schedule(this);
    }

    waitForResume();
  }

  public void execute(ChuckVM vm) {
    lock.lock();
    try {
      while (!isDone && code != null) {
        while (!isRunning && !isDone) {
          condition.await();
        }
        if (isDone) break;

        while (!isDone && isRunning && code != null && pc < code.getNumInstructions()) {
          ChuckInstr instr = code.getInstruction(pc);
          if (instr == null) break;

          if (++instructionCount > MAX_INSTRUCTIONS_BEFORE_YIELD) {
            instructionCount = 0;
            lock.unlock();
            try {
              this.yield(0);
            } finally {
              lock.lock();
            }
            if (isDone || !isRunning) break;
            instr = code.getInstruction(pc);
          }

          int oldPc = pc;
          ChuckCode oldCode = code;
          try {
            instr.execute(vm, this);
            if (code == oldCode && pc == oldPc) pc++;
          } catch (Exception e) {
            lastExceptionMessage =
                String.format(
                    "%s at pc[%d] in %s",
                    e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName(),
                    pc,
                    name);
            vm.print("[chuck]:(EXCEPTION) " + lastExceptionMessage + "\n");
            isDone = true;
            isRunning = false;
            return;
          }
        }
        if (isRunning && !isDone && pc >= code.getNumInstructions()) isDone = true;
      }
    } catch (InterruptedException e) {
      isDone = true;
    } finally {
      isRunning = false;
      condition.signalAll();
      notifyParked();
      lock.unlock();
    }
  }

  public void executeSynchronous(ChuckVM vm, ChuckCode code) {
    if (code == null) return;
    ScopedValue.where(ChuckVM.CURRENT_VM, vm)
        .where(CURRENT_SHRED, this)
        .run(
            () -> {
              ChuckCode savedCode = this.code;
              int savedPc = this.pc;
              int savedFp = this.framePointer;
              this.code = code;
              this.pc = 0;
              boolean wasRunning = this.isRunning;
              this.isRunning = true;
              while (!isDone && pc < code.getNumInstructions()) {
                ChuckInstr instr = code.getInstruction(pc);
                if (instr == null) break;
                int opc = pc;
                ChuckCode oc = this.code;
                try {
                  instr.execute(vm, this);
                  if (this.code == oc && pc == opc) pc++;
                } catch (Exception t) {
                  isDone = true;
                }
              }
              this.code = savedCode;
              this.pc = savedPc;
              this.framePointer = savedFp;
              this.isRunning = wasRunning;
            });
  }

  public void executeCtorSynchronous(
      ChuckVM vm, ChuckCode ctorCode, UserObject thisObj, Object[] args, boolean[] isDouble) {
    if (ctorCode == null || thisObj == null) return;
    thisStack.push(thisObj);
    executeSynchronous(vm, ctorCode);
    thisStack.pop();
  }

  public void abort() {
    exit();
  }

  public void exit() {
    isDone = true;
    isRunning = false;
    if (thread != null) thread.interrupt();
    lock.lock();
    try {
      condition.signalAll();
    } finally {
      lock.unlock();
    }
  }

  public boolean isWaiting() {
    return eventWaitingOn != null;
  }

  public ChuckEvent getEventWaitingOn() {
    return eventWaitingOn;
  }

  public void setEventWaitingOn(ChuckEvent e) {
    this.eventWaitingOn = e;
  }

  public boolean wasSignaled() {
    return wasSignaled;
  }

  public void suspendOnEvent() {
    ChuckVM currentVm = ChuckVM.CURRENT_VM.isBound() ? ChuckVM.CURRENT_VM.get() : null;
    lock.lock();
    try {
      isRunning = false;
      condition.signalAll();
      notifyParked();
    } finally {
      lock.unlock();
    }

    waitForResume();
  }

  public void resume(ChuckVM vm) {
    resume(vm, false);
  }

  public void resume(ChuckVM vm, boolean fromEvent) {
    if (fromEvent) {
      eventWaitingOn = null;
      wasSignaled = true;
    }
    setRunning(true);
  }

  public boolean notifyTriggered(ChuckEvent e, ChuckVM vm) {
    eventWaitingOn = null;
    wasSignaled = true;
    resume(vm, true);
    return true;
  }

  public void onNextPark(Runnable r) {
    this.onNextPark = r;
  }

  private void notifyParked() {
    if (onNextPark != null) {
      onNextPark.run();
      onNextPark = null;
    }
  }

  public ChuckString getString(String val) {
    return ChuckObjectPool.getString(val);
  }

  public ChuckDuration getDuration(long samples) {
    return ChuckObjectPool.getDuration((double) samples);
  }

  public ChuckDuration getDuration(double samples) {
    return ChuckObjectPool.getDuration(samples);
  }

  public ChuckObjectPool.ShredAllocator getAllocator() {
    return allocator;
  }

  public String dir(int level) {
    return ".";
  }

  public String path() {
    return name != null ? name : ".";
  }

  public String source() {
    return name != null ? name : "source";
  }

  public String getLastExceptionMessage() {
    return lastExceptionMessage;
  }

  public static void resetIdCounter() {
    ID_GENERATOR.set(1);
  }

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

  public void registerUGen(ChuckUGen ugen) {
    registeredUGens.add(ugen);
  }

  public void registerDestructible(UserObject uo) {
    registeredDestructibles.add(uo);
  }

  public void registerCloseable(AutoCloseable c) {
    registeredCloseables.add(c);
  }

  public void setParentShred(ChuckShred parent) {
    /* Stub */
  }

  public void cleanup(ChuckVM vm) {
    registeredUGens.forEach(org.chuck.audio.ChuckUGen::disconnectAll);
    registeredCloseables.forEach(
        c -> {
          try {
            c.close();
          } catch (Exception ignored) {
          }
        });
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
}
