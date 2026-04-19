package org.chuck.core;

/**
 * Static helper for ChucK-style machine and global access in the Java DSL. These methods utilize
 * ScopedValues to access the current VM context.
 */
public class Machine {

  public static int loglevel() {
    return ChuckVM.CURRENT_VM.get().getLogLevel();
  }

  public static void setLogLevel(int level) {
    ChuckVM.CURRENT_VM.get().setLogLevel(level);
  }

  public static Object getGlobalObject(String name) {
    return ChuckVM.CURRENT_VM.get().getGlobalObject(name);
  }

  public static long getGlobalInt(String name) {
    return ChuckVM.CURRENT_VM.get().getGlobalInt(name);
  }

  public static double getGlobalFloat(String name) {
    return ChuckVM.CURRENT_VM.get().getGlobalFloat(name);
  }

  public static void setGlobalObject(String name, Object val) {
    ChuckVM.CURRENT_VM.get().setGlobalObject(name, val);
  }

  public static void setGlobalInt(String name, long val) {
    ChuckVM.CURRENT_VM.get().setGlobalInt(name, val);
  }

  public static void setGlobalFloat(String name, double val) {
    ChuckVM.CURRENT_VM.get().setGlobalFloat(name, val);
  }

  public static int spork(Runnable task) {
    return ChuckVM.CURRENT_VM.get().spork(task);
  }

  public static int add(String path) {
    return ChuckVM.CURRENT_VM.get().add(path);
  }
}
