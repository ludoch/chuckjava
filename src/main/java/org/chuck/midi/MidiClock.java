package org.chuck.midi;

import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/**
 * MidiClock: Tracks MIDI Real-time clock messages and provides synchronization events. Must be fed
 * raw MidiMsg objects from a MidiIn.
 */
public class MidiClock extends ChuckObject {
  private final ChuckEvent beatEvent = new ChuckEvent();
  private final ChuckEvent sixteenthEvent = new ChuckEvent();
  private final ChuckEvent startEvent = new ChuckEvent();
  private final ChuckEvent stopEvent = new ChuckEvent();

  private int clockCount = 0;
  private long lastClockTime = 0;
  private double currentBpm = 0;

  public MidiClock() {
    super(ChuckType.OBJECT);
  }

  public void update(MidiMsg msg) {
    int status = msg.data1 & 0xFF;
    ChuckVM vm = ChuckVM.CURRENT_VM.get();

    if (status == 0xF8) { // Clock
      clockCount++;

      // Calculate BPM
      long now = System.currentTimeMillis();
      if (lastClockTime > 0) {
        long delta = now - lastClockTime;
        if (delta > 0 && delta < 1000) { // ignore huge gaps
          double currentClockBpm = 60000.0 / (delta * 24.0);
          currentBpm =
              currentBpm == 0 ? currentClockBpm : (currentBpm * 0.9) + (currentClockBpm * 0.1);
        }
      }
      lastClockTime = now;

      if (clockCount % 6 == 0) {
        sixteenthEvent.broadcast(vm);
      }
      if (clockCount % 24 == 0) {
        beatEvent.broadcast(vm);
        clockCount = 0;
      }
    } else if (status == 0xFA) { // Start
      clockCount = 0;
      startEvent.broadcast(vm);
    } else if (status == 0xFC) { // Stop
      stopEvent.broadcast(vm);
    } else if (status == 0xFB) { // Continue
      startEvent.broadcast(vm);
    }
  }

  public ChuckEvent onBeat() {
    return beatEvent;
  }

  public ChuckEvent onSixteenth() {
    return sixteenthEvent;
  }

  public ChuckEvent onStart() {
    return startEvent;
  }

  public ChuckEvent onStop() {
    return stopEvent;
  }

  public double bpm() {
    return currentBpm;
  }
}
