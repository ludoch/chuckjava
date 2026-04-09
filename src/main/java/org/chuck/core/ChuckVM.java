package org.chuck.core;

import org.chuck.audio.Adc;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.Blackhole;
import org.chuck.chugin.ChuginLoader;
import org.chuck.compiler.ChuckANTLRLexer;
import org.chuck.compiler.ChuckANTLRParser;
import org.chuck.compiler.ChuckAST;
import org.chuck.compiler.ChuckASTVisitor;
import org.chuck.compiler.ChuckEmitter;
import org.antlr.v4.runtime.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.PriorityQueue;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import org.chuck.audio.DacChannel;
import org.chuck.audio.MultiChannelDac;
import org.chuck.hid.Hid;
import org.chuck.hid.HidMsg;

/**
 * The ChucK Virtual Machine.
 * Manages shreds, timing, and global state.
 */
public class ChuckVM {
    /** The current VM instance for the current thread (ScopedValue). */
    public static final ScopedValue<ChuckVM> CURRENT_VM = ScopedValue.newInstance();

    // Current logical time in samples
    private final AtomicLong now = new AtomicLong(0);
    
    // Shred management
    private final PriorityQueue<ChuckShred> shreduler = new PriorityQueue<>();
    private final Map<Integer, ChuckShred> activeShreds = new ConcurrentHashMap<>();
    private final ReentrantLock shredulerLock = new ReentrantLock();
    private final Condition shredulerCondition = shredulerLock.newCondition();
    private int nextShredId = 1;
    
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
    private final Set<String> staticInitializedClasses = java.util.concurrent.ConcurrentHashMap.newKeySet();

    // Multi-channel support
    private final int numChannels;
    private final DacChannel[] dacChannels;
    private final MultiChannelDac multiChannelDac;
    
    // Special globals
    public final MultiChannelDac dac;
    public final Adc adc;
    public final Blackhole blackhole;

    // Listeners for printed output
    private final List<java.util.function.Consumer<String>> printListeners = new ArrayList<>();

    // HID management
    private final List<Hid> registeredHids = new ArrayList<>();

    // Pending event timeouts: fired when VM clock >= wakeTime
    private record TimeoutEntry(ChuckShred waitingShred, ChuckEvent event, long wakeTime) {}
    private final java.util.List<TimeoutEntry> pendingTimeouts = new java.util.concurrent.CopyOnWriteArrayList<>();

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

    public int getSampleRate() { return sampleRate; }
    public long getCurrentTime() { return now.get(); }
    
    public void addPrintListener(java.util.function.Consumer<String> listener) {
        synchronized(printListeners) { printListeners.add(listener); }
    }
    
    public void print(String text) {
        synchronized(printListeners) {
            if (printListeners.isEmpty()) System.out.print(text);
            else printListeners.forEach(l -> l.accept(text));
        }
    }

    public MultiChannelDac getMultiChannelDac() { return multiChannelDac; }
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
            // Keep the OLD static state, but accept the NEW method definitions and descriptor.
            // We want to update the methods/code but NOT reset the static variables.
            // Since UserClassDescriptor is a record, we must use the new one's maps
            // but fill them with existing data if present.
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
            CommonTokenStream tokens = new CommonTokenStream(lexer);
            ChuckANTLRParser parser = new ChuckANTLRParser(tokens);
            
            ChuckASTVisitor visitor = new ChuckASTVisitor();
            @SuppressWarnings("unchecked")
            List<ChuckAST.Stmt> ast = (List<ChuckAST.Stmt>) visitor.visit(parser.program());

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
                System.err.println(msg);
            }
            if (logLevel >= 2) {
                e.printStackTrace();
            }
            return -1;
        }
    }

    public int add(String filename) {
        return add(filename, null);
    }

    public int add(String filename, ChuckShred caller) {
        try {
            // ChucK supports passing arguments to added shreds via colons: "file.ck:arg1:arg2"
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
        schedule(shred);
        
        // Start shred execution in a Virtual Thread
        Thread.ofVirtual().start(() -> {
            try {
                // Ensure we are inside the ScopedValue context
                ScopedValue.where(CURRENT_VM, this)
                          .where(ChuckShred.CURRENT_SHRED, shred)
                          .run(() -> shred.execute(this));
            } finally {
                activeShreds.remove(shred.getId());
                shred.setDone(true);
                shred.cleanup();
                // Wake up any shreds waiting on this shred (if it's an Event)
                shred.broadcast(this);
            }
        });
        
        return shred.getId();
    }

    public int spork(Runnable task) {
        ChuckShred shred = new ChuckShred(null); // No ChucK code
        if (shred.getId() <= 0) {
            shred.setId(nextShredId++);
        }
        activeShreds.put(shred.getId(), shred);
        schedule(shred);
        
        Thread.ofVirtual().start(() -> {
            try {
                ScopedValue.where(CURRENT_VM, this)
                          .where(ChuckShred.CURRENT_SHRED, shred)
                          .run(() -> {
                              shred.waitForResume();
                              task.run();
                          });
            } finally {
                activeShreds.remove(shred.getId());
                shred.setDone(true);
                shred.cleanup();
                shred.broadcast(this);
            }
        });
        
        return shred.getId();
    }

    public void schedule(ChuckShred shred) {
        shredulerLock.lock();
        try {
            shreduler.add(shred);
            shredulerCondition.signalAll();
        } finally {
            shredulerLock.unlock();
        }
    }

    /**
     * Schedules a timeout to wake a shred if the event hasn't triggered by wakeTime.
     * Fired directly from advanceTime (no extra thread).
     */
    public void scheduleTimeout(ChuckShred shred, ChuckEvent event, long wakeTime) {
        pendingTimeouts.add(new TimeoutEntry(shred, event, wakeTime));
    }

    /** Check and fire any pending timeouts whose wakeTime <= current VM time. */
    private void fireTimeouts(long currentTime) {
        pendingTimeouts.removeIf(t -> {
            if (currentTime < t.wakeTime()) return false;
            // Only fire if the shred is still waiting on THIS event
            if (t.waitingShred().isWaiting()
                    && (t.waitingShred().getEventWaitingOn() == t.event()
                        || t.waitingShred().getEventWaitingOn() == null)) {
                t.event().removeWaitingShred(t.waitingShred());
                t.waitingShred().setEventWaitingOn(null);
                t.waitingShred().setWakeTime(currentTime);
                t.waitingShred().resume(this);
                if (!t.waitingShred().isDone() && !t.waitingShred().isWaiting()) {
                    schedule(t.waitingShred());
                }
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
        while (now.get() < targetTime) {
            long currentTime = now.get();

            // 0. Check timeouts
            fireTimeouts(currentTime);

            // 1. Run all shreds scheduled for this time (allow 0-yield chaining)
            int safety = 0;
            while (safety++ < 1000) {
                java.util.List<ChuckShred> ready = new java.util.ArrayList<>();
                shredulerLock.lock();
                try {
                    while (!shreduler.isEmpty() && shreduler.peek().getWakeTime() <= currentTime) {
                        ready.add(shreduler.poll());
                    }
                } finally {
                    shredulerLock.unlock();
                }

                if (ready.isEmpty()) break;

                for (ChuckShred nextShred : ready) {
                    nextShred.resume(this);
                    if (!nextShred.isDone() && !nextShred.isWaiting()) {
                        schedule(nextShred);
                    }
                }
            }

            // 2. Roots are the DAC channels
            for (DacChannel chan : dacChannels) {
                chan.tick(currentTime);
            }
            
            now.incrementAndGet();
        }
    }

    public int getActiveShredCount() {
        return activeShreds.size();
    }

    public int getNumShreds() { return activeShreds.size(); }

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
        ChuckShred s = activeShreds.get(id);
        if (s != null) s.setDone(true);
    }

    public void resetShredId() {
        nextShredId = 1;
        ChuckShred.resetIdCounter();
    }

    /** eval(source, args[]) — spork eval'd shred with string arguments. */
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

    /** Remove the most recently sporked shred; returns its ID or -1 if none. */
    public int removeLastShred() {
        if (activeShreds.isEmpty()) return -1;
        int lastId = activeShreds.keySet().stream().mapToInt(Integer::intValue).max().orElse(-1);
        if (lastId > 0) removeShred(lastId);
        return lastId;
    }

    // Machine API
    private static final String VERSION = "1.5.4.0 (java)";
    private int logLevel = 1;

    public void gc() {
        System.gc();
    }

    public String getVersion() { return VERSION; }
    public String getPlatform() { return System.getProperty("os.name", "unknown"); }

    public void clear() {
        activeShreds.values().forEach(s -> s.setDone(true));
        activeShreds.clear();
        shredulerLock.lock();
        try {
            shreduler.clear();
        } finally {
            shredulerLock.unlock();
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
        sb.append("--- ChucK VM Status ---\n");
        sb.append("Time: ").append(now.get()).append("\n");
        sb.append("Active Shreds: ").append(activeShreds.size()).append("\n");
        for (ChuckShred s : activeShreds.values()) {
            sb.append("  [").append(s.getId()).append("] ").append(s.getCode().getName()).append("\n");
        }
        return sb.toString();
    }

    public void setLogLevel(int level) { this.logLevel = level; }
    public int getLogLevel() { return logLevel; }
}
