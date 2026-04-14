package org.chuck.midi;

import java.lang.foreign.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native MIDI output using RtMidi via FFM. */
public class ChuckMidiOutNative {
  private static final Logger logger = Logger.getLogger(ChuckMidiOutNative.class.getName());

  private final Arena arena;
  private MemorySegment midiOutPtr = MemorySegment.NULL;

  public ChuckMidiOutNative() {
    this.arena = Arena.ofShared();
  }

  public boolean open(int portNumber) {
    if (!RtMidi.isAvailable()) return false;

    try {
      midiOutPtr = (MemorySegment) RtMidi.out_create_default.invoke();
      if (midiOutPtr.equals(MemorySegment.NULL)) return false;

      MemorySegment portName = arena.allocateFrom("ChucK-Java Output");
      RtMidi.open_port.invoke(midiOutPtr, portNumber, portName);

      logger.info("Native MIDI Output opened port " + portNumber);
      return true;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to open native MIDI output", t);
      return false;
    }
  }

  public boolean openVirtual(String name) {
    if (!RtMidi.isAvailable()) return false;
    try {
      midiOutPtr = (MemorySegment) RtMidi.out_create_default.invoke();
      MemorySegment portName = arena.allocateFrom(name);
      RtMidi.open_virtual_port.invoke(midiOutPtr, portName);
      logger.info("Native Virtual MIDI Output created: " + name);
      return true;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to create virtual MIDI output", t);
      return false;
    }
  }

  public void send(MidiMsg msg) {
    if (midiOutPtr.equals(MemorySegment.NULL)) return;

    try {
      // RtMidi expects a buffer of bytes
      MemorySegment buffer = arena.allocate(3);
      buffer.set(ValueLayout.JAVA_BYTE, 0, (byte) msg.data1);
      buffer.set(ValueLayout.JAVA_BYTE, 1, (byte) msg.data2);
      buffer.set(ValueLayout.JAVA_BYTE, 2, (byte) msg.data3);

      RtMidi.out_send_message.invoke(midiOutPtr, buffer, 3);
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to send native MIDI message", t);
    }
  }

  public void close() {
    if (!midiOutPtr.equals(MemorySegment.NULL)) {
      try {
        RtMidi.close_port.invoke(midiOutPtr);
        RtMidi.out_free.invoke(midiOutPtr);
      } catch (Throwable t) {
      }
      midiOutPtr = MemorySegment.NULL;
    }
  }
}
