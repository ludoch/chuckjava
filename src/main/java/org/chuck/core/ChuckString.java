package org.chuck.core;

/** A mutable string for ChucK, wrapping StringBuilder. */
public class ChuckString extends ChuckObject {
  private final StringBuilder sb;

  public ChuckString(String initial) {
    super(ChuckType.STRING);
    this.sb = new StringBuilder(initial == null ? "" : initial);
  }

  public long length() {
    return sb.length();
  }

  public long charAt(long i) {
    return (i >= 0 && i < sb.length()) ? sb.charAt((int) i) : 0;
  }

  public void setCharAt(long i, long c) {
    if (i >= 0 && i < sb.length()) sb.setCharAt((int) i, (char) c);
  }

  public ChuckString substring(long start) {
    int s = Math.max(0, Math.min((int) start, sb.length()));
    return new ChuckString(sb.substring(s));
  }

  public ChuckString substring(long start, long len) {
    int s = Math.max(0, Math.min((int) start, sb.length()));
    int e = Math.max(s, Math.min(s + (int) len, sb.length()));
    return new ChuckString(sb.substring(s, e));
  }

  private String valToString(Object val) {
    if (val instanceof Long l) return String.valueOf((char) l.intValue());
    if (val instanceof ChuckString cs) return cs.toString();
    return String.valueOf(val);
  }

  public ChuckString insert(long i, Object val) {
    int idx = Math.max(0, Math.min((int) i, sb.length()));
    sb.insert(idx, valToString(val));
    return this;
  }

  public ChuckString erase(long start, long len) {
    int s = Math.max(0, Math.min((int) start, sb.length()));
    int e = Math.max(s, Math.min(s + (int) len, sb.length()));
    sb.delete(s, e);
    return this;
  }

  /** ChucK 1.5.1.3+: replace(substr, replacement) */
  public ChuckString replace(Object substr, Object replacement) {
    String sub = valToString(substr);
    String rep = valToString(replacement);
    if (sub.isEmpty()) return this;

    int idx = sb.indexOf(sub);
    while (idx != -1) {
      sb.replace(idx, idx + sub.length(), rep);
      idx = sb.indexOf(sub, idx + rep.length());
    }
    return this;
  }

  public ChuckString replace(long start, Object val) {
    int s = Math.max(0, Math.min((int) start, sb.length()));
    String v = valToString(val);
    int e = Math.max(s, Math.min(s + v.length(), sb.length()));
    sb.replace(s, e, v);
    return this;
  }

  public ChuckString replace(long start, long len, Object val) {
    int s = Math.max(0, Math.min((int) start, sb.length()));
    int e = Math.max(s, Math.min(s + (int) len, sb.length()));
    String v = valToString(val);
    sb.replace(s, e, v);
    return this;
  }

  public long find(Object val) {
    return sb.indexOf(valToString(val));
  }

  public long find(Object val, long start) {
    return sb.indexOf(valToString(val), (int) start);
  }

  public long rfind(Object val) {
    return sb.lastIndexOf(valToString(val));
  }

  public long rfind(Object val, long start) {
    return sb.lastIndexOf(valToString(val), (int) start);
  }

  public ChuckString lower() {
    return new ChuckString(sb.toString().toLowerCase());
  }

  public ChuckString upper() {
    return new ChuckString(sb.toString().toUpperCase());
  }

  public ChuckString trim() {
    return new ChuckString(sb.toString().trim());
  }

  public ChuckString ltrim() {
    return new ChuckString(sb.toString().stripLeading());
  }

  public ChuckString rtrim() {
    return new ChuckString(sb.toString().stripTrailing());
  }

  public ChuckString set(Object val) {
    sb.setLength(0);
    sb.append(valToString(val));
    return this;
  }

  public void setValue(String val) {
    sb.setLength(0);
    sb.append(val == null ? "" : val);
  }

  /** Returns the directory path if the string is a file path. */
  public ChuckString path() {
    java.nio.file.Path p = java.nio.file.Paths.get(sb.toString()).getParent();
    String result = (p == null) ? "" : p.toString();
    // Standardize to forward slashes and ensure trailing slash if not empty
    result = result.replace('\\', '/');
    if (!result.isEmpty() && !result.endsWith("/")) result += "/";
    return new ChuckString(result);
  }

  @Override
  public String toString() {
    return sb.toString();
  }
}
