package org.chuck.midi;

import java.lang.foreign.*;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Native MIDI output using RtMidi via FFM. Supports port sharing. */
public class ChuckMidiOutNative {
  private static final Logger logger = Logger.getLogger(ChuckMidiOutNative.class.getName());

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
    MemorySegment errorStub = MemorySegment.NULL;
    Arena arena;
    String name = "unopened";
    int activeSubscribers = 0;

    void onNativeError(int type, MemorySegment errorText, MemorySegment userData) {
      String msg = errorText.getString(0);
      RtMidi.ErrorType et = RtMidi.ErrorType.fromId(type);
      System.err.println("[RtMidi] Native Error (" + et + "): " + msg);
      logger.log(Level.WARNING, "[RtMidi] " + et + ": " + msg);
    }
  }

  private static final ConcurrentHashMap<String, SharedPort> sharedPorts =
      new ConcurrentHashMap<>();
  // -----------------------------

  private SharedPort myPort = null;
  private final Arena localArena = Arena.ofShared();

  public ChuckMidiOutNative() {}

  public boolean open(int portNumber) {
    return open(portNumber, RtMidi.Api.UNSPECIFIED);
  }

  public boolean open(int portNumber, RtMidi.Api api) {
    if (!RtMidi.isAvailable()) return false;

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
                    sp.ptr = (MemorySegment) RtMidi.out_create_default.invoke();
                  } else {
                    MemorySegment clientName = sp.arena.allocateFrom("ChucK-Java Output");
                    sp.ptr = (MemorySegment) RtMidi.out_create.invoke(api.id, clientName);
                  }

                  if (sp.ptr.equals(MemorySegment.NULL)) return sp;

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

                  MemorySegment portNameStr = sp.arena.allocateFrom("ChucK-Java Output");
                  sp.name = RtMidi.getPortName(sp.ptr, portNumber);
                  RtMidi.open_port.invoke(sp.ptr, portNumber, portNameStr);

                  logger.info(
                      "Native MIDI Output opened shared port " + portNumber + " (API=" + api + ")");
                } catch (Throwable t) {
                  logger.log(Level.SEVERE, "Failed to open shared native MIDI output", t);
                }
                return sp;
              });

      if (!shared.ptr.equals(MemorySegment.NULL)) {
        this.myPort = shared;
        synchronized (shared) {
          shared.activeSubscribers++;
        }
        return true;
      }
      return false;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to open native MIDI output", t);
      return false;
    }
  }

  public boolean openVirtual(String name) {
    if (!RtMidi.isAvailable()) return false;
    try {
      SharedPort sp = new SharedPort();
      sp.arena = Arena.ofShared();
      sp.ptr = (MemorySegment) RtMidi.out_create_default.invoke();
      MemorySegment portNameStr = sp.arena.allocateFrom(name);

      RtMidi.open_virtual_port.invoke(sp.ptr, portNameStr);
      sp.name = name;
      logger.info("Native Virtual MIDI Output created: " + name);

      this.myPort = sp;
      synchronized (sp) {
        sp.activeSubscribers++;
      }
      return true;
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to create virtual MIDI output", t);
      return false;
    }
  }

  public void send(MidiMsg msg) {
    if (myPort == null || myPort.ptr.equals(MemorySegment.NULL)) return;

    try {
      byte[] raw = msg.getData();
      int size = msg.size();
      MemorySegment buffer = localArena.allocate(size);
      MemorySegment.copy(raw, 0, buffer, ValueLayout.JAVA_BYTE, 0, size);

      RtMidi.out_send_message.invoke(myPort.ptr, buffer, size);

      for (MidiMonitor monitor : monitors) {
        monitor.onMessage(myPort.name, msg);
      }
    } catch (Throwable t) {
      logger.log(Level.WARNING, "Failed to send native MIDI message", t);
    }
  }

  public void close() {
    if (myPort != null) {
      synchronized (myPort) {
        myPort.activeSubscribers--;
      }
      myPort = null;
    }
  }
}
