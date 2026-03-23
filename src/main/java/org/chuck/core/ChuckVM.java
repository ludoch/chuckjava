package org.chuck.core;

import org.chuck.audio.Adc;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.Blackhole;
import org.chuck.chugin.ChuginLoader;
import java.util.concurrent.atomic.AtomicLong;
import java.util.PriorityQueue;
import java.util.concurrent.locks.ReentrantLock;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The Virtual Machine is responsible for managing timing, shreds,
 * and sample-accurate execution.
 */
public class ChuckVM {
    /** The ScopedValue holding the active VM for the current scope. */
    public static final ScopedValue<ChuckVM> CURRENT_VM = ScopedValue.newInstance();
    
    // Global logical time in samples
    private final AtomicLong currentTime = new AtomicLong(0);
    
    // Shared Emitter to preserve class registry and metadata across shreds
    private final org.chuck.compiler.ChuckEmitter emitter;
    
    // Shreduler queue: orders shreds by wake-up time
    private final PriorityQueue<ChuckShred> shreduler = new PriorityQueue<>();
    private final Map<Integer, ChuckShred> activeShreds = new ConcurrentHashMap<>();
    private final ReentrantLock schedulerLock = new ReentrantLock();
    
    // Audio configuration
    private final int sampleRate;
    
    // Global variables
    private final Map<String, Long> globalInts = new ConcurrentHashMap<>();
    private final Map<String, Boolean> globalIsDouble = new ConcurrentHashMap<>();
    private final Map<String, Boolean> globalIsObject = new ConcurrentHashMap<>();
    private final Map<String, Object> globalObjects = new ConcurrentHashMap<>();
    private Map<String, UserClassDescriptor> userClassRegistry = new ConcurrentHashMap<>();
    private Map<String, ChuckCode> importedFunctions = new ConcurrentHashMap<>();

    public void registerUserClass(String name, UserClassDescriptor descriptor) {
        userClassRegistry.put(name, descriptor);
    }

    public UserClassDescriptor getUserClass(String name) {
        return userClassRegistry.get(name);
    }

    // Multi-channel output (dac)
    private final int numChannels = 2;
    private final ChuckUGen[] dacChannels = new ChuckUGen[numChannels];
    public final ChuckObject dac;
    public final Blackhole blackhole;
    public final Adc adc;

    public ChuckVM(int sampleRate) {
        this.sampleRate = sampleRate;
        this.emitter = new org.chuck.compiler.ChuckEmitter(userClassRegistry, importedFunctions);
        ChuginLoader.loadChugins("chugins");

        // Initialize dac channels as virtual summing nodes
        for (int i = 0; i < numChannels; i++) {
            dacChannels[i] = new org.chuck.audio.DacChannel(i);
        }

        this.dac = new ChuckObject(new ChuckType("dac", ChuckType.OBJECT, 0, 0));
        setGlobalObject("dac", dac);

        this.blackhole = new Blackhole();
        setGlobalObject("blackhole", blackhole);

        this.adc = new Adc();
        setGlobalObject("adc", adc);

        setGlobalObject("chout", new ChuckIO(System.out, this));
        setGlobalObject("cherr", new ChuckIO(System.err, this));
        setGlobalObject("IO", new ChuckIO(System.out, this));
    }

    public ChuckUGen getDacChannel(int channel) {
        return dacChannels[channel % numChannels];
    }

    public ChuckUGen getMultiChannelDac() {
        return new org.chuck.audio.MultiChannelDac(dacChannels);
    }

    public float getChannelLastOut(int channel) {
        return dacChannels[channel % numChannels].getLastOut();
    }

    public int getNumChannels() {
        return numChannels;
    }

    public void setGlobalInt(String name, long value) {
        globalInts.put(name, value);
        globalIsDouble.put(name, false);
        globalIsObject.put(name, false);
    }

    public void setGlobalFloat(String name, double value) {
        globalInts.put(name, Double.doubleToRawLongBits(value));
        globalIsDouble.put(name, true);
        globalIsObject.put(name, false);
    }

    public long getGlobalInt(String name) {
        return globalInts.getOrDefault(name, 0L);
    }

    public boolean isGlobalDouble(String name) {
        return globalIsDouble.getOrDefault(name, false);
    }

    public boolean isGlobalObject(String name) {
        return globalIsObject.getOrDefault(name, false);
    }

    public void setGlobalObject(String name, Object obj) {
        globalIsObject.put(name, true);
        if (obj == null) {
            globalObjects.remove(name);
        } else {
            globalObjects.put(name, obj);
        }
    }

    public Object getGlobalObject(String name) {
        return globalObjects.get(name);
    }
    
    // Printing support
    public interface PrintListener {
        void onPrint(String text);
    }
    private final List<PrintListener> printListeners = new ArrayList<>();
    
    // HID support
    private final List<org.chuck.hid.Hid> openHidDevices = new java.util.concurrent.CopyOnWriteArrayList<>();

    public int getActiveShredCount() {
        return activeShreds.size();
    }

    public boolean shredExists(int id) {
        return activeShreds.containsKey(id);
    }

    public void registerHid(org.chuck.hid.Hid hid) {
        openHidDevices.add(hid);
    }

    public void dispatchHidMsg(org.chuck.hid.HidMsg msg) {
        String type = msg.type == 3 || msg.type == 4 ? "mouse" : "keyboard";
        for (org.chuck.hid.Hid hid : openHidDevices) {
            if (hid.getDeviceType().equals(type)) {
                hid.pushMsg(msg);
                hid.broadcast(this);
            }
        }
    }

    public void addPrintListener(PrintListener listener) {
        printListeners.add(listener);
    }

    public void print(String text) {
        String msg = text.endsWith("\n") ? text.substring(0, text.length() - 1) : text;
        for (PrintListener listener : printListeners) {
            listener.onPrint(msg);
        }
        // Also print to stdout by default
    }

    public long getCurrentTime() {
        return currentTime.get();
    }
    
    public int getSampleRate() {
        return sampleRate;
    }

    /**
     * Schedules a shred to wake up based on its wakeTime.
     */
    public void schedule(ChuckShred shred) {
        schedulerLock.lock();
        try {
            shreduler.offer(shred);
        } finally {
            schedulerLock.unlock();
        }
    }

    /**
     * Spawns a new Java-based shred from a Runnable.
     */
    public int spork(Runnable task) {
        ChuckShred shred = new ChuckShred(null); // No bytecode for Java shreds
        schedulerLock.lock();
        try {
            shred.setWakeTime(currentTime.get());
            shreduler.offer(shred);
            activeShreds.put(shred.getId(), shred);
        } finally {
            schedulerLock.unlock();
        }

        Thread.ofVirtual().name("JavaShred-" + shred.getId()).start(() -> {
            ScopedValue.where(ChuckVM.CURRENT_VM, this)
                       .where(ChuckShred.CURRENT_SHRED, shred)
                       .run(() -> {
                try {
                    // Java shreds are expected to block using ChuckDSL.advance()
                    task.run();
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    shred.cleanup();
                    activeShreds.remove(shred.getId());
                }
            });
        });
        return shred.getId();
    }

    /**
     * Spawns a new shred and starts its Virtual Thread.
     */
    public int spork(ChuckShred shred) {
        schedulerLock.lock();
        boolean doLog;
        try {
            doLog = !activeShreds.isEmpty();
            // If sporking while other shreds are running (e.g. Machine.eval/add),
            // schedule for next sample to avoid processing in the same runShredsAt call.
            long wt = doLog ? currentTime.get() + 1 : currentTime.get();
            shred.setWakeTime(wt);
            shreduler.offer(shred);
            activeShreds.put(shred.getId(), shred);
        } finally {
            schedulerLock.unlock();
        }
        if (doLog) {
            ChuckCode c = shred.getCode();
            String fname = "shred";
            if (c != null && c.getName() != null) {
                String n = c.getName().replace('\\', '/');
                int slash = n.lastIndexOf('/');
                fname = slash >= 0 ? n.substring(slash + 1) : n;
            }
            print("[chuck]: (VM) sporking incoming shred: " + shred.getId() + " (" + fname + ")...\n");
        }
        
        Thread.ofVirtual().name("Shred-" + shred.getId()).start(() -> {
            ScopedValue.where(ChuckVM.CURRENT_VM, this)
                       .where(ChuckShred.CURRENT_SHRED, shred)
                       .run(() -> {
                try {
                    shred.execute(this);
                } catch (Throwable t) {
                    t.printStackTrace();
                } finally {
                    shred.cleanup();
                    activeShreds.remove(shred.getId());
                }
            });
        });
        return shred.getId();
    }

    public int add(String pathWithArgs) {
        try {
            // Support "path/to/file.ck:arg0:arg1" format (ChucK Machine.add convention)
            String filePath = pathWithArgs;
            String[] shredArgs = new String[0];
            int colonIdx = pathWithArgs.indexOf(':');
            if (colonIdx >= 0) {
                filePath = pathWithArgs.substring(0, colonIdx);
                String argStr = pathWithArgs.substring(colonIdx + 1);
                shredArgs = argStr.isEmpty() ? new String[0] : argStr.split(":");
            }

            if (filePath.endsWith(".java")) {
                Runnable task = ChuckDSL.load(java.nio.file.Paths.get(filePath));
                int id = spork(task);
                ChuckShred shred = getShred(id);
                if (shred != null && shredArgs.length > 0) shred.setArgs(shredArgs);
                return id;
            }

            String source = java.nio.file.Files.readString(java.nio.file.Paths.get(filePath));
            int shredId = run(source, filePath);
            if (shredId > 0 && shredArgs.length > 0) {
                ChuckShred shred = getShred(shredId);
                if (shred != null) shred.setArgs(shredArgs);
            }
            return shredId;
        } catch (Exception e) {
            print("Machine.add error: " + e.getMessage());
            return 0;
        }
    }

    /**
     * Compiles and executes a ChucK source string.
     * @param source The ChucK source code
     * @param name A label for this shred (e.g. filename)
     * @return The shred ID
     */
    public int run(String source, String name) {
        try {
            List<org.chuck.compiler.ChuckAST.Stmt> ast = parseSource(source);

            // Resolve an absolute path for the file so imports can be resolved relatively
            String absName;
            try {
                absName = Paths.get(name).toAbsolutePath().normalize().toString();
            } catch (Exception ex) {
                absName = name;
            }

            // Process @import directives before emitting
            Map<String, ChuckCode> fileImportedFunctions = new HashMap<>();
            Set<String> compiledFiles = new HashSet<>();
            LinkedHashSet<String> importStack = new LinkedHashSet<>();
            processImports(ast, absName, importStack, compiledFiles, fileImportedFunctions);
            importedFunctions.putAll(fileImportedFunctions);

            ChuckCode code = emitter.emit(ast, name);
            ChuckShred shred = new ChuckShred(code);
            return spork(shred);
        } catch (Exception e) {
            print("Machine.run error: " + e.getMessage());
            return 0;
        }
    }

    private List<org.chuck.compiler.ChuckAST.Stmt> parseSource(String source) {
        org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(source);
        org.chuck.compiler.ChuckANTLRLexer lexer = new org.chuck.compiler.ChuckANTLRLexer(input);
        org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
        org.chuck.compiler.ChuckANTLRParser parser = new org.chuck.compiler.ChuckANTLRParser(tokens);
        org.chuck.compiler.ChuckASTVisitor visitor = new org.chuck.compiler.ChuckASTVisitor();
        @SuppressWarnings("unchecked")
        List<org.chuck.compiler.ChuckAST.Stmt> result = (List<org.chuck.compiler.ChuckAST.Stmt>) visitor.visit(parser.program());
        return result;
    }

    private String resolveImportPath(String currentFilePath, String importPath) {
        java.nio.file.Path base = Paths.get(currentFilePath).getParent();
        if (base == null) base = Paths.get(".");
        java.nio.file.Path resolved = base.resolve(importPath).normalize();
        String str = resolved.toString();
        // Try as-is, then with .ck extension
        if (Files.exists(Paths.get(str))) return str;
        if (!str.endsWith(".ck")) {
            String withExt = str + ".ck";
            if (Files.exists(Paths.get(withExt))) return withExt;
            return withExt; // return even if not found (will error in caller)
        }
        return str;
    }

    private void processImports(
            List<org.chuck.compiler.ChuckAST.Stmt> ast,
            String filePath,
            LinkedHashSet<String> importStack,
            Set<String> compiledFiles,
            Map<String, ChuckCode> importedFunctions) throws Exception {

        importStack.add(filePath);
        for (org.chuck.compiler.ChuckAST.Stmt stmt : ast) {
            if (stmt instanceof org.chuck.compiler.ChuckAST.ImportStmt imp) {
                String importPath = resolveImportPath(filePath, imp.path());

                // Check file exists
                if (!Files.exists(Paths.get(importPath))) {
                    String shortName = Paths.get(imp.path()).getFileName().toString();
                    if (!shortName.endsWith(".ck")) shortName += ".ck";
                    throw new RuntimeException(Paths.get(filePath).getFileName() + ":"
                            + imp.line() + ":" + imp.column()
                            + ": error: no such file: '" + imp.path() + "'\n"
                            + "[" + imp.line() + "] @import \"" + imp.path() + "\"\n"
                            + "            ^");
                }

                // Check cycle
                if (importStack.contains(importPath)) {
                    String cyclic = Paths.get(importPath).getFileName().toString();
                    StringBuilder msg = new StringBuilder("[chuck]: @import error -- cycle detected:\n");
                    msg.append("[chuck]:  |- '").append(cyclic).append("' is imported from...\n");
                    // Walk the stack backwards to build the chain
                    List<String> chain = new ArrayList<>(importStack);
                    for (int i = chain.size() - 1; i >= 0; i--) {
                        String chainFile = Paths.get(chain.get(i)).getFileName().toString();
                        if (i == 0) {
                            msg.append("[chuck]:  |- '").append(chainFile).append("':[line ").append(imp.line()).append("] (this is the originating file)");
                        } else {
                            msg.append("[chuck]:  |- '").append(chainFile).append("':[line ").append(imp.line()).append("], which is imported from...\n");
                        }
                    }
                    throw new RuntimeException(msg.toString());
                }

                // Skip if already compiled (diamond dependency deduplication)
                if (compiledFiles.contains(importPath)) continue;

                // Read and parse the imported file
                String importSource = Files.readString(Paths.get(importPath));
                List<org.chuck.compiler.ChuckAST.Stmt> importAst = parseSource(importSource);

                // Recursively process its imports first
                processImports(importAst, importPath, importStack, compiledFiles, importedFunctions);

                // Compile to extract class definitions and public operator functions
                org.chuck.compiler.ChuckEmitter importEmitter =
                        new org.chuck.compiler.ChuckEmitter(userClassRegistry, importedFunctions);
                importEmitter.emit(importAst, importPath);

                // Merge class definitions into VM registry
                importEmitter.getUserClassRegistry().forEach((k, v) -> {
                    if (!userClassRegistry.containsKey(k)) userClassRegistry.put(k, v);
                });

                // Collect public operator functions for the importing file
                importedFunctions.putAll(importEmitter.getPublicFunctions());

                compiledFiles.add(importPath);
            }
        }
        importStack.remove(filePath);
    }

    public ChuckShred getShred(int id) {
        return activeShreds.get(id);
    }
    
    public int getNumShreds() {
        return activeShreds.size();
    }
    
    public int[] getActiveShredIds() {
        return activeShreds.keySet().stream().mapToInt(Integer::intValue).toArray();
    }

    public void removeShred(int id) {
        ChuckShred shred = activeShreds.get(id);
        if (shred != null) {
            shred.abort();
            activeShreds.remove(id);
        }
    }

    public int replace(int id, String path) {
        removeShred(id);
        return add(path);
    }

    public void status() {
        StringBuilder sb = new StringBuilder();
        sb.append("\n[chuck]: (VM) status (").append(activeShreds.size()).append(" shreds active):\n");
        sb.append("  | id | name       | wakeTime | args |\n");
        sb.append("  |----|------------|----------|------|\n");
        for (ChuckShred s : activeShreds.values()) {
            String n = s.getName();
            if (n == null || n.isEmpty()) n = "eval";
            else {
                n = n.replace('\\', '/');
                int slash = n.lastIndexOf('/');
                if (slash >= 0) n = n.substring(slash + 1);
            }
            if (n.length() > 10) n = n.substring(0, 10);
            
            sb.append(String.format("  | %2d | %-10s | %8d | %s |\n", 
                s.getId(), n, s.getWakeTime(), String.join(":", s.getArgs())));
        }
        print(sb.toString());
    }

    public int eval(String source) {
        return run(source, "eval");
    }

    public void clear() {
        activeShreds.values().forEach(ChuckShred::abort);
        activeShreds.clear();
        schedulerLock.lock();
        try {
            shreduler.clear();
        } finally {
            schedulerLock.unlock();
        }
        globalObjects.clear();
        globalInts.clear();
        // Disconnect all UGens from the audio graph so sound stops immediately
        for (int i = 0; i < numChannels; i++) dacChannels[i].clearSources();
        blackhole.clearSources();

        // Re-register defaults
        globalObjects.put("dac", dac);
        globalObjects.put("blackhole", blackhole);
        globalObjects.put("adc", adc);
    }
    
    /**
     * Advance logical time by computing audio samples and waking shreds.
     */
    public void advanceTime(int samplesToCompute) {
        // Run any shreds scheduled for the current time before advancing
        runShredsAt(currentTime.get());

        for (int s = 0; s < samplesToCompute; s++) {
            long now = currentTime.incrementAndGet();
            runShredsAt(now);
            
            // DRIVE THE UGEN GRAPH: Pull samples through the dac channels
            for (int i = 0; i < numChannels; i++) {
                dacChannels[i].tick(now);
            }
            // Also tick blackhole
            blackhole.tick(now);
        }
    }

    private void runShredsAt(long now) {
        int loopGuard = 0;
        while (true) {
            ChuckShred nextShred = null;
            
            schedulerLock.lock();
            try {
                if (!shreduler.isEmpty() && shreduler.peek().getWakeTime() <= now) {
                    nextShred = shreduler.poll();
                }
            } finally {
                schedulerLock.unlock();
            }

            if (nextShred == null) break;
            if (++loopGuard > 10000) {
                break;
            }

            nextShred.resume(this);
            
            if (!nextShred.isDone() && !nextShred.isWaiting()) {
                schedule(nextShred);
            }
        }
    }
}
