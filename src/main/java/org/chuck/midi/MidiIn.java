package org.chuck.midi;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;

/** The ChucK MidiIn object. Support native RtMidi via ChuckMidiNative. */
public class MidiIn extends ChuckEvent {
  private final ChuckMidiNative driver;
  private final ChuckMidi javaDriver;
  private final ConcurrentLinkedDeque<MidiMsg> queue = new ConcurrentLinkedDeque<>();
  private String openedName = "unopened";

  private final java.util.List<MidiPoly> targets =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  public MidiIn(ChuckVM vm) {
    this.driver = new ChuckMidiNative(vm, this, this.queue);
    this.javaDriver = new ChuckMidi(vm, this);

    // Auto-apply preferences from IDE
    java.util.prefs.Preferences prefs =
        java.util.prefs.Preferences.userNodeForPackage(RtMidi.class);
    boolean ignoreSysex = prefs.getBoolean("midi.ignoreSysex", false);
    boolean ignoreTime = prefs.getBoolean("midi.ignoreTime", true);
    driver.ignoreTypes(ignoreSysex, ignoreTime, true); // sense=true by default
  }

  public void connect(MidiPoly p) {
    if (p != null) targets.add(p);
  }

  public java.util.List<MidiPoly> getTargets() {
    return targets;
  }

  public void open(int port) {
    openedName = name(port);
    if (RtMidi.isAvailable()) {
      driver.open(port);
    } else {
      javaDriver.open(port);
    }
  }

  public void open(int port, RtMidi.Api api) {
    openedName = name(port);
    if (RtMidi.isAvailable()) {
      driver.open(port, api);
    } else {
      javaDriver.open(port);
    }
  }

  /** Returns the name of the currently opened port, or "unopened". */
  public String name() {
    return openedName;
  }

  /** Returns the number of available MIDI input ports. */
  public int num() {
    return list().length;
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

  /** Lists all available MIDI input port names. */
  public static String[] list() {
    if (RtMidi.isAvailable()) {
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
        // fall through to JavaSound if native call fails
      }
    }

    // Fallback to JavaSound
    List<String> javaPorts = ChuckMidi.listInputDevices();
    return javaPorts.toArray(new String[0]);
  }

  public void openVirtual(String name) {
    if (RtMidi.isAvailable()) {
      driver.openVirtual(name);
    }
  }

  public void ignoreTypes(boolean midiSysex, boolean midiTime, boolean midiSense) {
    if (RtMidi.isAvailable()) {
      driver.ignoreTypes(midiSysex, midiTime, midiSense);
    }
  }

  public boolean recv(MidiMsg msg) {
    if (RtMidi.isAvailable()) {
      MidiMsg m = queue.pollFirst();
      if (m != null) {
        msg.data1 = m.data1;
        msg.data2 = m.data2;
        msg.data3 = m.data3;
        msg.when = m.when;
        msg.setData(m.getData());
        return true;
      }
    } else {
      return javaDriver.recv(msg);
    }
    return false;
  }

  public void close() {
    if (RtMidi.isAvailable()) {
      driver.close();
    } else {
      javaDriver.close();
    }
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
