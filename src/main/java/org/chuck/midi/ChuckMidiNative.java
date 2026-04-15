package org.chuck.midi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;

/** Enhanced Native MIDI input using RtMidi callback model via FFM. */
public class ChuckMidiNative {
  private static final Logger logger = Logger.getLogger(ChuckMidiNative.class.getName());

  /** Functional interface for monitoring MIDI messages globally in the IDE. */
  @FunctionalInterface
  public interface MidiMonitor {
    void onMessage(String deviceName, MidiMsg msg);
  }

  private static final java.util.List<MidiMonitor> monitors =
      new java.util.concurrent.CopyOnWriteArrayList<>();

  public static void addMonitor(MidiMonitor monitor) {
    monitors.add(monitor);
  }

  public static void removeMonitor(MidiMonitor monitor) {
    monitors.remove(monitor);
  }

  private final ChuckVM vm;
  private final ChuckEvent event;
  private final Arena arena;
  private final ConcurrentLinkedDeque<MidiMsg> queue;

  private MemorySegment midiInPtr = MemorySegment.NULL;
  private MemorySegment callbackStub = null;
  private String openedName = "unopened";

  // Callback Descriptor: void callback(double timestamp, const unsigned char* message, size_t
  // messageSize, void* userData)
  private static final FunctionDescriptor CALLBACK_DESC =
      FunctionDescriptor.ofVoid(
          ValueLayout.JAVA_DOUBLE, // timestamp
          ValueLayout.ADDRESS, // message buffer
          ValueLayout.JAVA_LONG, // message size
          ValueLayout.ADDRESS // user data
          );

  public ChuckMidiNative(ChuckVM vm, ChuckEvent event, ConcurrentLinkedDeque<MidiMsg> queue) {
    this.vm = vm;
    this.event = event;
    this.arena = Arena.ofShared();
    this.queue = queue;
  }

  public void open(int portNumber) {
    open(portNumber, RtMidi.Api.UNSPECIFIED);
  }

  public void open(int portNumber, RtMidi.Api api) {
    if (!RtMidi.isAvailable()) return;

    try {
      if (api == RtMidi.Api.UNSPECIFIED) {
        midiInPtr = (MemorySegment) RtMidi.in_create_default.invoke();
      } else {
        MemorySegment clientName = arena.allocateFrom("ChucK-Java Input");
        midiInPtr = (MemorySegment) RtMidi.in_create.invoke(api.id, clientName, 1024);
      }

      if (midiInPtr.equals(MemorySegment.NULL)) return;

      // Prepare MIDI callback stub
      MethodHandle onMidiHandle =
          MethodHandles.lookup()
              .findVirtual(
                  ChuckMidiNative.class,
                  "onMidiMessage",
                  java.lang.invoke.MethodType.methodType(
                      void.class,
                      double.class,
                      MemorySegment.class,
                      long.class,
                      MemorySegment.class));
      callbackStub =
          Linker.nativeLinker().upcallStub(onMidiHandle.bindTo(this), CALLBACK_DESC, arena);
      RtMidi.in_set_callback.invoke(midiInPtr, callbackStub, MemorySegment.NULL);

      // Prepare Error callback stub
      MethodHandle onErrorHandle =
          MethodHandles.lookup()
              .findVirtual(
                  ChuckMidiNative.class,
                  "onNativeError",
                  java.lang.invoke.MethodType.methodType(
                      void.class, int.class, MemorySegment.class, MemorySegment.class));
      MemorySegment errorStub =
          Linker.nativeLinker()
              .upcallStub(onErrorHandle.bindTo(this), RtMidi.ERROR_CALLBACK_DESC, arena);
      RtMidi.set_error_callback.invoke(midiInPtr, errorStub, MemorySegment.NULL);

      MemorySegment portName = arena.allocateFrom("ChucK-Java Input");
      openedName = RtMidi.getPortName(midiInPtr, portNumber);
      RtMidi.open_port.invoke(midiInPtr, portNumber, portName);

      logger.info("Native MIDI Input opened port " + portNumber + " (API=" + api + ")");
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to open native MIDI input", t);
    }
  }

  private void onNativeError(int type, MemorySegment errorText, MemorySegment userData) {
    String msg = errorText.getString(0);
    RtMidi.ErrorType et = RtMidi.ErrorType.fromId(type);
    vm.print("[RtMidi] Native Error (" + et + "): " + msg + "\n");
    logger.log(Level.WARNING, "[RtMidi] " + et + ": " + msg);
  }

  public void openVirtual(String name) {
    if (!RtMidi.isAvailable()) return;
    try {
      midiInPtr = (MemorySegment) RtMidi.in_create_default.invoke();
      MemorySegment portName = arena.allocateFrom(name);

      // Set callback
      MethodHandle onMidiHandle =
          MethodHandles.lookup()
              .findVirtual(
                  ChuckMidiNative.class,
                  "onMidiMessage",
                  java.lang.invoke.MethodType.methodType(
                      void.class,
                      double.class,
                      MemorySegment.class,
                      long.class,
                      MemorySegment.class));
      callbackStub =
          Linker.nativeLinker().upcallStub(onMidiHandle.bindTo(this), CALLBACK_DESC, arena);
      RtMidi.in_set_callback.invoke(midiInPtr, callbackStub, MemorySegment.NULL);

      RtMidi.open_virtual_port.invoke(midiInPtr, portName);
      openedName = name;
      logger.info("Native Virtual MIDI Input created: " + name);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to create virtual MIDI input", t);
    }
  }

  /** Native callback invoked by RtMidi when a message arrives. */
  private void onMidiMessage(
      double timestamp, MemorySegment message, long size, MemorySegment userData) {
    if (size <= 0) return;

    MidiMsg msg = new MidiMsg();
    byte[] raw = new byte[(int) size];
    MemorySegment.copy(message, ValueLayout.JAVA_BYTE, 0, raw, 0, (int) size);
    msg.setData(raw);

    // Notify global monitors (for IDE visualization)
    for (MidiMonitor monitor : monitors) {
      monitor.onMessage(openedName, msg);
    }

    queue.addLast(msg);
    event.broadcast(vm);
  }

  public void ignoreTypes(boolean midiSysex, boolean midiTime, boolean midiSense) {
    if (midiInPtr.equals(MemorySegment.NULL)) return;
    try {
      RtMidi.in_ignore_types.invoke(midiInPtr, midiSysex, midiTime, midiSense);
    } catch (Throwable t) {
    }
  }

  public void close() {
    if (!midiInPtr.equals(MemorySegment.NULL)) {
      try {
        RtMidi.close_port.invoke(midiInPtr);
        RtMidi.in_free.invoke(midiInPtr);
      } catch (Throwable t) {
      }
      midiInPtr = MemorySegment.NULL;
    }
    // arena will close via shutdown hooks or manual close if we used ofConfined,
    // but here we keep it for the life of the driver.
  }
}
