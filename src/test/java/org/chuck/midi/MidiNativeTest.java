package org.chuck.midi;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class MidiNativeTest {

  @Test
  public void testRtMidiAvailability() {
    // This test just prints status, doesn't fail if library is missing
    System.out.println("RtMidi Available: " + RtMidi.isAvailable());
  }

  @Test
  public void testListPorts() {
    String[] inNames = MidiIn.list();
    System.out.println("Native MIDI Input Ports (" + inNames.length + "):");
    for (String n : inNames) System.out.println("  - " + n);

    String[] outNames = MidiOut.list();
    System.out.println("MIDI Output Ports (" + outNames.length + "):");
    for (String n : outNames) System.out.println("  - " + n);
  }

  @Test
  public void testMidiInNative() {
    if (!RtMidi.isAvailable()) return;

    ChuckVM vm = new ChuckVM(44100);
    MidiIn min = new MidiIn(vm);
    assertTrue(min.isNative());

    // We can't easily test actual MIDI input without a virtual loopback
    // but we can check if it opens/closes without crashing
    min.open(0);
    min.close();
  }

  @Test
  public void testMidiOutNative() {
    if (!RtMidi.isAvailable()) return;

    MidiOut mout = new MidiOut();
    assertTrue(mout.isNative());

    mout.open(0);

    MidiMsg msg = new MidiMsg();
    msg.data1 = 0x90; // Note on
    msg.data2 = 60; // Middle C
    msg.data3 = 100; // Velocity

    mout.send(msg);
    mout.close();
  }
}
