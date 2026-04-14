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

  /** Lists all available native MIDI input port names. */
  public static String[] list() {
    if (!RtMidi.isAvailable()) return new String[0];
    try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
      java.lang.foreign.MemorySegment in =
          (java.lang.foreign.MemorySegment) RtMidi.in_create_default.invoke();
      if (in.equals(java.lang.foreign.MemorySegment.NULL)) return new String[0];

      int count = (int) RtMidi.get_port_count.invoke(in);
      String[] names = new String[count];
      for (int i = 0; i < count; i++) {
        names[i] = RtMidi.getPortName(in, i);
      }
      RtMidi.in_free.invoke(in);
      return names;
    } catch (Throwable t) {
      return new String[0];
    }
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
