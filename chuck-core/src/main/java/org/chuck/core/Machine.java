package org.chuck.core;

import java.util.List;

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

  // --- Global Variable Access ---

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

  // --- Shred Management ---

  public static int spork(Runnable task) {
    return ChuckVM.CURRENT_VM.get().spork(task);
  }

  public static int add(String path) {
    return ChuckVM.CURRENT_VM.get().add(path);
  }

  public static int eval(String code) {
    return ChuckVM.CURRENT_VM.get().eval(code);
  }

  public static int replace(int id, String path) {
    return ChuckVM.CURRENT_VM.get().replace(id, path);
  }

  public static void remove(int id) {
    ChuckVM.CURRENT_VM.get().removeShred(id);
  }

  public static void removeAllShreds() {
    ChuckVM.CURRENT_VM.get().clear();
  }

  public static boolean status() {
    return true; // Stub
  }

  public static boolean realtime() {
    return true;
  }

  public static boolean silent() {
    return false;
  }

  public static double jitter() {
    return ChuckVM.CURRENT_VM.get().getAverageJitter();
  }

  public static double maxJitter() {
    return (double) ChuckVM.CURRENT_VM.get().getMaxJitter();
  }

  public static boolean shredExists(int id) {
    return ChuckVM.CURRENT_VM.get().shredExists(id);
  }

  public static boolean shredExists(long id) {
    return shredExists((int) id);
  }

  public static int numShreds() {
    return ChuckVM.CURRENT_VM.get().getNumShreds();
  }

  public static ChuckArray shreds() {
    List<ChuckShred> all = ChuckVM.CURRENT_VM.get().getAllShreds();
    long[] ids = new long[all.size()];
    for (int i = 0; i < all.size(); i++) ids[i] = all.get(i).id();
    return new ChuckArray("int", ids);
  }

  public static void help() {
    System.out.println("Machine commands:");
    System.out.println("Machine.add(\"path.ck\") - add a shred");
  }

  public static int intsize() {
    return 64;
  }

  public static String version() {
    return ChuckVM.CURRENT_VM.get().getVersion();
  }

  public static void crash() {
    System.exit(1);
  }
}
