package org.chuck.network;

import java.util.ArrayList;
import java.util.List;

/** Represents an OSC message. */
public class OscMsg extends org.chuck.core.ChuckObject {
  public String address = "";
  private final List<Object> args = new ArrayList<>();

  public OscMsg() {
    super(new org.chuck.core.ChuckType("OscMsg", org.chuck.core.ChuckType.OBJECT, 0, 0));
  }

  public void addInt(int val) {
    args.add(val);
  }

  public void addFloat(float val) {
    args.add(val);
  }

  public void addString(String val) {
    args.add(val);
  }

  public int getInt(int index) {
    if (index >= 0 && index < args.size() && args.get(index) instanceof Integer v) return v;
    return 0;
  }

  public float getFloat(int index) {
    if (index >= 0 && index < args.size()) {
      Object v = args.get(index);
      if (v instanceof Float f) return f;
      if (v instanceof Double d) return d.floatValue();
      if (v instanceof Integer i) return i.floatValue();
    }
    return 0.0f;
  }

  public String getString(int index) {
    if (index >= 0 && index < args.size() && args.get(index) instanceof String s) return s;
    return "";
  }

  public int numArgs() {
    return args.size();
  }

  public List<Object> getArgs() {
    return args;
  }

  public void copyFrom(OscMsg other) {
    this.address = other.address;
    this.args.clear();
    this.args.addAll(other.args);
  }
}
