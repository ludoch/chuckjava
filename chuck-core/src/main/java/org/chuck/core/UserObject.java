package org.chuck.core;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.chuck.audio.Chugen;

/**
 * Runtime instance of a user-defined ChucK class. Extends Chugen so all user objects can be Unit
 * Generators.
 */
public class UserObject extends Chugen {
  public final String className;

  /** Non-null if this class (or an ancestor) extends the built-in Event type. */
  public final ChuckEvent eventDelegate;

  /** Raw-long storage for int/float fields (floats stored as Double.doubleToRawLongBits). */
  private final Map<String, Long> primitiveFields = new LinkedHashMap<>();

  /** Names of fields declared as float/double. */
  private final java.util.Set<String> floatFields = new java.util.HashSet<>();

  /** Object-typed fields. */
  private final Map<String, ChuckObject> objectFields = new LinkedHashMap<>();

  /** Compiled method bodies, shared across all instances of this class. */
  public final Map<String, ChuckCode> methods;

  public UserObject(
      String className,
      List<String[]> fieldDefs,
      Map<String, ChuckCode> methods,
      boolean extendsEvent) {
    super(new ChuckType(className, ChuckType.OBJECT, 0, 0));
    this.className = className;
    this.methods = methods != null ? methods : new java.util.HashMap<>();
    this.eventDelegate = extendsEvent ? new ChuckEvent() : null;

    if (fieldDefs != null) {
      for (String[] f : fieldDefs) {
        boolean isFloat = f.length > 0 && ("float".equals(f[0]) || "double".equals(f[0]));
        long initVal;
        if (f.length > 2 && f[2] != null) {
          try {
            initVal =
                isFloat
                    ? Double.doubleToRawLongBits(Double.parseDouble(f[2]))
                    : Long.parseLong(f[2]);
          } catch (NumberFormatException e) {
            initVal = isFloat ? Double.doubleToRawLongBits(0.0) : 0L;
          }
        } else {
          initVal = isFloat ? Double.doubleToRawLongBits(0.0) : 0L;
        }
        primitiveFields.put(f[1], initVal);
        if (isFloat) floatFields.add(f[1]);
      }
    }
  }

  public long getPrimitiveField(String name) {
    return primitiveFields.getOrDefault(name, 0L);
  }

  public void setPrimitiveField(String name, long value) {
    primitiveFields.put(name, value);
  }

  public double getFloatField(String name) {
    return Double.longBitsToDouble(getPrimitiveField(name));
  }

  public void setFloatField(String name, double value) {
    setPrimitiveField(name, Double.doubleToRawLongBits(value));
  }

  public boolean isFloatField(String name) {
    return floatFields.contains(name);
  }

  public ChuckObject getObjectField(String name) {
    return objectFields.get(name);
  }

  public boolean hasObjectField(String name) {
    return objectFields.containsKey(name);
  }

  public void setObjectField(String name, ChuckObject value) {
    objectFields.put(name, value);
  }

  public ChuckCode getMethod(String name) {
    return methods.get(name);
  }

  @Override
  public String toString() {
    return className + "@" + Integer.toHexString(hashCode());
  }
}
