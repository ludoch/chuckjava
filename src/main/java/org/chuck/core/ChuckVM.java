package org.chuck.core;

import org.chuck.audio.Adc;
import org.chuck.audio.ChuckUGen;
import org.chuck.audio.Blackhole;
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
    
    // Global logical time in samples
    private final AtomicLong currentTime = new AtomicLong(0);
    
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
    private final Map<String, UserClassDescriptor> userClassRegistry = new ConcurrentHashMap<>();

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

        // Initialize dac channels as virtual summing nodes
        for (int i = 0; i < numChannels; i++) {
            final int channelIndex = i;
            dacChannels[i] = new ChuckUGen() {
                @Override protected float compute(float input) { return input; }
                @Override public float tick(long systemTime) {
                    if (systemTime != -1 && systemTime == lastTickTime) {
                        return lastOut;
                    }
                    float sum = 0.0f;
                    for (ChuckUGen src : sources) {
                        // Crucially, tick the source once per sample (the first dac channel handles this)
                        // Subsequent channels will use the cached value.
                        src.tick(systemTime);
                        if (src instanceof org.chuck.audio.StereoUGen s) {
                            sum += (channelIndex == 0) ? s.getLastOutLeft() : s.getLastOutRight();
                        } else {
                            sum += src.getLastOut();
                        }
                    }
                    lastOut = compute(sum) * gain;
                    lastTickTime = systemTime;
                    return lastOut;
                }
            };
        }

        this.dac = new ChuckObject(new ChuckType("dac", ChuckType.OBJECT, 0, 0));
        globalObjects.put("dac", dac);

        this.blackhole = new Blackhole();
        globalObjects.put("blackhole", blackhole);

        this.adc = new Adc();
        globalObjects.put("adc", adc);

        globalObjects.put("chout", new ChuckIO(System.out, this));
        globalObjects.put("cherr", new ChuckIO(System.err, this));
        globalObjects.put("IO", new ChuckIO(System.out, this));
    }

    public ChuckUGen getDacChannel(int channel) {
        return dacChannels[channel % numChannels];
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

    private boolean antlrEnabled = false;

    public void setAntlrEnabled(boolean enabled) {
        this.antlrEnabled = enabled;
    }

    public boolean isAntlrEnabled() {
        return antlrEnabled;
    }

    public int getActiveShredCount() {
        return activeShreds.size();
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
        for (PrintListener listener : printListeners) {
            listener.onPrint(text);
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
     * Spawns a new shred and starts its Virtual Thread.
     */
    public int spork(ChuckShred shred) {
        schedulerLock.lock();
        try {
            shred.setWakeTime(currentTime.get());
            shreduler.offer(shred);
            activeShreds.put(shred.getId(), shred);
        } finally {
            schedulerLock.unlock();
        }
        
        Thread.ofVirtual().name("Shred-" + shred.getId()).start(() -> {
            try {
                shred.execute(this);
            } catch (Throwable t) {
                print("Error: Shred-" + shred.getId() + " (" + shred.getName() + ") crashed: " + t.getMessage());
                t.printStackTrace();
            } finally {
                shred.cleanup();
                activeShreds.remove(shred.getId());
            }
        });
        return shred.getId();
    }

    public int add(String path) {
        try {
            String source = java.nio.file.Files.readString(java.nio.file.Paths.get(path));
            return run(source, path);
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
            Map<String, ChuckCode> importedFunctions = new HashMap<>();
            Set<String> compiledFiles = new HashSet<>();
            LinkedHashSet<String> importStack = new LinkedHashSet<>();
            processImports(ast, absName, importStack, compiledFiles, importedFunctions);

            org.chuck.compiler.ChuckEmitter emitter = new org.chuck.compiler.ChuckEmitter(userClassRegistry, importedFunctions);
            ChuckCode code = emitter.emit(ast, name);
            ChuckShred shred = new ChuckShred(code);
            return spork(shred);
        } catch (Exception e) {
            print("Machine.run error: " + e.getMessage());
            return 0;
        }
    }

    private List<org.chuck.compiler.ChuckAST.Stmt> parseSource(String source) {
        if (antlrEnabled) {
            org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(source);
            org.chuck.compiler.ChuckANTLRLexer lexer = new org.chuck.compiler.ChuckANTLRLexer(input);
            org.antlr.v4.runtime.CommonTokenStream tokens = new org.antlr.v4.runtime.CommonTokenStream(lexer);
            org.chuck.compiler.ChuckANTLRParser parser = new org.chuck.compiler.ChuckANTLRParser(tokens);
            org.chuck.compiler.ChuckASTVisitor visitor = new org.chuck.compiler.ChuckASTVisitor();
            return (List<org.chuck.compiler.ChuckAST.Stmt>) visitor.visit(parser.program());
        } else {
            org.chuck.compiler.ChuckLexer lexer = new org.chuck.compiler.ChuckLexer(source);
            org.chuck.compiler.ChuckParser parser = new org.chuck.compiler.ChuckParser(lexer.tokenize());
            return parser.parse();
        }
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

    public void removeShred(int id) {
        ChuckShred shred = activeShreds.get(id);
        if (shred != null) {
            shred.abort();
            activeShreds.remove(id);
        }
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
        // First, run any shreds scheduled for NOW (handles yield behavior)
        runShredsAt(currentTime.get());

        // Then, advance time and compute samples
        for (int s = 0; s < samplesToCompute; s++) {
            currentTime.incrementAndGet();
            long now = currentTime.get();
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
                System.err.println("[ChucK] Infinite loop detected at time " + now + " - forcing sample advance");
                break;
            }

            nextShred.resume(this);
            
            if (!nextShred.isDone() && !nextShred.isWaiting()) {
                schedule(nextShred);
            }
        }
    }
}
