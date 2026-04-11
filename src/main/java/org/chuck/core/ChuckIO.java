package org.chuck.core;

import java.io.PrintStream;

/** Represents an IO stream in ChucK (chout, cherr). */
public class ChuckIO extends ChuckObject {
  // Binary format constants (also accessible as IO.INT16 etc.)
  public static final int INT8 = 1;
  public static final int INT16 = 2;
  public static final int INT32 = 4;
  public static final int INT64 = 8;
  public static final int FLOAT32 = 16;
  public static final int FLOAT64 = 32;

  private final PrintStream stream;
  private ChuckVM vm;

  public ChuckIO(PrintStream stream, ChuckVM vm) {
    super(new ChuckType("IO", ChuckType.OBJECT, 0, 0));
    this.stream = stream;
    this.vm = vm;
  }

  // Overloaded write methods for <= operator
  public ChuckIO write(String s) {
    vm.print(s);
    return this;
  }

  public ChuckIO write(long l) {
    vm.print(String.valueOf(l));
    return this;
  }

  public ChuckIO write(double d) {
    String s = java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString();
    // Handle case where stripTrailingZeros leaves a .0
    if (s.endsWith(".0")) s = s.substring(0, s.length() - 2);
    vm.print(s);
    return this;
  }

  public ChuckIO write(Object o) {
    if (o instanceof Double d) return write(d.doubleValue());
    if (o instanceof Long l) return write(l.longValue());
    if (o instanceof ChuckString cs) return write(cs.toString());
    vm.print(String.valueOf(o));
    return this;
  }

  // Static helper for IO.newline()
  public static String newline() {
    return "\n";
  }
}
