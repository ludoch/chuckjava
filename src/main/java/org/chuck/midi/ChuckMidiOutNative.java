package org.chuck.midi;

import java.lang.foreign.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.rtmidijava.RtMidi;
import org.rtmidijava.RtMidiFactory;

/** Native MIDI output using RtMidiJava via FFM. Supports port sharing. */
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
    org.rtmidijava.RtMidiOut midiOut;
    String name = "unopened";
    int activeSubscribers = 0;
  }

  private static final ConcurrentHashMap<String, SharedPort> sharedPorts =
      new ConcurrentHashMap<>();
  // -----------------------------

  private SharedPort myPort = null;

  public ChuckMidiOutNative() {}

  public boolean open(int portNumber) {
    return open(portNumber, RtMidi.Api.UNSPECIFIED);
  }

  public boolean open(int portNumber, RtMidi.Api api) {
    String portKey = api + ":" + portNumber;

    try {
      SharedPort shared =
          sharedPorts.computeIfAbsent(
              portKey,
              k -> {
                SharedPort sp = new SharedPort();
                try {
                  sp.midiOut =
                      (api == RtMidi.Api.UNSPECIFIED)
                          ? RtMidiFactory.createDefaultOut()
                          : RtMidiFactory.createOut(api);

                  sp.name = sp.midiOut.getPortName(portNumber);
                  sp.midiOut.openPort(portNumber, "ChucK-Java Output");

                  logger.info(
                      "Native MIDI Output opened shared port " + portNumber + " (API=" + api + ")");
                } catch (Throwable t) {
                  logger.log(Level.SEVERE, "Failed to open shared native MIDI output", t);
                }
                return sp;
              });

      if (shared.midiOut != null && shared.midiOut.isPortOpen()) {
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
    try {
      SharedPort sp = new SharedPort();
      sp.midiOut = RtMidiFactory.createDefaultOut();
      sp.midiOut.openVirtualPort(name);
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
    if (myPort == null || myPort.midiOut == null) return;

    try {
      byte[] raw = msg.getData();
      myPort.midiOut.sendMessage(raw);

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
        if (myPort.activeSubscribers <= 0) {
          myPort.midiOut.closePort();
          sharedPorts.values().remove(myPort);
        }
      }
      myPort = null;
    }
  }
}
