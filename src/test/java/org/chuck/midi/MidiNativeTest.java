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
  public void testSysexMsg() {
    MidiMsg msg = new MidiMsg();
    byte[] sysex = {(byte) 0xF0, 1, 2, (byte) 0xF7};
    msg.setData(sysex);

    assertEquals(4, msg.size());
    assertEquals(0xF0, msg.data1);
    assertEquals("MidiMsg[F0 01 02 F7]", msg.toString());

    byte[] out = msg.getData();
    assertArrayEquals(sysex, out);
  }

  @Test
  public void testCompiledApis() {
    System.out.println("Compiled MIDI APIs:");
    for (RtMidi.Api api : MidiIn.getCompiledApis()) {
      System.out.println("  - " + api);
    }
  }

  @Test
  public void testOpenByName() {
    ChuckVM vm = new ChuckVM(44100);
    MidiIn min = new MidiIn(vm);

    // This port probably doesn't exist, so it should return false safely
    boolean result = min.open("NonExistentMidiDevice12345");
    assertFalse(result, "Should not open a non-existent device");

    MidiOut mout = new MidiOut();
    int outResult = mout.open("NonExistentMidiDevice12345");
    assertEquals(0, outResult, "Should not open a non-existent device");
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
