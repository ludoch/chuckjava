package org.chuck.core;

import java.lang.reflect.InvocationTargetException;
import org.chuck.audio.ChuckUGen;

/**
 * Fluent Java DSL for ChucK-Java. Leveraging ScopedValues (JEP 481) for shred-local logical time.
 */
public class ChuckDSL {

  /** Implicit access to the DAC via ScopedValue context. */
  public static ChuckUGen dac() {
    return ChuckVM.CURRENT_VM.get().getMultiChannelDac();
  }

  /** Implicit access to the ADC via ScopedValue context. */
  public static ChuckUGen adc() {
    return ChuckVM.CURRENT_VM.get().adc;
  }

  /** Implicit access to the Blackhole via ScopedValue context. */
  public static ChuckUGen blackhole() {
    return ChuckVM.CURRENT_VM.get().blackhole;
  }

  /** Returns the current logical time. */
  public static long now() {
    return ChuckVM.CURRENT_VM.get().getCurrentTime();
  }

  /** Returns the VM sample rate. */
  public static int sampleRate() {
    return ChuckVM.CURRENT_VM.get().getSampleRate();
  }

  /** Advances time for the current shred. Equivalent to: duration => now; */
  public static void advance(ChuckDuration duration) {
    ChuckShred current = ChuckShred.CURRENT_SHRED.get();
    ChuckVM vm = ChuckVM.CURRENT_VM.get();
    if (current != null && vm != null) {
      current.suspendOnTime(Math.round(duration.samples()));
    }
  }

  /** Waits for an event. Equivalent to: event => now; */
  public static boolean advance(ChuckEvent event) {
    ChuckShred current = ChuckShred.CURRENT_SHRED.get();
    ChuckVM vm = ChuckVM.CURRENT_VM.get();
    if (current != null && vm != null) {
      event.waitOn(current, vm);
      return current.wasSignaled();
    }
    return false;
  }

  /** Waits for any event in an array. Equivalent to: event_array => now; */
  public static boolean advance(ChuckEvent[] events) {
    ChuckShred current = ChuckShred.CURRENT_SHRED.get();
    ChuckVM vm = ChuckVM.CURRENT_VM.get();
    if (current != null && vm != null) {
      ChuckEventDisjunction disjunction = new ChuckEventDisjunction();
      for (ChuckEvent e : events) disjunction.addEvent(e);
      disjunction.waitOn(current, vm);
      return current.wasSignaled();
    }
    return false;
  }

  /** 1 sample duration. */
  public static ChuckDuration samp() {
    return ChuckDuration.of(1);
  }

  public static ChuckDuration samp(double n) {
    return ChuckDuration.of(n);
  }

  /** ms duration. */
  public static ChuckDuration ms() {
    return ms(1);
  }

  public static ChuckDuration ms(double value) {
    return ChuckDuration.fromMs(value, sampleRate());
  }

  /** second duration. */
  public static ChuckDuration second() {
    return second(1);
  }

  public static ChuckDuration second(double value) {
    return ChuckDuration.fromSeconds(value, sampleRate());
  }

  /** Helper to start a chain. returns the UGen. */
  public static <T extends ChuckUGen> T chuck(T ugen) {
    return ugen;
  }

  /** MIDI note to Frequency conversion. */
  public static double mtof(double midiNote) {
    return Std.mtof(midiNote);
  }

  /** Creates an event conjunction (wait until ALL trigger). */
  public static ChuckEvent eventAnd(ChuckEvent... events) {
    ChuckEventConjunction conjunction = new ChuckEventConjunction();
    for (ChuckEvent e : events) conjunction.addEvent(e);
    return conjunction;
  }

  /** Creates an event disjunction (wait until ANY trigger). */
  public static ChuckEvent eventOr(ChuckEvent... events) {
    ChuckEventDisjunction disjunction = new ChuckEventDisjunction();
    for (ChuckEvent e : events) disjunction.addEvent(e);
    return disjunction;
  }

  public static long getInt(ChuckArray a, String key) {
    return a.getAssocInt(key);
  }

  public static double getFloat(ChuckArray a, String key) {
    return a.getAssocFloat(key);
  }

  public static Object getObject(ChuckArray a, String key) {
    return a.getAssocObject(key);
  }

  public static long setInt(ChuckArray a, String key, long val) {
    a.setAssocInt(key, val);
    return val;
  }

  public static double setFloat(ChuckArray a, String key, double val) {
    a.setAssocFloat(key, val);
    return val;
  }

  public static Object setObject(ChuckArray a, String key, Object val) {
    a.setAssocObject(key, val);
    return val;
  }

  public static ChuckShred me() {
    return ChuckShred.CURRENT_SHRED.get();
  }

  // String helpers for ChucK parity
  public static String setCharAt(String s, int pos, int c) {
    char[] chars = s.toCharArray();
    if (pos >= 0 && pos < chars.length) chars[pos] = (char) c;
    return new String(chars);
  }

  public static String setCharAt2(String s, int pos, String sub) {
    if (sub == null || sub.isEmpty()) return s;
    return setCharAt(s, pos, sub.charAt(0));
  }

  public static String charAt2(String s, int pos) {
    if (pos < 0 || pos >= s.length()) return "";
    return String.valueOf(s.charAt(pos));
  }

  public static int find(String s, String sub) {
    return s.indexOf(sub);
  }

  public static int find(String s, int c) {
    return s.indexOf(c);
  }

  public static int rfind(String s, String sub) {
    return s.lastIndexOf(sub);
  }

  public static int rfind(String s, int c) {
    return s.lastIndexOf(c);
  }

  public static String lower(String s) {
    return s.toLowerCase();
  }

  public static String upper(String s) {
    return s.toUpperCase();
  }

  public static String ltrim(String s) {
    return s.stripLeading();
  }

  public static String rtrim(String s) {
    return s.stripTrailing();
  }

  public static String trim(String s) {
    return s.trim();
  }

  public static String insert(String s, int pos, String sub) {
    if (pos < 0) pos = 0;
    if (pos > s.length()) pos = s.length();
    return s.substring(0, pos) + sub + s.substring(pos);
  }

  public static String erase(String s, int pos, int len) {
    if (pos < 0) pos = 0;
    if (pos >= s.length()) return s;
    int end = pos + len;
    if (end > s.length()) end = s.length();
    return s.substring(0, pos) + s.substring(end);
  }

  public static String replace(String s, int pos, int len, String sub) {
    return insert(erase(s, pos, len), pos, sub);
  }

  public static String replace(String s, int pos, String sub) {
    return replace(s, pos, sub.length(), sub);
  }

  public static void print(Object msg) {
    ChuckVM.CURRENT_VM.get().print(String.valueOf(msg));
  }

  public static class ChuckIO {
    private final boolean isErr;
    public ChuckIO(boolean isErr) { this.isErr = isErr; }
    public ChuckIO print(Object o) {
      if (isErr) System.err.print(o);
      else System.out.print(o);
      return this;
    }
  }

  public static final ChuckIO chout = new ChuckIO(false);
  public static final ChuckIO cherr = new ChuckIO(true);

  public static void _CHUCK_INTERNAL_ASSERT_(boolean condition, String message) {
    if (!condition) {
      throw new RuntimeException("ChucK Assertion Failed: " + message);
    }
  }

  public static void _CHUCK_INTERNAL_ASSERT_(long condition, String message) {
    _CHUCK_INTERNAL_ASSERT_(condition != 0, message);
  }

  /** Compiles a Java DSL string into a Class that can be instantiated. */
  public static Class<?> compileSource(String source, String className) throws Exception {
    var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
    if (compiler == null)
      throw new RuntimeException("JDK Compiler not found. Ensure you are running on a full JDK.");

    java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("chuck_jit");
    java.nio.file.Path sourceFile = tempDir.resolve(className + ".java");
    java.nio.file.Files.writeString(sourceFile, source);

    var fileManager = compiler.getStandardFileManager(null, null, null);
    var compilationUnits = fileManager.getJavaFileObjects(sourceFile);

    String classpath = System.getProperty("java.class.path");
    var options =
        java.util.List.of(
            "-d", tempDir.toString(), "-cp", classpath, "--enable-preview", "--release", "25");

    var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
    if (!task.call()) {
      throw new RuntimeException("Compilation failed for " + className);
    }

    java.net.URLClassLoader loader =
        new java.net.URLClassLoader(
            new java.net.URL[] {tempDir.toUri().toURL()}, ChuckDSL.class.getClassLoader());
    return loader.loadClass(className);
  }

  /**
   * Compiles a Java DSL file into a Runnable that can be sporked. The class in the file must
   * implement org.chuck.core.Shred or have a shred() method.
   */
  @SuppressWarnings("CallToPrintStackTrace")
  public static Runnable load(java.nio.file.Path path) throws Exception {
    var compiler = javax.tools.ToolProvider.getSystemJavaCompiler();
    if (compiler == null)
      throw new RuntimeException("JDK Compiler not found. Ensure you are running on a full JDK.");

    var fileManager = compiler.getStandardFileManager(null, null, null);
    var compilationUnits = fileManager.getJavaFileObjects(path);

    java.nio.file.Path tempDir = java.nio.file.Files.createTempDirectory("chuck_java_dsl");
    // Use current classpath to ensure the core library is available during compilation
    String classpath = System.getProperty("java.class.path");
    var options =
        java.util.List.of(
            "-d", tempDir.toString(), "-cp", classpath, "--enable-preview", "--release", "25");

    var task = compiler.getTask(null, fileManager, null, options, null, compilationUnits);
    if (!task.call()) {
      throw new RuntimeException("Compilation failed for " + path.getFileName());
    }

    String fileName = path.getFileName().toString();
    String className = fileName.substring(0, fileName.lastIndexOf('.'));

    // Use the same class loader parent to ensure ScopedValues and static dac() etc. are shared
    var loader =
        new java.net.URLClassLoader(
            new java.net.URL[] {tempDir.toUri().toURL()}, ChuckDSL.class.getClassLoader()) {
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
      } catch (IllegalAccessException
          | IllegalArgumentException
          | InstantiationException
          | NoSuchMethodException
          | InvocationTargetException e) {
        System.err.println("Runtime error in Java Shred: " + className);
        ChuckVM vm = ChuckVM.CURRENT_VM.get();
        if (vm != null && vm.getLogLevel() >= 2) {
          e.printStackTrace();
        }
      }
    };
  }
}
