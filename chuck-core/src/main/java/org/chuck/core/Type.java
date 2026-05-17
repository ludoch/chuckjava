package org.chuck.core;

/** ChucK Type utility class. */
public class Type extends ChuckObject {
  public static final int PRIMITIVE = 1;
  public static final int BUILTIN = 2;
  public static final int OBJECT = 3;
  public static final int CHUGIN = 4;

  public Type() {
    super(ChuckType.OBJECT);
  }

  public static ChuckArray getTypes(int category, int source) {
    return new ChuckArray("string", new String[0]);
  }
}
