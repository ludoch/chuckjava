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
    return open(portNumber, RtMidi.Api.UNSPECIFIED);
  }

  public boolean open(int portNumber, RtMidi.Api api) {
    if (!RtMidi.isAvailable()) return false;

    try {
      if (api == RtMidi.Api.UNSPECIFIED) {
        midiOutPtr = (MemorySegment) RtMidi.out_create_default.invoke();
      } else {
        MemorySegment clientName = arena.allocateFrom("ChucK-Java Output");
        midiOutPtr = (MemorySegment) RtMidi.out_create.invoke(api.id, clientName);
      }

      if (midiOutPtr.equals(MemorySegment.NULL)) return false;

      // Prepare Error callback stub
      MethodHandle onErrorHandle =
          MethodHandles.lookup()
              .findVirtual(
                  ChuckMidiOutNative.class,
                  "onNativeError",
                  java.lang.invoke.MethodType.methodType(
                      void.class, int.class, MemorySegment.class, MemorySegment.class));
      MemorySegment errorStub =
          Linker.nativeLinker()
              .upcallStub(onErrorHandle.bindTo(this), RtMidi.ERROR_CALLBACK_DESC, arena);
      RtMidi.set_error_callback.invoke(midiOutPtr, errorStub, MemorySegment.NULL);

      MemorySegment portName = arena.allocateFrom("ChucK-Java Output");
      RtMidi.open_port.invoke(midiOutPtr, portNumber, portName);

      logger.info("Native MIDI Output opened port " + portNumber + " (API=" + api + ")");
      return true;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to open native MIDI output", t);
      return false;
    }
  }

  private void onNativeError(int type, MemorySegment errorText, MemorySegment userData) {
    String msg = errorText.getString(0);
    RtMidi.ErrorType et = RtMidi.ErrorType.fromId(type);
    System.err.println("[RtMidi] Native Error (" + et + "): " + msg);
    logger.log(Level.WARNING, "[RtMidi] " + et + ": " + msg);
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
      byte[] raw = msg.getData();
      int size = msg.size();
      MemorySegment buffer = arena.allocate(size);
      MemorySegment.copy(raw, 0, buffer, ValueLayout.JAVA_BYTE, 0, size);

      RtMidi.out_send_message.invoke(midiOutPtr, buffer, size);
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
