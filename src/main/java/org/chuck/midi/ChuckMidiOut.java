package org.chuck.midi;

import java.util.ArrayList;
import java.util.List;
import javax.sound.midi.*;

/** Real MIDI output using javax.sound.midi (built into JDK). */
public class ChuckMidiOut {
  private MidiDevice device;
  private Receiver receiver;

  public static List<String> listOutputDevices() {
    List<String> names = new ArrayList<>();
    for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
      try {
        MidiDevice dev = MidiSystem.getMidiDevice(info);
        if (dev.getMaxReceivers() != 0) { // output-capable (JDK receives from us)
          names.add(info.getName());
        }
      } catch (MidiUnavailableException ignored) {
      }
    }
    return names;
  }

  public boolean open(int portIndex) {
    close();
    List<MidiDevice.Info> outputs = new ArrayList<>();
    for (MidiDevice.Info info : MidiSystem.getMidiDeviceInfo()) {
      try {
        MidiDevice dev = MidiSystem.getMidiDevice(info);
        if (dev.getMaxReceivers() != 0) outputs.add(info);
      } catch (MidiUnavailableException ignored) {
      }
    }
    if (portIndex < 0 || portIndex >= outputs.size()) return false;
    try {
      device = MidiSystem.getMidiDevice(outputs.get(portIndex));
      device.open();
      receiver = device.getReceiver();
      return true;
    } catch (MidiUnavailableException e) {
      return false;
    }
  }

  public void send(MidiMsg msg) {
    if (receiver == null) return;
    try {
      byte[] raw = msg.getData();
      ShortMessage sm = new ShortMessage();
      if (raw.length >= 3) {
        sm.setMessage(raw[0] & 0xFF, raw[1] & 0xFF, raw[2] & 0xFF);
      } else if (raw.length == 2) {
        sm.setMessage(raw[0] & 0xFF, raw[1] & 0xFF, 0);
      } else if (raw.length == 1) {
        sm.setMessage(raw[0] & 0xFF, 0, 0);
      }
      receiver.send(sm, -1);
    } catch (InvalidMidiDataException ignored) {
    }
  }

  public void close() {
    if (receiver != null) {
      receiver.close();
      receiver = null;
    }
    if (device != null) {
      device.close();
      device = null;
    }
  }
}
