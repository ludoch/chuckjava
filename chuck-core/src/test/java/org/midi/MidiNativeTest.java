package org.chuck.midi;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;
import org.rtmidijava.RtMidi;

public class MidiNativeTest {

  @Test
  public void testRtMidiAvailability() {
    // This test just prints status, doesn't fail if library is missing
    System.out.println("RtMidi Available: true"); // rtmidijava is pure java, it's always available
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
  public void testNumAndName() {
    ChuckVM vm = new ChuckVM(44100);
    MidiIn min = new MidiIn(vm);
    int numIn = min.num();
    assertTrue(numIn >= 0);
    if (numIn > 0) {
      assertNotNull(min.name(0));
    }

    MidiOut mout = new MidiOut();
    int numOut = mout.num();
    assertTrue(numOut >= 0);
    if (numOut > 0) {
      assertNotNull(mout.name(0));
    }
  }

  @Test
  public void testMidiInNative() {
    ChuckVM vm = new ChuckVM(44100);
    MidiIn min = new MidiIn(vm);
    assertTrue(min.isNative());

    // We can't easily test actual MIDI input without a virtual loopback
    // but we can check if it opens/closes without crashing
    if (min.num() > 0) {
      min.open(0);
      min.close();
    }
  }

  @Test
  public void testMidiOutNative() {
    MidiOut mout = new MidiOut();
    assertTrue(mout.isNative());

    if (mout.num() > 0) {
      mout.open(0);

      MidiMsg msg = new MidiMsg();
      msg.data1 = 0x90; // Note on
      msg.data2 = 60; // Middle C
      msg.data3 = 100; // Velocity

      mout.send(msg);
      mout.close();
    }
  }
}
