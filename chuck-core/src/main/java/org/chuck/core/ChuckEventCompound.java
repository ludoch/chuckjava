package org.chuck.core;

import java.util.ArrayList;
import java.util.List;

/** Base class for compound events (Conjunction and Disjunction). */
public abstract class ChuckEventCompound extends ChuckEvent {
  protected final List<ChuckEvent> events = new ArrayList<>();

  public void addEvent(ChuckEvent e) {
    events.add(e);
  }

  public List<ChuckEvent> getEvents() {
    return events;
  }
}
