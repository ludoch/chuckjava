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

  /** Raw-long array storage for int/float fields (zero Long boxing). */
  private long[] primitiveSlotValues = new long[8];

  private final Map<String, Integer> primitiveSlotMap = new LinkedHashMap<>();

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
      if (fieldDefs.size() > primitiveSlotValues.length) {
        primitiveSlotValues = new long[fieldDefs.size() + 4];
      }
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
        int slot = primitiveSlotMap.size();
        primitiveSlotMap.put(f[1], slot);
        primitiveSlotValues[slot] = initVal;
        if (isFloat) floatFields.add(f[1]);
      }
    }
  }

  public long getPrimitiveField(String name) {
    Integer slot = primitiveSlotMap.get(name);
    if (slot != null && slot < primitiveSlotValues.length) {
      return primitiveSlotValues[slot];
    }
    return 0L;
  }

  public void setPrimitiveField(String name, long value) {
    Integer slot = primitiveSlotMap.get(name);
    if (slot != null && slot < primitiveSlotValues.length) {
      primitiveSlotValues[slot] = value;
      return;
    }
    int newSlot = primitiveSlotMap.size();
    if (newSlot >= primitiveSlotValues.length) {
      long[] next = new long[Math.max(primitiveSlotValues.length * 2, newSlot + 4)];
      System.arraycopy(primitiveSlotValues, 0, next, 0, primitiveSlotValues.length);
      primitiveSlotValues = next;
    }
    primitiveSlotMap.put(name, newSlot);
    primitiveSlotValues[newSlot] = value;
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
