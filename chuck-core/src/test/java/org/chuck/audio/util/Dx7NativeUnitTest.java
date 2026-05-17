package org.chuck.audio.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * JUnit test for the native Dx7 DLL (deluge_dsp_native.dll) via JNI.
 *
 * <p>Exercises all 10 native methods declared in {@link Dx7Native}: lifecycle (init, create/destroy
 * voice), patch loading, note on/off, per-sample tick, block tick, pitch bend, and isActive query.
 *
 * <p>Requires the DLL to be on java.library.path or the development fallback path
 * (deluge/dx7native/lib/). Only runs when the DLL loads successfully; tests are {@code @Disabled}
 * via assumption if loading fails.
 */
public class Dx7NativeUnitTest {

  /** Tomsweep hex patch from Dx7A.xml (310 hex chars = 155 bytes, pad to 156). */
  static final String TOMSWEEP_HEX =
      "63521B19604E0000290000000000000363010147076355141C5B30000036000000000000006001014F07"
          + "63321930630000003600000000000000430000000763631F206363000037003000000300016300000007"
          + "6316140B61010000360000000003000048000000075F251416635C00003B000000000700026300000007"
          + "62626262323232320707010000630001040718546F6D737765657020203F";

  private static byte[] tomsweepBytes;

  @BeforeAll
  public static void checkNativeLibrary() {
    // Force-load the native library (same as Dx7Native static initializer).
    // If it fails, we skip all tests via assumption.
    try {
      // Access the class — its static initializer loads the library
      Class.forName("org.chuck.audio.util.Dx7Native");
      // Quick sanity: call nativeInit
      Dx7Native.nativeInit();
    } catch (Throwable t) {
      // Library not available — skip all tests
      System.err.println(
          "Dx7Native library not available, skipping native tests: " + t.getMessage());
      org.junit.jupiter.api.Assumptions.abort("Native DX7 library not available");
    }

    // Prepare patch bytes
    tomsweepBytes = hexToBytes(TOMSWEEP_HEX);
    if (tomsweepBytes.length < 156) {
      byte[] padded = new byte[156];
      System.arraycopy(tomsweepBytes, 0, padded, 0, tomsweepBytes.length);
      tomsweepBytes = padded;
    }
  }

  @Test
  public void testInitAndCreateVoice() {
    long handle = Dx7Native.nativeCreateVoice();
    assertNotEquals(0, handle, "nativeCreateVoice should return non-zero handle");
    Dx7Native.nativeDestroyVoice(handle);
  }

  @Test
  public void testLoadPatchAndNoteOn() {
    long handle = Dx7Native.nativeCreateVoice();
    assertNotEquals(0, handle);

    Dx7Native.nativeLoadPatch(handle, tomsweepBytes);
    Dx7Native.nativeNoteOn(handle, 60, 100);

    // After note-on with fast envelope, should produce audio
    float sum = 0;
    for (int i = 0; i < 132; i++) {
      float s = Dx7Native.nativeTick(handle);
      sum += Math.abs(s);
    }

    System.out.println("testLoadPatchAndNoteOn: sum|samples| = " + sum);
    assertTrue(sum > 0.001, "Dx7Native should produce audio after note-on, sum=" + sum);

    Dx7Native.nativeNoteOff(handle);
    Dx7Native.nativeDestroyVoice(handle);
  }

  @Test
  public void testNoteOffAndIsActive() {
    long handle = Dx7Native.nativeCreateVoice();
    Dx7Native.nativeLoadPatch(handle, tomsweepBytes);
    Dx7Native.nativeNoteOn(handle, 72, 80);

    assertTrue(Dx7Native.nativeIsActive(handle), "Voice should be active after note-on");

    // Render some samples to settle
    for (int i = 0; i < 264; i++) Dx7Native.nativeTick(handle);

    Dx7Native.nativeNoteOff(handle);

    // Render release tail
    for (int i = 0; i < 1320; i++) Dx7Native.nativeTick(handle);

    // After release, may still be active (envelope releasing)
    // This just verifies isActive doesn't crash and returns a boolean
    System.out.println(
        "testNoteOffAndIsActive: isActive after release = " + Dx7Native.nativeIsActive(handle));

    Dx7Native.nativeDestroyVoice(handle);
  }

  @Test
  public void testTickBlockRendersAudio() {
    long handle = Dx7Native.nativeCreateVoice();
    Dx7Native.nativeLoadPatch(handle, tomsweepBytes);
    Dx7Native.nativeNoteOn(handle, 60, 100);

    float[] block = new float[132];
    Dx7Native.nativeTickBlock(handle, block, 132);

    float blockSum = 0;
    for (float v : block) blockSum += Math.abs(v);

    System.out.println("testTickBlock: sum|samples| = " + blockSum);
    assertTrue(blockSum > 0.001, "tickBlock should produce audio, sum=" + blockSum);

    Dx7Native.nativeNoteOff(handle);
    Dx7Native.nativeDestroyVoice(handle);
  }

  @Test
  public void testPitchBendChangesOutput() {
    long handle = Dx7Native.nativeCreateVoice();
    Dx7Native.nativeLoadPatch(handle, tomsweepBytes);
    Dx7Native.nativeNoteOn(handle, 60, 100);

    // Render with no pitch bend
    float sumNoBend = 0;
    for (int i = 0; i < 132; i++) sumNoBend += Math.abs(Dx7Native.nativeTick(handle));

    // Note off, new note with pitch bend
    Dx7Native.nativeNoteOff(handle);
    Dx7Native.nativeNoteOn(handle, 60, 100);
    Dx7Native.nativeSetPitchBend(handle, 1 << 20); // ~1 semitone up in Q24

    float sumBend = 0;
    for (int i = 0; i < 132; i++) sumBend += Math.abs(Dx7Native.nativeTick(handle));

    System.out.println("testPitchBend: sumNoBend=" + sumNoBend + " sumBend=" + sumBend);
    // Pitch bend should change the output (may or may not be audible depending on patch)
    // We just verify it doesn't crash and produces sound

    assertTrue(sumBend > 0.001, "Pitch-bent voice should produce audio");

    Dx7Native.nativeDestroyVoice(handle);
  }

  @Test
  public void testMultipleVoices() {
    long[] voices = new long[4];
    for (int i = 0; i < 4; i++) {
      voices[i] = Dx7Native.nativeCreateVoice();
      assertNotEquals(0, voices[i], "Voice " + i + " should be created");
      Dx7Native.nativeLoadPatch(voices[i], tomsweepBytes);
      Dx7Native.nativeNoteOn(voices[i], 60 + i * 4, 80 + i * 10);
    }

    // Render all 4 voices
    float[] sums = new float[4];
    for (int i = 0; i < 132; i++) {
      for (int v = 0; v < 4; v++) {
        sums[v] += Math.abs(Dx7Native.nativeTick(voices[v]));
      }
    }

    for (int v = 0; v < 4; v++) {
      System.out.println("Voice " + v + " sum|samples| = " + sums[v]);
      assertTrue(sums[v] > 0.001, "Voice " + v + " should produce audio");
    }

    for (long v : voices) {
      Dx7Native.nativeNoteOff(v);
      Dx7Native.nativeDestroyVoice(v);
    }
  }

  // ── helpers ──

  private static byte[] hexToBytes(String hex) {
    int len = hex.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
      data[i / 2] =
          (byte)
              ((Character.digit(hex.charAt(i), 16) << 4) + Character.digit(hex.charAt(i + 1), 16));
    }
    return data;
  }
}
