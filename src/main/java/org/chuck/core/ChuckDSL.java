package org.chuck.core;

import java.lang.reflect.InvocationTargetException;

import org.chuck.audio.ChuckUGen;

/**
 * Fluent Java DSL for ChucK-Java.
 * Leveraging ScopedValues (JEP 481) for shred-local logical time.
 */
public class ChuckDSL {
    
    /** Implicit access to the DAC via ScopedValue context. */
    public static ChuckUGen dac() { return ChuckVM.CURRENT_VM.get().getMultiChannelDac(); }
    
    /** Implicit access to the ADC via ScopedValue context. */
    public static ChuckUGen adc() { return ChuckVM.CURRENT_VM.get().adc; }
    
    /** Implicit access to the Blackhole via ScopedValue context. */
    public static ChuckUGen blackhole() { return ChuckVM.CURRENT_VM.get().blackhole; }

    /** Returns the current logical time. */
    public static long now() { return ChuckVM.CURRENT_VM.get().getCurrentTime(); }

    /** Returns the VM sample rate. */
    public static int sampleRate() { return ChuckVM.CURRENT_VM.get().getSampleRate(); }

    /**
     * Advances time for the current shred.
     * Equivalent to: duration => now;
     */
    public static void advance(ChuckDuration duration) {
        ChuckShred current = ChuckShred.CURRENT_SHRED.get();
        ChuckVM vm = ChuckVM.CURRENT_VM.get();
        if (current != null && vm != null) {
            current.yield(Math.round(duration.samples()));
        }
    }

    /** 1 sample duration. */
    public static ChuckDuration samp() { return ChuckDuration.of(1); }
    public static ChuckDuration samp(double n) { return ChuckDuration.of(n); }

    /** ms duration. */
    public static ChuckDuration ms() { return ms(1); }
    public static ChuckDuration ms(double value) {
        return ChuckDuration.fromMs(value, sampleRate());
    }

    /** second duration. */
    public static ChuckDuration second() { return second(1); }
    public static ChuckDuration second(double value) {
        return ChuckDuration.fromSeconds(value, sampleRate());
    }

    /** Helper to start a chain. returns the UGen. */
    public static <T extends ChuckUGen> T chuck(T ugen) {
        return ugen;
    }

    /**
     * Compiles a Java DSL file into a Runnable that can be sporked.
     * The class in the file must implement org.chuck.core.Shred or have a shred() method.
     */
    @SuppressWarnings("CallToPrintStackTrace")
    public static Runnable load(java.nio.file.Path path) throws Exception {
        var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new RuntimeException("JDK Compiler not found. Ensure you are running on a full JDK.");
        
        var fileManager = compiler.getStandardFileManager(null, null, null);
        var compilationUnits = fileManager.getJavaFileObjects(path);
        
        java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("chuck_java_dsl");
        // Use current classpath to ensure the core library is available during compilation
        String classpath = System.getProperty("java.class.path");
        var options = java.util.List.of("-d", tempDir.toString(), "-cp", classpath, "--enable-preview", "--release", "25");
        
        var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
        if (!task.call()) {
            throw new RuntimeException("Compilation failed for " + path.getFileName());
        }
        
        String fileName = path.getFileName().toString();
        String className = fileName.substring(0, fileName.lastIndexOf('.'));
        
        // Use the same class loader parent to ensure ScopedValues and static dac() etc. are shared
        var loader = new java.net.URLClassLoader(
            new java.net.URL[]{tempDir.toUri().toURL()}, 
            ChuckDSL.class.getClassLoader()
        ) {
            @Override
            protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
                // Force loading of core ChucK classes from the parent loader
                if (name.startsWith("org.chuck.core.") || name.startsWith("org.chuck.audio.")) {
                    return ChuckDSL.class.getClassLoader().loadClass(name);
                }
                return super.loadClass(name, resolve);
            }
        };
        Class<?> clazz = loader.loadClass(className);
        
        return () -> {
            try {
                // Ensure we are inside the ScopedValue context when instantiating and running
                Object instance = clazz.getDeclaredConstructor().newInstance();
                if (instance instanceof Shred s) {
                    s.shred();
                } else {
                    var method = clazz.getMethod("shred");
                    method.invoke(instance);
                }
            } catch (IllegalAccessException | IllegalArgumentException | InstantiationException | NoSuchMethodException | InvocationTargetException e) {
                System.err.println("Runtime error in Java Shred: " + className);
                ChuckVM vm = ChuckVM.CURRENT_VM.get();
                if (vm != null && vm.getLogLevel() >= 2) {
                    e.printStackTrace();
                }
            }
        };
    }
}
