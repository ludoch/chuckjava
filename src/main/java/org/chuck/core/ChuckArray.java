package org.chuck.core;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Represents an array in ChucK. ChucK arrays can be both indexed (int) and associative (string).
 */
public class ChuckArray extends ChuckObject {
  private final List<Long> intData = new ArrayList<>();
  private final List<Double> floatData = new ArrayList<>();
  private final List<Object> objectData = new ArrayList<>();
  private final List<Byte> types = new ArrayList<>(); // 0=int, 1=float, 2=obj
  private final Map<String, Long> assocInt = new HashMap<>();
  private final Map<String, Double> assocFloat = new HashMap<>();
  private final Map<String, Object> assocObject = new HashMap<>();

  // Vector tag (e.g. "vec3", "complex", "polar") for formatting
  public String vecTag = null;
  public String elementTypeName = null;

  public ChuckArray(ChuckType type) {
    super(type);
  }

  public ChuckArray(ChuckType type, int size) {
    super(type);
    for (int i = 0; i < size; i++) {
      intData.add(0L);
      floatData.add(0.0);
      objectData.add(null);
      types.add((byte) 1); // default to float/double for vec types
    }
  }

  public ChuckArray(String elementTypeName, int size) {
    super(ChuckType.ARRAY);
    this.elementTypeName = elementTypeName;
    this.vecTag =
        (elementTypeName != null
                && (elementTypeName.startsWith("vec")
                    || elementTypeName.equals("complex")
                    || elementTypeName.equals("polar")))
            ? elementTypeName
            : null;
    if (size > 0) ensureCapacity(size - 1);
  }

  public ChuckArray(String tag, double[] vals) {
    super(ChuckType.ARRAY);
    this.vecTag = tag;
    this.elementTypeName = tag;
    for (double v : vals) {
      floatData.add(v);
      intData.add(0L);
      objectData.add(null);
      types.add((byte) 1);
    }
  }

  private void ensureCapacity(int index) {
    while (types.size() <= index) {
      intData.add(0L);
      floatData.add(0.0);
      if (elementTypeName != null
          && (elementTypeName.equals("complex") || elementTypeName.equals("polar"))) {
        objectData.add(new ChuckArray(elementTypeName, new double[] {0, 0}));
        types.add((byte) 2);
      } else {
        objectData.add(null);
        byte t = (byte) ("float".equals(elementTypeName) ? 1 : 0);
        types.add(t);
      }
    }
  }

  public void setInt(int index, long value) {
    ensureCapacity(index);
    intData.set(index, value);
    types.set(index, (byte) 0);
  }

  public void setFloat(int index, double value) {
    ensureCapacity(index);
    floatData.set(index, value);
    types.set(index, (byte) 1);
  }

  public double getFloat(int index) {
    if (index < 0) index = types.size() + index;
    if (index < 0 || index >= types.size()) return 0.0;
    byte t = types.get(index);
    if (t == 1) return floatData.get(index);
    if (t == 0) return (double) intData.get(index);
    return 0.0;
  }

  public long getInt(int index) {
    if (index < 0) index = types.size() + index;
    if (index < 0 || index >= types.size()) return 0L;
    byte t = types.get(index);
    if (t == 0) return intData.get(index);
    if (t == 1) return floatData.get(index).longValue();
    return 0L;
  }

  public boolean isDoubleAt(int index) {
    if (index < 0) index = types.size() + index;
    if (index < 0 || index >= types.size()) return false;
    return types.get(index) == 1;
  }

  public boolean isObjectAt(int index) {
    if (index < 0) index = types.size() + index;
    if (index < 0 || index >= types.size()) return false;
    return types.get(index) == 2;
  }

  public void setObject(int index, Object value) {
    ensureCapacity(index);
    objectData.set(index, value);
    types.set(index, (byte) 2);
  }

  public Object getObject(int index) {
    if (index < 0) index = objectData.size() + index;
    if (index < 0 || index >= objectData.size()) return null;
    return objectData.get(index);
  }

  public void sort() {
    if (types.isEmpty()) return;

    // Create a list of indices and sort them based on the data
    List<Integer> indices = new ArrayList<>();
    for (int i = 0; i < types.size(); i++) indices.add(i);

    indices.sort(
        (i, j) -> {
          byte ti = types.get(i), tj = types.get(j);

          // If both are numeric (0 or 1), compare by double value
          if (ti <= 1 && tj <= 1) {
            return Double.compare(getFloat(i), getFloat(j));
          }

          if (ti != tj) return Byte.compare(ti, tj);

          if (ti == 2) {
            Object oi = objectData.get(i), oj = objectData.get(j);
            if (oi instanceof ChuckString si && oj instanceof ChuckString sj)
              return si.toString().compareTo(sj.toString());
            if (oi instanceof ChuckArray ai && oj instanceof ChuckArray aj) {
              // Sort by magnitude
              double mi, mj;
              if ("polar".equals(ai.vecTag)) mi = ai.getFloat(0);
              else mi = ai.dot(ai);

              if ("polar".equals(aj.vecTag)) mj = aj.getFloat(0);
              else mj = aj.dot(aj);

              if (Math.abs(mi - mj) > 1e-9) return Double.compare(mi, mj);
              // Tie-break by components
              int len = Math.min(ai.size(), aj.size());
              for (int k = 0; k < len; k++) {
                int cmp = Double.compare(ai.getFloat(k), aj.getFloat(k));
                if (cmp != 0) return cmp;
              }
              return Integer.compare(ai.size(), aj.size());
            }
            if (oi == null) return (oj == null) ? 0 : -1;
            if (oj == null) return 1;
            return Integer.compare(oi.hashCode(), oj.hashCode());
          }
          return 0;
        });

    // Reorder all parallel lists
    List<Byte> newTypes = new ArrayList<>();
    List<Long> newIntData = new ArrayList<>();
    List<Double> newFloatData = new ArrayList<>();
    List<Object> newObjectData = new ArrayList<>();

    for (int idx : indices) {
      newTypes.add(types.get(idx));
      newIntData.add(intData.get(idx));
      newFloatData.add(floatData.get(idx));
      newObjectData.add(objectData.get(idx));
    }

    types.clear();
    types.addAll(newTypes);
    intData.clear();
    intData.addAll(newIntData);
    floatData.clear();
    floatData.addAll(newFloatData);
    objectData.clear();
    objectData.addAll(newObjectData);
  }

  public void reverse() {
    int left = 0, right = types.size() - 1;
    while (left < right) {
      byte tl = types.get(left), tr = types.get(right);
      long il = intData.get(left), ir = intData.get(right);
      double fl = floatData.get(left), fr = floatData.get(right);
      Object ol = objectData.get(left), or = objectData.get(right);

      types.set(left, tr);
      types.set(right, tl);
      intData.set(left, ir);
      intData.set(right, il);
      floatData.set(left, fr);
      floatData.set(right, fl);
      objectData.set(left, or);
      objectData.set(right, ol);

      left++;
      right--;
    }
  }

  public void shuffle() {
    java.util.Random rng = Std.rng;
    for (int i = types.size() - 1; i > 0; i--) {
      int j = rng.nextInt(i + 1);

      byte tj = types.get(j), ti = types.get(i);
      long ij = intData.get(j), ii = intData.get(i);
      double fj = floatData.get(j), fi = floatData.get(i);
      Object oj = objectData.get(j), oi = objectData.get(i);

      types.set(i, tj);
      types.set(j, ti);
      intData.set(i, ij);
      intData.set(j, ii);
      floatData.set(i, fj);
      floatData.set(j, fi);
      objectData.set(i, oj);
      objectData.set(j, oi);
    }
  }

  public void copyFrom(ChuckArray other) {
    if (other == null) {
      clear();
      return;
    }
    clear();
    for (int i = 0; i < other.size(); i++) {
      if (other.isObjectAt(i)) appendObject(other.getObject(i));
      else if (other.isDoubleAt(i)) appendFloat(other.getFloat(i));
      else appendInt(other.getInt(i));
    }
    // Also copy associative maps
    this.assocInt.putAll(other.assocInt);
    this.assocFloat.putAll(other.assocFloat);
    this.assocObject.putAll(other.assocObject);
  }

  public int size() {
    return types.size();
  }

  public int cap() {
    return types.size();
  }

  public int resolveIndex(long idx) {
    int i = (int) idx;
    if (i < 0) i = types.size() + i;
    if (i < 0 || i >= types.size()) {
      throw new org.chuck.core.ChuckRuntimeException(
          "ArrayOutofBounds: index[" + idx + "] size[" + types.size() + "]");
    }
    return i;
  }

  public void popOut(int index) {
    if (index < 0 || index >= types.size()) return;
    intData.remove(index);
    floatData.remove(index);
    objectData.remove(index);
    types.remove(index);
  }

  public void erase(int index) {
    if (index < 0) index = types.size() + index;
    if (index >= 0 && index < types.size()) {
      popOut(index);
    }
  }

  public void erase(int begin, int end) {
    if (begin < 0) begin = 0;
    if (end > types.size()) end = types.size();
    if (begin >= end) return;

    for (int i = 0; i < end - begin; i++) {
      popOut(begin);
    }
  }

  public void appendInt(long val) {
    intData.add(val);
    floatData.add(0.0);
    objectData.add(null);
    types.add((byte) 0);
  }

  public void appendFloat(double val) {
    intData.add(0L);
    floatData.add(val);
    objectData.add(null);
    types.add((byte) 1);
  }

  public void appendObject(Object val) {
    intData.add(0L);
    floatData.add(0.0);
    objectData.add(val);
    types.add((byte) 2);
  }

  // Associative access — all three maps share a single key namespace in ChucK
  public void setAssocInt(String key, long value) {
    assocInt.put(key, value);
    assocFloat.remove(key);
    assocObject.remove(key);
  }

  public long getAssocInt(String key) {
    if (assocFloat.containsKey(key)) return (long) assocFloat.get(key).doubleValue();
    if (assocObject.containsKey(key)) return 0L;
    return assocInt.getOrDefault(key, 0L);
  }

  public void setAssocFloat(String key, double value) {
    assocFloat.put(key, value);
    assocInt.remove(key);
    assocObject.remove(key);
  }

  public double getAssocFloat(String key) {
    if (assocFloat.containsKey(key)) return assocFloat.get(key);
    if (assocInt.containsKey(key)) return (double) assocInt.get(key);
    return 0.0;
  }

  public void setAssocObject(String key, Object value) {
    assocObject.put(key, value);
    assocInt.remove(key);
    assocFloat.remove(key);
  }

  public Object getAssocObject(String key) {
    return assocObject.get(key);
  }

  /** ChucK API: {@code a.getKeys(outArr)} — fills {@code outArr} with all associative keys. */
  public ChuckArray getKeys(ChuckArray outArr) {
    if (outArr == null) return outArr;
    java.util.Set<String> keys = new java.util.LinkedHashSet<>();
    keys.addAll(assocInt.keySet());
    keys.addAll(assocFloat.keySet());
    keys.addAll(assocObject.keySet());
    for (String k : keys) outArr.appendObject(new ChuckString(k));
    return outArr;
  }

  /** ChucK API: {@code a.isInMap(key)} — returns 1 if key exists, 0 otherwise. */
  public long isInMap(String key) {
    return (assocInt.containsKey(key)
            || assocFloat.containsKey(key)
            || assocObject.containsKey(key))
        ? 1L
        : 0L;
  }

  /** Remove first element. */
  public void popFront() {
    if (size() > 0) popOut(0);
  }

  /** Remove last element. */
  public void popBack() {
    if (size() > 0) popOut(size() - 1);
  }

  public void clear() {
    intData.clear();
    floatData.clear();
    objectData.clear();
    types.clear();
  }

  public void zero() {
    for (int i = 0; i < types.size(); i++) {
      byte t = types.get(i);
      if (t == 0) intData.set(i, 0L);
      else if (t == 1) floatData.set(i, 0.0);
      else if (t == 2) {
        Object o = objectData.get(i);
        if (o instanceof ChuckArray a) a.zero();
        else objectData.set(i, null);
      }
    }
  }

  // append (<<) support
  public ChuckArray append(long val) {
    if ("float".equals(elementTypeName)) return append((double) val);
    int idx = types.size();
    ensureCapacity(idx);
    intData.set(idx, val);
    types.set(idx, (byte) 0);
    return this;
  }

  public ChuckArray append(double val) {
    if ("int".equals(elementTypeName)) return append((long) val);
    int idx = types.size();
    ensureCapacity(idx);
    floatData.set(idx, val);
    types.set(idx, (byte) 1);
    return this;
  }

  public ChuckArray append(Object val) {
    if ("float".equals(elementTypeName)) {
      if (val instanceof Number n) return append(n.doubleValue());
    }
    if (val instanceof Long l) return append(l.longValue());
    if (val instanceof Double d) return append(d.doubleValue());
    if (val instanceof Integer i) return append((long) i);
    if (val instanceof Float f) return append((double) f);

    int idx = types.size();
    ensureCapacity(idx);
    objectData.set(idx, val);
    types.set(idx, (byte) 2);
    return this;
  }

  // ChucK .size()
  public int size(int s) {
    if (s < 0) s = 0;
    while (size() > s) popOut(size() - 1);
    while (size() < s) ensureCapacity(s - 1);
    return size();
  }

  @Override
  public String toString() {
    if ("complex".equals(vecTag)) {
      return String.format("#(%.6f,%.6f)", getFloat(0), getFloat(1));
    }
    if ("polar".equals(vecTag)) {
      return String.format("%%(%.6f,%.6f*pi)", getFloat(0), getFloat(1) / Math.PI);
    }
    if (vecTag != null && vecTag.startsWith("vec")) {
      StringBuilder sb = new StringBuilder("@(");
      for (int i = 0; i < size(); i++) {
        sb.append(String.format("%.6f", getFloat(i)));
        if (i < size() - 1) sb.append(",");
      }
      sb.append(")");
      return sb.toString();
    }
    StringBuilder sb = new StringBuilder("[");
    for (int i = 0; i < size(); i++) {
      if (isObjectAt(i)) sb.append(getObject(i));
      else if (isDoubleAt(i)) sb.append(String.format("%.6f", getFloat(i)));
      else sb.append(getInt(i));
      if (i < size() - 1) sb.append(",");
    }
    sb.append("]");
    return sb.toString();
  }

  public double dot(ChuckArray other) {
    double sum = 0;
    int len = Math.min(size(), other.size());
    for (int i = 0; i < len; i++) {
      sum += getFloat(i) * other.getFloat(i);
    }
    return sum;
  }

  public double euclideanDistance(ChuckArray other) {
    double sumSq = 0;
    int len = Math.min(size(), other.size());
    for (int i = 0; i < len; i++) {
      double diff = getFloat(i) - other.getFloat(i);
      sumSq += diff * diff;
    }
    return Math.sqrt(sumSq);
  }

  /** Negate all components, returns new array. */
  public ChuckArray negate() {
    ChuckArray result = new ChuckArray(ChuckType.ARRAY, size());
    result.vecTag = vecTag;
    for (int i = 0; i < size(); i++) {
      result.setFloat(i, -getFloat(i));
    }
    return result;
  }

  public ChuckArray cross(ChuckArray other) {
    ChuckArray result = new ChuckArray(ChuckType.ARRAY, 3);
    result.vecTag = "vec3";
    double a1 = getFloat(0), a2 = getFloat(1), a3 = getFloat(2);
    double b1 = other.getFloat(0), b2 = other.getFloat(1), b3 = other.getFloat(2);
    result.setFloat(0, a2 * b3 - a3 * b2);
    result.setFloat(1, a3 * b1 - a1 * b3);
    result.setFloat(2, a1 * b2 - a2 * b1);
    return result;
  }
}
