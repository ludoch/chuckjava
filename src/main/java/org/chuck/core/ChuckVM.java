package org.chuck.core;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.prefs.Preferences;
import org.antlr.v4.runtime.*;
import org.chuck.audio.util.Adc;
import org.chuck.audio.util.Blackhole;
import org.chuck.audio.util.DacChannel;
import org.chuck.audio.util.MultiChannelDac;
import org.chuck.compiler.ChuckANTLRLexer;
import org.chuck.compiler.ChuckANTLRParser;
import org.chuck.compiler.ChuckAST;
import org.chuck.compiler.ChuckASTVisitor;
import org.chuck.compiler.ChuckEmitter;
import org.chuck.hid.Hid;
import org.chuck.hid.HidMsg;

/** The ChucK Virtual Machine. Manages shreds, timing, and global state. */
public class ChuckVM {
  private static final Logger logger = Logger.getLogger(ChuckVM.class.getName());

  /** The current VM instance for the current thread (ScopedValue). */
  public static final ScopedValue<ChuckVM> CURRENT_VM = ScopedValue.newInstance();

  // Current logical time in samples
  private final AtomicLong now = new AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong totalJitter =
      new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong wakeCount =
      new java.util.concurrent.atomic.AtomicLong(0);
  private final java.util.concurrent.atomic.AtomicLong maxJitter =
      new java.util.concurrent.atomic.AtomicLong(0);

  private org.chuck.audio.ChuckAudio audio;

  public void setAudio(org.chuck.audio.ChuckAudio audio) {
    this.audio = audio;
  }

  public double getAverageDrift() {
    return audio != null ? audio.getAverageDriftMs() : 0.0;
  }

  public double getMaxDrift() {
    return audio != null ? audio.getMaxDriftMs() : 0.0;
  }

  // Cached preference: whether to use parallel shred execution
  private static final boolean PARALLEL_ENGINE =
      Preferences.userNodeForPackage(org.chuck.ide.ChuckIDE.class)
          .getBoolean("engine.parallel", false);

  // Shred management
  private final PriorityQueue<ChuckShred> shreduler = new PriorityQueue<>();
  private final Map<Integer, ChuckShred> activeShreds = new ConcurrentHashMap<>();
  private final java.util.List<ChuckShred> deadShreds =
      java.util.Collections.synchronizedList(new java.util.ArrayList<>());
  private final ReentrantLock shredulerLock = new ReentrantLock();
  private final Condition shredulerCondition = shredulerLock.newCondition();
  private int nextShredId = 1;

  // Count of virtual threads currently executing a shred (including finally cleanup)
  private final java.util.concurrent.atomic.AtomicInteger liveThreadCount =
      new java.util.concurrent.atomic.AtomicInteger(0);

  // Audio configuration
  private final int sampleRate;

  // Global variables
  private final Map<String, Long> globalInts = new ConcurrentHashMap<>();
  private final Map<String, Boolean> globalIsInt = new ConcurrentHashMap<>();
  private final Map<String, Boolean> globalIsDouble = new ConcurrentHashMap<>();
  private final Map<String, Boolean> globalIsObject = new ConcurrentHashMap<>();
  private final Map<String, Object> globalObjects = new ConcurrentHashMap<>();
  private final Map<String, String> globalDocs = new ConcurrentHashMap<>();
  private final Map<String, String> globalFunctionDocs = new ConcurrentHashMap<>();
  private final Map<String, UserClassDescriptor> userClassRegistry = new ConcurrentHashMap<>();
  private final Set<String> staticInitializedClasses =
      java.util.concurrent.ConcurrentHashMap.newKeySet();

  // Multi-channel support
  private final int numChannels;
  private final DacChannel[] dacChannels;
  private final MultiChannelDac multiChannelDac;

  // Special globals
  public final MultiChannelDac dac;
  public final Adc adc;
  public final Blackhole blackhole;

  private volatile boolean graphDirty = true;
  private org.chuck.audio.ChuckUGen[] sortedUGens = new org.chuck.audio.ChuckUGen[0];

  public void invalidateGraph() {
    graphDirty = true;
  }

  private void rebuildGraph() {
    if (!graphDirty) return;

    List<org.chuck.audio.ChuckUGen> result = new ArrayList<>();
    java.util.Set<org.chuck.audio.ChuckUGen> visited =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
    java.util.Set<org.chuck.audio.ChuckUGen> stack =
        java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());

    // Traverse from roots: dacChannels and blackhole
    for (DacChannel chan : dacChannels) {
      sortDFS(chan, visited, stack, result);
    }
    sortDFS(blackhole, visited, stack, result);

    this.sortedUGens = result.toArray(new org.chuck.audio.ChuckUGen[0]);
    graphDirty = false;
  }

  private void sortDFS(
      org.chuck.audio.ChuckUGen ugen,
      java.util.Set<org.chuck.audio.ChuckUGen> visited,
      java.util.Set<org.chuck.audio.ChuckUGen> stack,
      List<org.chuck.audio.ChuckUGen> result) {
    if (ugen == null || visited.contains(ugen)) return;

    visited.add(ugen);
    stack.add(ugen);

    // Recursively visit sources (dependencies)
    for (org.chuck.audio.ChuckUGen src : ugen.getSources()) {
      sortDFS(src, visited, stack, result);
    }

    stack.remove(ugen);
    result.add(ugen); // Post-order ensures dependencies come first
  }

  // Listeners for printed output
  private final List<java.util.function.Consumer<String>> printListeners = new ArrayList<>();

  // HID management
  private final List<Hid> registeredHids = new ArrayList<>();

  // Pending event timeouts: fired when VM clock >= wakeTime
  private record TimeoutEntry(ChuckShred waitingShred, ChuckEvent event, long wakeTime) {}

  private final java.util.List<TimeoutEntry> pendingTimeouts =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  public ChuckVM(int sampleRate) {
    this(sampleRate, 2);
  }

  public ChuckVM(int sampleRate, int channels) {
    this.sampleRate = sampleRate;
    this.numChannels = channels;
    this.dacChannels = new DacChannel[numChannels];
    for (int i = 0; i < numChannels; i++) {
      dacChannels[i] = new DacChannel(i);
    }
    this.multiChannelDac = new MultiChannelDac(dacChannels);
    this.dac = multiChannelDac;
    this.adc = new Adc();
    this.blackhole = new Blackhole();

    // Initialize special globals
    setGlobalObject("dac", multiChannelDac);
    setGlobalObject("blackhole", blackhole);
    setGlobalObject("adc", adc);
    setGlobalObject("Machine", this);
    initIO(System.out, System.err);
  }

  public void initIO(java.io.PrintStream out, java.io.PrintStream err) {
    setGlobalObject("chout", new ChuckIO(out, this));
    setGlobalObject("cherr", new ChuckIO(err, this));
    setGlobalObject("IO", new ChuckIO(out, this));
  }

  public int getSampleRate() {
    return sampleRate;
  }

  public long getCurrentTime() {
    return now.get();
  }

  public void addPrintListener(java.util.function.Consumer<String> listener) {
    synchronized (printListeners) {
      printListeners.add(listener);
    }
  }

  public void print(String text) {
    synchronized (printListeners) {
      if (printListeners.isEmpty()) System.out.print(text);
      else printListeners.forEach(l -> l.accept(text));
    }
  }

  public MultiChannelDac getMultiChannelDac() {
    return multiChannelDac;
  }

  public DacChannel getDacChannel(int channel) {
    return dacChannels[channel % numChannels];
  }

  public float getChannelLastOut(int channel) {
    return dacChannels[channel % numChannels].getLastOut();
  }

  public int getNumChannels() {
    return numChannels;
  }

  public void setGlobalInt(String name, long val) {
    globalInts.put(name, val);
    globalIsInt.put(name, true);
    globalIsDouble.put(name, false);
    globalIsObject.put(name, false);
  }

  public void setGlobalFloat(String name, double value) {
    globalInts.put(name, Double.doubleToRawLongBits(value));
    globalIsDouble.put(name, true);
    globalIsInt.put(name, false);
    globalIsObject.put(name, false);
  }

  public long getGlobalInt(String name) {
    return globalInts.getOrDefault(name, 0L);
  }

  public double getGlobalFloat(String name) {
    return Double.longBitsToDouble(getGlobalInt(name));
  }

  public boolean isGlobalInt(String name) {
    return globalIsInt.getOrDefault(name, false);
  }

  public boolean isGlobalDouble(String name) {
    return globalIsDouble.getOrDefault(name, false);
  }

  public boolean isGlobalObject(String name) {
    return globalIsObject.getOrDefault(name, false);
  }

  public void setGlobalObject(String name, Object obj) {
    globalIsObject.put(name, true);
    globalIsInt.put(name, false);
    globalIsDouble.put(name, false);
    if (obj == null) {
      globalObjects.remove(name);
    } else {
      globalObjects.put(name, obj);
    }
  }

  public Object getGlobalObject(String name) {
    return globalObjects.get(name);
  }

  public void setGlobalDoc(String name, String doc) {
    if (doc == null) globalDocs.remove(name);
    else globalDocs.put(name, doc);
  }

  public String getGlobalDoc(String name) {
    return globalDocs.get(name);
  }

  public void setGlobalFunctionDoc(String key, String doc) {
    if (doc == null) globalFunctionDocs.remove(key);
    else globalFunctionDocs.put(key, doc);
  }

  public String getGlobalFunctionDoc(String key) {
    return globalFunctionDocs.get(key);
  }

  public Set<String> getGlobalFunctionDocKeys() {
    return globalFunctionDocs.keySet();
  }

  public Set<String> getGlobalIntKeys() {
    return globalIsInt.entrySet().stream()
        .filter(java.util.Map.Entry::getValue)
        .map(java.util.Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toSet());
  }

  public Set<String> getGlobalFloatKeys() {
    return globalIsDouble.entrySet().stream()
        .filter(java.util.Map.Entry::getValue)
        .map(java.util.Map.Entry::getKey)
        .collect(java.util.stream.Collectors.toSet());
  }

  public Map<String, UserClassDescriptor> getUserClassRegistry() {
    return userClassRegistry;
  }

  public UserClassDescriptor getUserClass(String name) {
    return userClassRegistry.get(name);
  }

  public boolean isStaticInitialized(String name) {
    return staticInitializedClasses.contains(name);
  }

  public void setStaticInitialized(String name) {
    staticInitializedClasses.add(name);
  }

  public void registerUserClass(String name, UserClassDescriptor desc) {
    UserClassDescriptor existing = userClassRegistry.get(name);
    if (existing != null) {
      desc.staticInts().putAll(existing.staticInts());
      desc.staticIsDouble().putAll(existing.staticIsDouble());
      desc.staticObjects().putAll(existing.staticObjects());
    }
    userClassRegistry.put(name, desc);
  }

  public Map<String, String> getGlobalTypeMap() {
    Map<String, String> types = new HashMap<>();
    for (String n : globalIsInt.keySet()) if (globalIsInt.get(n)) types.put(n, "int");
    for (String n : globalIsDouble.keySet()) if (globalIsDouble.get(n)) types.put(n, "float");
    for (String n : globalIsObject.keySet()) {
      if (globalIsObject.get(n)) {
        Object obj = globalObjects.get(n);
        if (obj instanceof ChuckArray ca && ca.vecTag != null) {
          types.put(n, ca.vecTag);
        } else if (obj instanceof UserObject uo) {
          types.put(n, uo.className);
        } else if (obj instanceof ChuckObject co) {
          types.put(n, co.getType().getName());
        } else if (obj != null) {
          types.put(n, obj.getClass().getSimpleName());
        }
      }
    }
    return types;
  }

  public int run(String source, String name) {
    try {
      CharStream input = CharStreams.fromString(source);
      ChuckANTLRLexer lexer = new ChuckANTLRLexer(input);
      lexer.removeErrorListeners();
      lexer.addErrorListener(
          new BaseErrorListener() {
            @Override
            public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
              print(String.format("Lexer error: line %d:%d %s\n", line, charPositionInLine, msg));
            }
          });

      CommonTokenStream tokens = new CommonTokenStream(lexer);
      ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
      parser.removeErrorListeners();
      parser.addErrorListener(
          new BaseErrorListener() {
            @Override
            public void syntaxError(
                Recognizer<?, ?> recognizer,
                Object offendingSymbol,
                int line,
                int charPositionInLine,
                String msg,
                RecognitionException e) {
              print(String.format("Parser error: line %d:%d %s\n", line, charPositionInLine, msg));
            }
          });

      ChuckANTLRParser.ProgramContext programCtx = parser.program();
      if (parser.getNumberOfSyntaxErrors() > 0) return -1;

      ChuckASTVisitor visitor = new ChuckASTVisitor();
      @SuppressWarnings("unchecked")
      List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(programCtx);

      ChuckEmitter emitter = new ChuckEmitter(userClassRegistry);
      ChuckEmitter.EmitResult result = emitter.emitWithDocs(ast, name, getGlobalTypeMap());

      // Register docs
      result.globalDocs().forEach(this::setGlobalDoc);
      result.functionDocs().forEach(this::setGlobalFunctionDoc);

      ChuckShred shred = new ChuckShred(result.code());
      return spork(shred);
    } catch (Exception e) {
      String msg = "Machine.run error: " + e.getMessage();
      if (logLevel >= 1) {
        print(msg + "\n");
        logger.log(Level.SEVERE, msg);
      }
      if (logLevel >= 2) {
        logger.log(Level.SEVERE, "Machine.run stacktrace:", e);
      }
      return -1;
    }
  }

  public int add(String filename) {
    return add(filename, null);
  }

  public int add(String filename, ChuckShred caller) {
    try {
      String baseFilename = filename;
      String[] parts = filename.split(":");
      if (parts.length > 1 && (parts[0].endsWith(".ck") || parts[0].endsWith(".disabled"))) {
        baseFilename = parts[0];
      }

      java.nio.file.Path path = java.nio.file.Paths.get(baseFilename);
      if (!path.isAbsolute() && caller != null) {
        String callerPath = caller.getCode().getName();
        if (callerPath != null && !callerPath.equals("eval")) {
          java.nio.file.Path callerDir = java.nio.file.Paths.get(callerPath).getParent();
          if (callerDir != null) {
            path = callerDir.resolve(baseFilename);
          }
        }
      }
      String source = java.nio.file.Files.readString(path);

      int id = run(source, path.toString());

      // Pass arguments if any
      if (id > 0 && parts.length > 1 && baseFilename.equals(parts[0])) {
        ChuckShred ns = activeShreds.get(id);
        if (ns != null) {
          String[] shredArgs = new String[parts.length - 1];
          System.arraycopy(parts, 1, shredArgs, 0, parts.length - 1);
          ns.setArgs(shredArgs);
        }
      }
      return id;
    } catch (IOException e) {
      print("Error adding file: " + filename + "\n");
      return -1;
    }
  }

  public int eval(String code) {
    return run(code, "eval");
  }

  public int replace(int id, String filename) {
    return replace(id, filename, null);
  }

  public int replace(int id, String filename, ChuckShred caller) {
    removeShred(id);
    return add(filename, caller);
  }

  public int spork(ChuckShred shred) {
    if (shred.getId() <= 0) {
      shred.setId(nextShredId++);
    }
    activeShreds.put(shred.getId(), shred);
    shred.setWakeTime(now.get());
    schedule(shred);

    liveThreadCount.incrementAndGet();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                ScopedValue.where(CURRENT_VM, this)
                    .where(ChuckShred.CURRENT_SHRED, shred)
                    .run(() -> shred.execute(this));
              } finally {
                activeShreds.remove(shred.getId());
                shred.setDone(true);
                shred.cleanup(this);
                shred.broadcast(this);
                deadShreds.add(shred);
                if (deadShreds.size() > 50) deadShreds.remove(0);
                liveThreadCount.decrementAndGet();
              }
            });

    return shred.getId();
  }

  public int spork(Runnable task) {
    ChuckShred shred = new ChuckShred(null);
    if (shred.getId() <= 0) {
      shred.setId(nextShredId++);
    }
    activeShreds.put(shred.getId(), shred);
    shred.setWakeTime(now.get());
    schedule(shred);

    liveThreadCount.incrementAndGet();
    Thread.ofVirtual()
        .start(
            () -> {
              try {
                ScopedValue.where(CURRENT_VM, this)
                    .where(ChuckShred.CURRENT_SHRED, shred)
                    .run(
                        () -> {
                          shred.waitForResume();
                          task.run();
                        });
              } finally {
                activeShreds.remove(shred.getId());
                shred.setDone(true);
                shred.cleanup(this);
                shred.broadcast(this);
                deadShreds.add(shred);
                if (deadShreds.size() > 50) deadShreds.remove(0);
                liveThreadCount.decrementAndGet();
              }
            });

    return shred.getId();
  }

  private final java.util.concurrent.atomic.AtomicLong nextWakeTime =
      new java.util.concurrent.atomic.AtomicLong(Long.MAX_VALUE);

  public long getNextWakeTime() {
    return nextWakeTime.get();
  }

  private void updateNextWakeTime() {
    shredulerLock.lock();
    try {
      if (shreduler.isEmpty()) {
        nextWakeTime.set(Long.MAX_VALUE);
      } else {
        nextWakeTime.set(shreduler.peek().getWakeTime());
      }
    } finally {
      shredulerLock.unlock();
    }
  }

  public void schedule(ChuckShred shred) {
    shredulerLock.lock();
    try {
      shreduler.add(shred);
      if (shred.getWakeTime() < nextWakeTime.get()) {
        nextWakeTime.set(shred.getWakeTime());
      }
      shredulerCondition.signalAll();
    } finally {
      shredulerLock.unlock();
    }
  }

  public void scheduleTimeout(ChuckShred shred, ChuckEvent event, long wakeTime) {
    pendingTimeouts.add(new TimeoutEntry(shred, event, wakeTime));
  }

  private void fireTimeouts(long currentTime) {
    pendingTimeouts.removeIf(
        t -> {
          if (currentTime < t.wakeTime()) return false;
          if (t.waitingShred().isWaiting()
              && (t.waitingShred().getEventWaitingOn() == t.event()
                  || t.waitingShred().getEventWaitingOn() == null)) {
            t.event().removeWaitingShred(t.waitingShred());
            t.waitingShred().setEventWaitingOn(null);
            t.waitingShred().setWakeTime(currentTime);
            t.waitingShred().resume(this);
          }
          return true;
        });
  }

  public void executeSynchronous(ChuckCode code, ChuckShred shred) {
    if (code != null) {
      shred.executeSynchronous(this, code);
    }
  }

  public void advanceTime(long samples) {
    long targetTime = now.get() + samples;
    boolean parallel = PARALLEL_ENGINE;

    while (now.get() < targetTime) {
      long currentTime = now.get();
      fireTimeouts(currentTime);

      if (currentTime + 1 < nextWakeTime.get()) {
        for (DacChannel chan : dacChannels) chan.tick(currentTime);
        blackhole.tick(currentTime);
        now.incrementAndGet();
        continue;
      }

      int safety = 0;
      while (safety++ < 1000) {
        List<ChuckShred> ready = new ArrayList<>();
        shredulerLock.lock();
        try {
          while (!shreduler.isEmpty() && shreduler.peek().getWakeTime() <= currentTime) {
            ready.add(shreduler.poll());
          }
          if (!ready.isEmpty()) {
            updateNextWakeTime();
          }
        } finally {
          shredulerLock.unlock();
        }

        if (ready.isEmpty()) break;

        if (parallel && ready.size() > 1) {
          final Phaser phaser = new Phaser(1);
          for (ChuckShred nextShred : ready) {
            updateJitter(currentTime, nextShred);
            phaser.register();
            nextShred.onNextPark(phaser::arriveAndDeregister);
            nextShred.resume(this, false);
          }
          phaser.arriveAndAwaitAdvance();
        } else {
          for (ChuckShred nextShred : ready) {
            updateJitter(currentTime, nextShred);
            nextShred.resume(this, true);
          }
        }
      }

      for (DacChannel chan : dacChannels) chan.tick(currentTime);
      blackhole.tick(currentTime);
      now.incrementAndGet();
    }
  }

  private void updateJitter(long currentTime, ChuckShred shred) {
    long jitter = currentTime - shred.getWakeTime();
    totalJitter.addAndGet(jitter);
    wakeCount.incrementAndGet();
    if (jitter > maxJitter.get()) maxJitter.set(jitter);
  }

  public void advanceTime(float[][] dacBuffers, int offset, int length) {
    int processed = 0;
    while (processed < length) {
      long currentTime = now.get();
      long nextWake = nextWakeTime.get();
      int remaining = length - processed;
      int blockSize = (int) Math.min(remaining, Math.max(1, nextWake - currentTime));

      if (blockSize > 1) {
        rebuildGraph();
        for (org.chuck.audio.ChuckUGen ugen : sortedUGens) {
          ugen.tick(null, 0, blockSize, currentTime);
        }
        for (int c = 0; c < numChannels; c++) {
          System.arraycopy(
              dacChannels[c].getBlockCache(), 0, dacBuffers[c], offset + processed, blockSize);
        }
        now.addAndGet(blockSize);
        processed += blockSize;
      } else {
        advanceTime(1);
        for (int c = 0; c < numChannels; c++) {
          dacBuffers[c][offset + processed] = dacChannels[c].getLastOut();
        }
        processed++;
      }
    }
  }

  public double getAverageJitter() {
    long count = wakeCount.get();
    return count == 0 ? 0.0 : (double) totalJitter.get() / count;
  }

  public long getMaxJitter() {
    return maxJitter.get();
  }

  public int getActiveShredCount() {
    return activeShreds.size();
  }

  public int getNumShreds() {
    return activeShreds.size();
  }

  public int[] getActiveShredIds() {
    Set<Integer> ids = activeShreds.keySet();
    int[] result = new int[ids.size()];
    int i = 0;
    for (Integer id : ids) result[i++] = id;
    return result;
  }

  public boolean shredExists(int id) {
    return activeShreds.containsKey(id);
  }

  public ChuckShred getShred(int id) {
    return activeShreds.get(id);
  }

  public List<ChuckShred> getAllShreds() {
    return new ArrayList<>(activeShreds.values());
  }

  public void removeShred(int id) {
    ChuckShred s = activeShreds.remove(id);
    if (s != null) {
      s.cleanup(this);
    }
  }

  public void resetShredId() {
    nextShredId = 1;
    ChuckShred.resetIdCounter();
  }

  public int eval(String source, ChuckArray argArr) {
    int id = run(source, "eval");
    if (id > 0 && argArr != null) {
      ChuckShred shred = activeShreds.get(id);
      if (shred != null) {
        String[] strArgs = new String[argArr.size()];
        for (int i = 0; i < argArr.size(); i++) {
          Object v = argArr.getObject(i);
          strArgs[i] = v != null ? v.toString() : "";
        }
        shred.setArgs(strArgs);
      }
    }
    return id;
  }

  public int removeLastShred() {
    if (activeShreds.isEmpty()) return -1;
    int lastId = activeShreds.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
    if (lastId > 0) removeShred(lastId);
    return lastId;
  }

  private static final String VERSION = "1.5.4.0 (java)";
  private int logLevel = 1;

  public void gc() {
    System.gc();
  }

  public String getVersion() {
    return VERSION;
  }

  public String getPlatform() {
    return System.getProperty("os.name", "unknown");
  }

  public void clear() {
    java.util.List<ChuckShred> shreds = new java.util.ArrayList<>(activeShreds.values());
    activeShreds.clear();
    shredulerLock.lock();
    try {
      shreduler.clear();
    } finally {
      shredulerLock.unlock();
    }
    for (ChuckShred s : shreds) {
      s.cleanup(this);
    }
  }

  /**
   * Aborts all active shreds and waits for their virtual threads to exit. Use this for clean
   * teardown in tests or when the VM will no longer be used, so the GC can reclaim all memory.
   */
  public void shutdown() {
    java.util.List<ChuckShred> shreds = new java.util.ArrayList<>(activeShreds.values());
    activeShreds.clear();
    shredulerLock.lock();
    try {
      shreduler.clear();
    } finally {
      shredulerLock.unlock();
    }
    for (ChuckShred s : shreds) {
      s.abort();
    }
    // Wait up to 2 seconds for all virtual threads to exit their finally blocks
    long deadline = System.currentTimeMillis() + 2000;
    while (liveThreadCount.get() > 0 && System.currentTimeMillis() < deadline) {
      try {
        Thread.sleep(5);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }

  public double getTimeOfDay() {
    return System.currentTimeMillis() / 1000.0;
  }

  public void registerHid(Hid hid) {
    registeredHids.add(hid);
  }

  public void dispatchHidMsg(HidMsg msg) {
    for (Hid hid : registeredHids) {
      hid.dispatch(msg, this);
    }
  }

  public String status() {
    StringBuilder sb = new StringBuilder();
    double secs = (double) now.get() / sampleRate;
    sb.append(
        String.format(
            "[ChucK VM] uptime: %.2fs | shreds: %d | srate: %d Hz\n",
            secs, activeShreds.size(), sampleRate));

    if (audio != null) {
      sb.append(
          String.format(
              "           load: %.1f%% | drift avg: %.2fms | max: %.2fms\n",
              audio.getCpuLoad() * 100.0, getAverageDrift(), getMaxDrift()));
    }

    if (!activeShreds.isEmpty()) {
      sb.append("           Active Shreds:\n");
      for (ChuckShred s : activeShreds.values()) {
        ChuckCode c = s.getCode();
        sb.append(String.format("             [%d] %s", s.getId(), s.getName()));
        if (c != null && c.getJitExecutable() != null) sb.append(" (JIT)");
        sb.append("\n");
      }
    }

    if (!deadShreds.isEmpty()) {
      java.util.List<ChuckShred> failures =
          deadShreds.stream().filter(s -> s.getLastExceptionMessage() != null).toList();
      if (!failures.isEmpty()) {
        sb.append("           Recent Exceptions:\n");
        for (int i = Math.max(0, failures.size() - 5); i < failures.size(); i++) {
          ChuckShred s = failures.get(i);
          sb.append(
              String.format("             [%d] %s\n", s.getId(), s.getLastExceptionMessage()));
        }
      }
    }

    return sb.toString();
  }

  public void setLogLevel(int level) {
    this.logLevel = level;
  }

  public int getLogLevel() {
    return logLevel;
  }
}
