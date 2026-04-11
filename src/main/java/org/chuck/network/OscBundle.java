package org.chuck.network;

import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/** OscBundle: Groups multiple OSC messages into a single packet. */
public class OscBundle extends ChuckObject {
  private final List<Object> elements = new ArrayList<>(); // OscMsg or OscBundle

  public OscBundle() {
    super(new ChuckType("OscBundle", ChuckType.OBJECT, 0, 0));
  }

  public void add(OscMsg msg) {
    elements.add(msg);
  }

  public void add(OscBundle bundle) {
    elements.add(bundle);
  }

  public List<Object> getElements() {
    return elements;
  }
}
