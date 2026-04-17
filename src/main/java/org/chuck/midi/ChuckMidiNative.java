package org.chuck.midi;

import java.lang.foreign.*;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.chuck.core.ChuckEvent;
import org.chuck.core.ChuckVM;
import org.rtmidijava.RtMidi;
import org.rtmidijava.RtMidiFactory;

/** Enhanced Native MIDI input using RtMidiJava via FFM. */
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

  private static final ConcurrentHashMap<String, List<MidiOut>> thruRoutes =
      new ConcurrentHashMap<>();

  public static void addThruRoute(String inputName, MidiOut output) {
    thruRoutes.computeIfAbsent(inputName, k -> new CopyOnWriteArrayList<>()).add(output);
  }

  public static void removeThruRoute(String inputName, MidiOut output) {
    List<MidiOut> list = thruRoutes.get(inputName);
    if (list != null) list.remove(output);
  }

  // --- Shared Port Management ---
  private static class SharedPort {
    org.rtmidijava.RtMidiIn midiIn;
    String name = "unopened";
    final java.util.List<ChuckMidiNative> subscribers = new CopyOnWriteArrayList<>();

    void onMidiMessage(double timestamp, MemorySegment message) {
      byte[] raw = message.toArray(ValueLayout.JAVA_BYTE);

      for (ChuckMidiNative sub : subscribers) {
        MidiMsg msg = new MidiMsg();
        msg.when = timestamp;
        msg.setData(raw);
        sub.queue.addLast(msg);
        sub.event.broadcast(sub.vm);

        // Forward to MidiPoly targets connected via 'min => poly'
        if (sub.event instanceof MidiIn min) {
          for (MidiPoly target : min.getTargets()) {
            target.onMessage(msg);
          }
        }
      }

      for (MidiMonitor monitor : monitors) {
        MidiMsg msg = new MidiMsg();
        msg.when = timestamp;
        msg.setData(raw);
        monitor.onMessage(name, msg);
      }

      List<MidiOut> targets = thruRoutes.get(name);
      if (targets != null) {
        MidiMsg msg = new MidiMsg();
        msg.when = timestamp;
        msg.setData(raw);
        for (MidiOut out : targets) {
          out.send(msg);
        }
      }
    }
  }

  private static final ConcurrentHashMap<String, SharedPort> sharedPorts =
      new ConcurrentHashMap<>();
  // -----------------------------

  private final ChuckVM vm;
  private final ChuckEvent event;
  private final ConcurrentLinkedDeque<MidiMsg> queue;
  private SharedPort myPort = null;

  /** Opens a MIDI input port for global monitoring (IDE-level). */
  public static void openGlobalMonitor(int portNumber) {
    String portKey = RtMidi.Api.UNSPECIFIED + ":" + portNumber;
    sharedPorts.computeIfAbsent(
        portKey,
        k -> {
          SharedPort sp = new SharedPort();
          try {
            sp.midiIn = RtMidiFactory.createDefaultIn();
            sp.name = sp.midiIn.getPortName(portNumber);
            sp.midiIn.setFastCallback(sp::onMidiMessage);
            sp.midiIn.openPort(portNumber, "ChucK-Java Monitor");

            logger.info("Global MIDI Monitor opened port " + portNumber + " (" + sp.name + ")");
          } catch (Throwable t) {
            logger.log(Level.SEVERE, "Failed to initialize global MIDI monitor", t);
          }
          return sp;
        });
  }

  public ChuckMidiNative(ChuckVM vm, ChuckEvent event, ConcurrentLinkedDeque<MidiMsg> queue) {
    this.vm = vm;
    this.event = event;
    this.queue = queue;
  }

  public void open(int portNumber) {
    open(portNumber, RtMidi.Api.UNSPECIFIED);
  }

  public void open(int portNumber, RtMidi.Api api) {
    String portKey = api + ":" + portNumber;

    try {
      SharedPort shared =
          sharedPorts.computeIfAbsent(
              portKey,
              k -> {
                SharedPort sp = new SharedPort();
                try {
                  sp.midiIn =
                      (api == RtMidi.Api.UNSPECIFIED)
                          ? RtMidiFactory.createDefaultIn()
                          : RtMidiFactory.createIn(api);

                  sp.name = sp.midiIn.getPortName(portNumber);
                  sp.midiIn.setFastCallback(sp::onMidiMessage);
                  sp.midiIn.openPort(portNumber, "ChucK-Java Input");

                  logger.info(
                      "Native MIDI Input opened shared port " + portNumber + " (API=" + api + ")");
                } catch (Throwable t) {
                  logger.log(Level.SEVERE, "Failed to initialize shared native MIDI input", t);
                }
                return sp;
              });

      if (shared.midiIn != null && shared.midiIn.isPortOpen()) {
        this.myPort = shared;
        shared.subscribers.add(this);
      }
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to open native MIDI input", t);
    }
  }

  public void openVirtual(String name) {
    try {
      SharedPort sp = new SharedPort();
      sp.midiIn = RtMidiFactory.createDefaultIn();
      sp.midiIn.setFastCallback(sp::onMidiMessage);
      sp.midiIn.openVirtualPort(name);
      sp.name = name;
      logger.info("Native Virtual MIDI Input created: " + name);

      this.myPort = sp;
      sp.subscribers.add(this);
    } catch (Throwable t) {
      logger.log(Level.SEVERE, "Failed to create virtual MIDI input", t);
    }
  }

  public void ignoreTypes(boolean midiSysex, boolean midiTime, boolean midiSense) {
    if (myPort == null || myPort.midiIn == null) return;
    myPort.midiIn.ignoreTypes(midiSysex, midiTime, midiSense);
  }

  public void close() {
    if (myPort != null) {
      myPort.subscribers.remove(this);
      if (myPort.subscribers.isEmpty()) {
        myPort.midiIn.closePort();
        sharedPorts.values().remove(myPort);
      }
      myPort = null;
    }
  }
}
