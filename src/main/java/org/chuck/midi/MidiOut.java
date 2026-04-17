package org.chuck.midi;

import java.util.List;
import javax.sound.midi.*;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;
import org.rtmidijava.RtMidi;
import org.rtmidijava.RtMidiFactory;

/** MidiOut: Support for sending MIDI messages. Uses native RtMidiJava for low latency. */
public class MidiOut extends ChuckObject {
  private ChuckMidiOut javaDriver;
  private ChuckMidiOutNative nativeDriver;
  private String openedName = "unopened";

  public MidiOut() {
    super(ChuckType.OBJECT);
    nativeDriver = new ChuckMidiOutNative();
    javaDriver = new ChuckMidiOut();
  }

  public int open(int port) {
    openedName = name(port);
    return open(port, RtMidi.Api.UNSPECIFIED);
  }

  public int open(int port, RtMidi.Api api) {
    openedName = name(port);
    return nativeDriver.open(port, api) ? 1 : 0;
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
    try {
      org.rtmidijava.RtMidiOut out = RtMidiFactory.createDefaultOut();
      int count = out.getPortCount();
      String[] names = new String[count];
      for (int i = 0; i < count; i++) {
        names[i] = out.getPortName(i);
      }
      out.closePort();
      return names;
    } catch (Throwable t) {
      // Fallback: list JavaSound devices
      List<String> javaPorts = ChuckMidiOut.listOutputDevices();
      return javaPorts.toArray(new String[0]);
    }
  }

  /** Virtual ports are only supported by the native driver (macOS/Linux). */
  public int openVirtual(String name) {
    return nativeDriver.openVirtual(name) ? 1 : 0;
  }

  public void send(MidiMsg msg) {
    nativeDriver.send(msg);
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
    nativeDriver.close();
    javaDriver.close();
  }

  public boolean isNative() {
    return true;
  }

  /** Returns the name of the currently opened port, or "unopened". */
  public String name() {
    return openedName;
  }

  /** Returns all compiled native MIDI APIs for the current platform. */
  public static java.util.List<RtMidi.Api> getCompiledApis() {
    return java.util.Arrays.asList(RtMidi.Api.values());
  }
}
