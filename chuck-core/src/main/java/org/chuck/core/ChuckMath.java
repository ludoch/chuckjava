package org.chuck.core;

/**
 * Bridge for ChucK's Math module.
 * Delegates to java.lang.Math and Std where appropriate.
 */
public class ChuckMath {
  public static final double PI = java.lang.Math.PI;
  public static double sgn(double v) { return Std.sgn(v); }
  public static double fabs(double v) { return java.lang.Math.abs(v); }
  public static double abs(double v) { return java.lang.Math.abs(v); }
  public static long abs(long v) { return java.lang.Math.abs(v); }
  
  public static double pow(double a, double b) { return java.lang.Math.pow(a, b); }
  public static double sqrt(double a) { return java.lang.Math.sqrt(a); }
  public static double sin(double a) { return java.lang.Math.sin(a); }
  public static double cos(double a) { return java.lang.Math.cos(a); }
  public static double tan(double a) { return java.lang.Math.tan(a); }
  public static double asin(double a) { return java.lang.Math.asin(a); }
  public static double acos(double a) { return java.lang.Math.acos(a); }
  public static double atan(double a) { return java.lang.Math.atan(a); }
  public static double atan2(double y, double x) { return java.lang.Math.atan2(y, x); }
  public static double log(double a) { return java.lang.Math.log(a); }
  public static double log10(double a) { return java.lang.Math.log10(a); }
  public static double exp(double a) { return java.lang.Math.exp(a); }
  public static double floor(double a) { return java.lang.Math.floor(a); }
  public static double ceil(double a) { return java.lang.Math.ceil(a); }
  public static long round(double a) { return java.lang.Math.round(a); }
  public static double min(double a, double b) { return java.lang.Math.min(a, b); }
  public static double max(double a, double b) { return java.lang.Math.max(a, b); }
  public static long min(long a, long b) { return java.lang.Math.min(a, b); }
  public static long max(long a, long b) { return java.lang.Math.max(a, b); }

  public static long random() { return Std.rand(); }
  public static double randomf() { return Std.randf(); }
  public static long random2(long min, long max) { return Std.rand2(min, max); }
  public static double random2f(double min, double max) { return Std.rand2f(min, max); }
}
