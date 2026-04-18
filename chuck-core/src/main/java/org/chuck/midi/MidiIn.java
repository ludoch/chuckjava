package org.chuck.midi;

import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;
import org.rtmidijava.RtMidi;
import org.rtmidijava.RtMidiFactory;

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
        java.util.prefs.Preferences.userNodeForPackage(MidiIn.class);
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
    driver.open(port);
  }

  public void open(int port, RtMidi.Api api) {
    openedName = name(port);
    driver.open(port, api);
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
    try {
      org.rtmidijava.RtMidiIn in = RtMidiFactory.createDefaultIn();
      int count = in.getPortCount();
      String[] names = new String[count];
      for (int i = 0; i < count; i++) {
        names[i] = in.getPortName(i);
      }
      in.closePort();
      return names;
    } catch (Throwable t) {
      // Fallback to JavaSound
      List<String> javaPorts = ChuckMidi.listInputDevices();
      return javaPorts.toArray(new String[0]);
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
      msg.when = m.when;
      msg.setData(m.getData());
      return true;
    }
    return false;
  }

  public void close() {
    driver.close();
    javaDriver.close();
  }

  public boolean isNative() {
    return true;
  }

  /** Returns all compiled native MIDI APIs for the current platform. */
  public static java.util.List<RtMidi.Api> getCompiledApis() {
    // Return common ones for now
    return java.util.Arrays.asList(RtMidi.Api.values());
  }

  // Expose the event for 'min => now'
  public Object getEvent() {
    return this;
  }
}
