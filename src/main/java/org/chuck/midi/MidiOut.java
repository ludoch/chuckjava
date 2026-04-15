package org.chuck.midi;

import java.util.List;
import javax.sound.midi.*;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * MidiOut: Support for sending MIDI messages. Uses native RtMidi if available, falls back to
 * javax.sound.midi.
 */
public class MidiOut extends ChuckObject {
  private ChuckMidiOut javaDriver;
  private ChuckMidiOutNative nativeDriver;
  private String openedName = "unopened";

  public MidiOut() {
    super(ChuckType.OBJECT);
    if (RtMidi.isAvailable()) {
      nativeDriver = new ChuckMidiOutNative();
    }
    javaDriver = new ChuckMidiOut();
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
    return javaDriver.open(port) ? 1 : 0;
  }

  /** Returns the number of available MIDI output ports. */
  public int num() {
    return list().length;
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
    List<String> javaPorts = ChuckMidiOut.listOutputDevices();
    return javaPorts.toArray(new String[0]);
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
    } else {
      javaDriver.send(msg);
    }
  }

  /** Sends a Note On message. */
  public void noteOn(int chan, int note, int velocity) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0x90 | (chan & 0x0F);
    msg.data2 = note & 0x7F;
    msg.data3 = velocity & 0x7F;
    send(msg);
  }

  /** Sends a Note Off message. */
  public void noteOff(int chan, int note, int velocity) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0x80 | (chan & 0x0F);
    msg.data2 = note & 0x7F;
    msg.data3 = velocity & 0x7F;
    send(msg);
  }

  /** Sends a Control Change message. */
  public void controlChange(int chan, int ctrl, int value) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0xB0 | (chan & 0x0F);
    msg.data2 = ctrl & 0x7F;
    msg.data3 = value & 0x7F;
    send(msg);
  }

  /** Sends a Program Change message. */
  public void programChange(int chan, int program) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0xC0 | (chan & 0x0F);
    msg.data2 = program & 0x7F;
    msg.data3 = 0;
    msg.setData(new byte[] {(byte) msg.data1, (byte) msg.data2});
    send(msg);
  }

  /** Sends a Polyphonic Key Pressure (Aftertouch) message. */
  public void polyPressure(int chan, int note, int value) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0xA0 | (chan & 0x0F);
    msg.data2 = note & 0x7F;
    msg.data3 = value & 0x7F;
    send(msg);
  }

  /** Sends a Channel Pressure (Aftertouch) message. */
  public void channelPressure(int chan, int value) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0xD0 | (chan & 0x0F);
    msg.data2 = value & 0x7F;
    msg.data3 = 0;
    msg.setData(new byte[] {(byte) msg.data1, (byte) msg.data2});
    send(msg);
  }

  /** Sends a Pitch Bend message (14-bit). */
  public void pitchBend(int chan, int value) {
    // value is 0 to 16383, 8192 is center
    int lsb = value & 0x7F;
    int msb = (value >> 7) & 0x7F;
    pitchBend(chan, lsb, msb);
  }

  /** Sends a Pitch Bend message (LSB, MSB). */
  public void pitchBend(int chan, int lsb, int msb) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0xE0 | (chan & 0x0F);
    msg.data2 = lsb & 0x7F;
    msg.data3 = msb & 0x7F;
    send(msg);
  }

  public void close() {
    if (nativeDriver != null) {
      nativeDriver.close();
    }
    javaDriver.close();
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
