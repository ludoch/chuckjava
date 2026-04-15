package org.chuck.midi;

import javax.sound.midi.*;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * MidiOut: Support for sending MIDI messages. Uses native RtMidi if available, falls back to
 * javax.sound.midi.
 */
public class MidiOut extends ChuckObject {
  private Receiver receiver; // javax.sound.midi fallback
  private MidiDevice device; // javax.sound.midi fallback

  private ChuckMidiOutNative nativeDriver;
  private String openedName = "unopened";

  public MidiOut() {
    super(ChuckType.OBJECT);
    if (RtMidi.isAvailable()) {
      nativeDriver = new ChuckMidiOutNative();
    }
  }

  public int open(int port) {
    openedName = name(port);
    return open(port, RtMidi.Api.UNSPECIFIED);
  }

  public int open(int port, RtMidi.Api api) {
    openedName = name(port);
    if (nativeDriver != null) {
      return nativeDriver.open(port, api) ? 1 : 0;
    }

    if (api != RtMidi.Api.UNSPECIFIED) return 0; // JavaSound doesn't support specific RtMidi APIs

    // Fallback to JavaSound
    try {
      MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
      if (port < 0 || port >= infos.length) return 0;

      device = MidiSystem.getMidiDevice(infos[port]);
      if (!device.isOpen()) device.open();
      receiver = device.getReceiver();
      return 1;
    } catch (MidiUnavailableException e) {
      System.err.println("MidiOut: Error opening port " + port + ": " + e.getMessage());
      return 0;
    }
  }

  /** Returns the number of available MIDI output ports. */
  public int num() {
    String[] ports = list();
    return ports.length;
  }

  /** Returns the name of the MIDI output port at the given index. */
  public String name(int index) {
    String[] ports = list();
    if (index >= 0 && index < ports.length) return ports[index];
    return "";
  }

  /**
   * Opens the first MIDI output port whose name contains the given substring (case-insensitive).
   */
  public int open(String name) {
    String[] ports = list();
    String lowerTarget = name.toLowerCase();
    for (int i = 0; i < ports.length; i++) {
      if (ports[i].toLowerCase().contains(lowerTarget)) {
        return open(i);
      }
    }
    return 0;
  }

  /** Lists all available MIDI output port names. */
  public static String[] list() {
    if (RtMidi.isAvailable()) {
      try (java.lang.foreign.Arena arena = java.lang.foreign.Arena.ofConfined()) {
        java.lang.foreign.MemorySegment out =
            (java.lang.foreign.MemorySegment) RtMidi.out_create_default.invoke();
        if (out.equals(java.lang.foreign.MemorySegment.NULL)) return new String[0];

        int count = (int) RtMidi.get_port_count.invoke(out);
        String[] names = new String[count];
        for (int i = 0; i < count; i++) {
          names[i] = RtMidi.getPortName(out, i);
        }
        RtMidi.out_free.invoke(out);
        return names;
      } catch (Throwable t) {
      }
    }

    // Fallback: list JavaSound devices
    MidiDevice.Info[] infos = MidiSystem.getMidiDeviceInfo();
    String[] names = new String[infos.length];
    for (int i = 0; i < infos.length; i++) {
      names[i] = infos[i].getName();
    }
    return names;
  }

  /** Virtual ports are only supported by the native driver (macOS/Linux). */
  public int openVirtual(String name) {
    if (nativeDriver != null) {
      return nativeDriver.openVirtual(name) ? 1 : 0;
    }
    return 0;
  }

  public void send(MidiMsg msg) {
    if (nativeDriver != null) {
      nativeDriver.send(msg);
      return;
    }

    if (receiver == null) return;
    try {
      byte[] raw = msg.getData();
      int status = raw[0] & 0xFF;

      if (status == 0xF0) { // Sysex
        SysexMessage sm = new SysexMessage();
        sm.setMessage(raw, raw.length);
        receiver.send(sm, -1);
      } else if (status == 0xFF) { // Meta
        MetaMessage mm = new MetaMessage();
        mm.setMessage(raw[1] & 0xFF, raw, raw.length);
        receiver.send(mm, -1);
      } else {
        ShortMessage sm = new ShortMessage();
        sm.setMessage(msg.data1, msg.data2, msg.data3);
        receiver.send(sm, -1);
      }
    } catch (InvalidMidiDataException e) {
      System.err.println("MidiOut: Invalid MIDI data: " + e.getMessage());
    }
  }

  public void close() {
    if (nativeDriver != null) {
      nativeDriver.close();
      return;
    }

    if (receiver != null) receiver.close();
    if (device != null && device.isOpen()) device.close();
    receiver = null;
    device = null;
  }

  public boolean isNative() {
    return nativeDriver != null;
  }

  /** Returns the name of the currently opened port, or "unopened". */
  public String name() {
    return openedName;
  }

  /** Returns all compiled native MIDI APIs for the current platform. */
  public static java.util.List<RtMidi.Api> getCompiledApis() {
    return RtMidi.getCompiledApis();
  }
}
