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

    // Auto-apply preferences from IDE
    java.util.prefs.Preferences prefs =
        java.util.prefs.Preferences.userNodeForPackage(RtMidi.class);
    boolean ignoreSysex = prefs.getBoolean("midi.ignoreSysex", false);
    boolean ignoreTime = prefs.getBoolean("midi.ignoreTime", true);
    driver.ignoreTypes(ignoreSysex, ignoreTime, true); // sense=true by default
  }

  public void open(int port) {
    driver.open(port);
  }

  public void open(int port, RtMidi.Api api) {
    driver.open(port, api);
  }

  /** Returns the number of available native MIDI input ports. */
  public int num() {
    String[] ports = list();
    return ports.length;
  }

  /** Returns the name of the MIDI input port at the given index. */
  public String name(int index) {
    String[] ports = list();
    if (index >= 0 && index < ports.length) return ports[index];
    return "";
  }

  /**
   * Opens the first native MIDI input port whose name contains the given substring
   * (case-insensitive).
   */
  public boolean open(String name) {
    if (!RtMidi.isAvailable()) return false;
    String[] ports = list();
    String lowerTarget = name.toLowerCase();
    for (int i = 0; i < ports.length; i++) {
      if (ports[i].toLowerCase().contains(lowerTarget)) {
        open(i);
        return true;
      }
    }
    return false;
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

  /** Returns all compiled native MIDI APIs for the current platform. */
  public static java.util.List<RtMidi.Api> getCompiledApis() {
    return RtMidi.getCompiledApis();
  }

  // Expose the event for 'min => now'
  public Object getEvent() {
    return this;
  }
}
