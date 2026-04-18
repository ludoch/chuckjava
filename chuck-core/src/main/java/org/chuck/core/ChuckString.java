package org.chuck.core;

/** A mutable string for ChucK, wrapping StringBuilder. */
public class ChuckString extends ChuckObject {
  private final StringBuilder sb;

  public ChuckString(String initial) {
    super(ChuckType.STRING);
    this.sb = new StringBuilder(initial != null ? initial : "");
  }

  public void setValue(String val) {
    sb.setLength(0);
    sb.append(val != null ? val : "");
  }

  public ChuckString set(Object val) {
    sb.setLength(0);
    sb.append(valToString(val));
    return this;
  }

  public long length() {
    return sb.length();
  }

  public long charAt(long index) {
    if (index < 0 || index >= sb.length()) return 0;
    return sb.charAt((int) index);
  }

  public void setCharAt(long index, long ch) {
    if (index >= 0 && index < sb.length()) {
      sb.setCharAt((int) index, (char) ch);
    }
  }

  public ChuckString substring(long start) {
    int s = Math.max(0, Math.min(sb.length(), (int) start));
    return new ChuckString(sb.substring(s));
  }

  public ChuckString substring(long start, long len) {
    int s = Math.max(0, Math.min(sb.length(), (int) start));
    int e = Math.max(s, Math.min(sb.length(), s + (int) len));
    return new ChuckString(sb.substring(s, e));
  }

  public ChuckString insert(long offset, Object val) {
    int off = Math.max(0, Math.min(sb.length(), (int) offset));
    sb.insert(off, valToString(val));
    return this;
  }

  public ChuckString erase(long start, long len) {
    int s = Math.max(0, Math.min(sb.length(), (int) start));
    int e = Math.max(s, Math.min(sb.length(), s + (int) len));
    sb.delete(s, e);
    return this;
  }

  public ChuckString replace(long start, Object val) {
    String s = valToString(val);
    int sIdx = Math.max(0, Math.min(sb.length(), (int) start));
    sb.replace(sIdx, sb.length(), s);
    return this;
  }

  public ChuckString replace(Object oldVal, Object newVal) {
    String os = valToString(oldVal);
    if (os.isEmpty()) return this; // empty search string: no-op
    String ns = valToString(newVal);
    int idx = sb.indexOf(os);
    while (idx != -1) {
      sb.replace(idx, idx + os.length(), ns);
      idx = sb.indexOf(os, idx + ns.length());
    }
    return this;
  }

  public ChuckString replace(long start, long len, Object val) {
    int sIdx = Math.max(0, Math.min(sb.length(), (int) start));
    int eIdx = Math.max(sIdx, Math.min(sb.length(), sIdx + (int) len));
    sb.replace(sIdx, eIdx, valToString(val));
    return this;
  }

  public long find(Object val) {
    return sb.indexOf(valToString(val));
  }

  public long find(Object val, long start) {
    int s = Math.max(0, Math.min(sb.length(), (int) start));
    return sb.indexOf(valToString(val), s);
  }

  public long rfind(Object val) {
    return sb.lastIndexOf(valToString(val));
  }

  public long rfind(Object val, long start) {
    int s = Math.max(0, Math.min(sb.length(), (int) start));
    return sb.lastIndexOf(valToString(val), s);
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
    String s = sb.toString();
    int i = 0;
    while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++;
    return new ChuckString(s.substring(i));
  }

  public ChuckString rtrim() {
    String s = sb.toString();
    int i = s.length() - 1;
    while (i >= 0 && Character.isWhitespace(s.charAt(i))) i--;
    return new ChuckString(s.substring(0, i + 1));
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

  private static String valToString(Object val) {
    if (val == null) return "null";
    if (val instanceof ChuckString cs) return cs.sb.toString();
    return val.toString();
  }
}
