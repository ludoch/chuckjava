package org.chuck.core;

/**
 * ChuckFunction — runtime representation of a ChucK function/method. Provides introspection API:
 * name(), numArgs(), returnType().
 */
public class ChuckFunction extends ChuckObject {
  private final String name;
  private final int numArgs;
  private final String returnType;

  public ChuckFunction(String name, int numArgs, String returnType) {
    super(ChuckType.OBJECT);
    this.name = name;
    this.numArgs = numArgs;
    this.returnType = returnType;
  }

  public ChuckFunction(ChuckCode code) {
    this(code.getName(), code.getNumArgs(), code.getReturnType());
  }

  /** Returns the name of the function. */
  public ChuckString name() {
    return new ChuckString(name);
  }

  /** Returns the number of arguments. */
  public long numArgs() {
    return (long) numArgs;
  }

  /** Returns the return type as a string. */
  public ChuckString returnType() {
    return new ChuckString(returnType);
  }

  @Override
  public String toString() {
    return "fun " + returnType + " " + name + "(" + numArgs + " args)";
  }
}
