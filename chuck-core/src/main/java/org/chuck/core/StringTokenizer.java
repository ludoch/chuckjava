package org.chuck.core;

/** ChucK StringTokenizer - splits a string into whitespace-delimited tokens. */
public class StringTokenizer extends ChuckObject {
  private String[] tokens = new String[0];
  private int pos = 0;

  public StringTokenizer() {
    super(new ChuckType("StringTokenizer", ChuckType.OBJECT, 0, 0));
  }

  public void set(String s) {
    if (s == null || s.trim().isEmpty()) {
      tokens = new String[0];
    } else {
      tokens = s.trim().split("\\s+");
    }
    pos = 0;
  }

  public int size() {
    return tokens.length;
  }

  public boolean more() {
    return pos < tokens.length;
  }

  public String next() {
    if (pos < tokens.length) return tokens[pos++];
    return "";
  }

  public String next(String ignored) {
    return next();
  }

  public String get(int index) {
    if (index >= 0 && index < tokens.length) return tokens[index];
    return "";
  }

  public String get(int index, String ignored) {
    return get(index);
  }

  public void delims(String d) {
    // Simplified: we could use these as regex, but for now just stubbed
  }

  public void reset() {
    pos = 0;
  }
}
