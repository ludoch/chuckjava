package org.chuck.midi;

import java.util.concurrent.ConcurrentLinkedDeque;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;

/** The ChucK MidiIn object. Support native RtMidi via ChuckMidiNative. */
public class MidiIn extends ChuckEvent {
  private final ChuckMidiNative driver;
  private final ConcurrentLinkedDeque<MidiMsg> queue = new ConcurrentLinkedDeque<>();

  public MidiIn(ChuckVM vm) {
    this.driver = new ChuckMidiNative(vm, this, this.queue);
  }

  public void open(int port) {
    driver.open(port);
  }

  public void openVirtual(String name) {
    driver.openVirtual(name);
  }

  public void ignoreTypes(boolean midiSysex, boolean midiTime, boolean midiSense) {
    driver.ignoreTypes(midiSysex, midiTime, midiSense);
  }

  public boolean recv(MidiMsg msg) {
    MidiMsg m = queue.pollFirst();
    if (m != null) {
      msg.data1 = m.data1;
      msg.data2 = m.data2;
      msg.data3 = m.data3;
      return true;
    }
    return false;
  }

  public void close() {
    driver.close();
  }

  public boolean isNative() {
    return RtMidi.isAvailable();
  }

  // Expose the event for 'min => now'
  public Object getEvent() {
    return this;
  }
}
