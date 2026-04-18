package org.chuck.ide;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import org.chuck.core.ChuckShred;

/** Model for active shreds in the IDE. */
public class ShredInfo {
  public final int id;
  public final String name;
  public final long startTimeMillis;
  public final ChuckShred shred;
  public final StringProperty durationProp = new SimpleStringProperty("0.0s");

  public ShredInfo(int id, String name, ChuckShred shred) {
    this.id = id;
    this.name = name;
    this.shred = shred;
    this.startTimeMillis = System.currentTimeMillis();
  }

  public void updateDuration() {
    double secs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
    String s = String.format("%.1fs", secs);
    if (!s.equals(durationProp.get())) {
      durationProp.set(s);
    }
  }
}
