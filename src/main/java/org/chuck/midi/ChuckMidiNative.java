package org.chuck.midi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
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

  private static final java.util.List<MidiMonitor> monitors = new CopyOnWriteArrayList<>();

  public static void addMonitor(MidiMonitor monitor) {
    monitors.add(monitor);
  }

  public static void removeMonitor(MidiMonitor monitor) {
    monitors.remove(monitor);
  }

  // --- Shared Port Management ---
  private static class SharedPort {
    MemorySegment ptr = MemorySegment.NULL;
    MemorySegment callbackStub = MemorySegment.NULL;
    MemorySegment errorStub = MemorySegment.NULL;
    Arena arena;
    String name = "unopened";
    final java.util.List<ChuckMidiNative> subscribers = new CopyOnWriteArrayList<>();

    void onMidiMessage(double timestamp, MemorySegment message, long size, MemorySegment userData) {
      if (size <= 0) return;
      byte[] raw = new byte[(int) size];
      MemorySegment.copy(message, ValueLayout.JAVA_BYTE, 0, raw, 0, (int) size);

      for (ChuckMidiNative sub : subscribers) {
        MidiMsg msg = new MidiMsg();
        msg.when = timestamp;
        msg.setData(raw);
        sub.queue.addLast(msg);
        sub.event.broadcast(sub.vm);
      }

      for (MidiMonitor monitor : monitors) {
        MidiMsg msg = new MidiMsg();
        msg.when = timestamp;
        msg.setData(raw);
        monitor.onMessage(name, msg);
      }
    }

    void onNativeError(int type, MemorySegment errorText, MemorySegment userData) {
      String msg = errorText.getString(0);
      RtMidi.ErrorType et = RtMidi.ErrorType.fromId(type);
      logger.log(Level.WARNING, "[RtMidi] " + et + ": " + msg);
    }
  }

  private static final ConcurrentHashMap<String, SharedPort> sharedPorts =
      new ConcurrentHashMap<>();
  // -----------------------------

  private final ChuckVM vm;
  private final ChuckEvent event;
  private final ConcurrentLinkedDeque<MidiMsg> queue;
  private SharedPort myPort = null;

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
    this.queue = queue;
  }

  public void open(int portNumber) {
    open(portNumber, RtMidi.Api.UNSPECIFIED);
  }

  public void open(int portNumber, RtMidi.Api api) {
    if (!RtMidi.isAvailable()) return;

    String portKey = api.id + ":" + portNumber;

    try {
      SharedPort shared =
          sharedPorts.computeIfAbsent(
              portKey,
              k -> {
                SharedPort sp = new SharedPort();
                sp.arena = Arena.ofShared();

                try {
                  if (api == RtMidi.Api.UNSPECIFIED) {
                    sp.ptr = (MemorySegment) RtMidi.in_create_default.invoke();
                  } else {
                    MemorySegment clientName = sp.arena.allocateFrom("ChucK-Java Input");
                    sp.ptr = (MemorySegment) RtMidi.in_create.invoke(api.id, clientName, 1024);
                  }

                  if (sp.ptr.equals(MemorySegment.NULL)) return sp;

                  MethodHandle onMidiHandle =
                      MethodHandles.lookup()
                          .findVirtual(
                              SharedPort.class,
                              "onMidiMessage",
                              java.lang.invoke.MethodType.methodType(
                                  void.class,
                                  double.class,
                                  MemorySegment.class,
                                  long.class,
                                  MemorySegment.class));
                  sp.callbackStub =
                      Linker.nativeLinker()
                          .upcallStub(onMidiHandle.bindTo(sp), CALLBACK_DESC, sp.arena);
                  RtMidi.in_set_callback.invoke(sp.ptr, sp.callbackStub, MemorySegment.NULL);

                  MethodHandle onErrorHandle =
                      MethodHandles.lookup()
                          .findVirtual(
                              SharedPort.class,
                              "onNativeError",
                              java.lang.invoke.MethodType.methodType(
                                  void.class, int.class, MemorySegment.class, MemorySegment.class));
                  sp.errorStub =
                      Linker.nativeLinker()
                          .upcallStub(
                              onErrorHandle.bindTo(sp), RtMidi.ERROR_CALLBACK_DESC, sp.arena);
                  RtMidi.set_error_callback.invoke(sp.ptr, sp.errorStub, MemorySegment.NULL);

                  MemorySegment portNameStr = sp.arena.allocateFrom("ChucK-Java Input");
                  sp.name = RtMidi.getPortName(sp.ptr, portNumber);
                  RtMidi.open_port.invoke(sp.ptr, portNumber, portNameStr);

                  logger.info(
                      "Native MIDI Input opened shared port " + portNumber + " (API=" + api + ")");
                } catch (Throwable t) {
                  logger.log(Level.SEVERE, "Failed to initialize shared native MIDI input", t);
                }
                return sp;
              });

      if (!shared.ptr.equals(MemorySegment.NULL)) {
        this.myPort = shared;
        shared.subscribers.add(this);
      }
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to open native MIDI input", t);
    }
  }

  public void openVirtual(String name) {
    if (!RtMidi.isAvailable()) return;

    // Virtual ports are generally not shared in the same way as hardware ports,
    // but we still wrap them in a SharedPort object for consistency.
    try {
      SharedPort sp = new SharedPort();
      sp.arena = Arena.ofShared();
      sp.ptr = (MemorySegment) RtMidi.in_create_default.invoke();
      MemorySegment portNameStr = sp.arena.allocateFrom(name);

      MethodHandle onMidiHandle =
          MethodHandles.lookup()
              .findVirtual(
                  SharedPort.class,
                  "onMidiMessage",
                  java.lang.invoke.MethodType.methodType(
                      void.class,
                      double.class,
                      MemorySegment.class,
                      long.class,
                      MemorySegment.class));
      sp.callbackStub =
          Linker.nativeLinker().upcallStub(onMidiHandle.bindTo(sp), CALLBACK_DESC, sp.arena);
      RtMidi.in_set_callback.invoke(sp.ptr, sp.callbackStub, MemorySegment.NULL);

      RtMidi.open_virtual_port.invoke(sp.ptr, portNameStr);
      sp.name = name;
      logger.info("Native Virtual MIDI Input created: " + name);

      this.myPort = sp;
      sp.subscribers.add(this);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to create virtual MIDI input", t);
    }
  }

  public void ignoreTypes(boolean midiSysex, boolean midiTime, boolean midiSense) {
    if (myPort == null || myPort.ptr.equals(MemorySegment.NULL)) return;
    try {
      RtMidi.in_ignore_types.invoke(myPort.ptr, midiSysex, midiTime, midiSense);
    } catch (Throwable t) {
    }
  }

  public void close() {
    if (myPort != null) {
      myPort.subscribers.remove(this);

      // We don't automatically close the shared native pointer here unless subscribers == 0.
      // But for simplicity and safety against leaks in hot-reloading,
      // we'll keep the port open for the lifetime of the JVM if it's a shared physical port.
      // Virtual ports could be closed if we tracked them separately.
      if (myPort.subscribers.isEmpty() && myPort.name.startsWith("Virtual:")) {
        // close virtual
      }
      myPort = null;
    }
  }
}
