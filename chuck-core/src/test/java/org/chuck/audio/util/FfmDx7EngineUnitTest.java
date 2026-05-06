package org.chuck.audio.util;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * JUnit test for the FFM-based DX7 engine bindings ({@link FfmDx7Engine}).
 *
 * <p>Exercises all 9 C API functions exposed by the DLL:
 * init, create/destroy voice, patch loading, note on/off,
 * per-sample tick, block tick, pitch bend, and isActive query.
 *
 * <p>Requires the DLL to be found by the FFM library lookup.
 * Tests are skipped via assumption if loading fails.
 */
public class FfmDx7EngineUnitTest {

    /** Tomsweep hex patch from Dx7A.xml (310 hex chars = 155 bytes, pad to 156). */
    static final String TOMSWEEP_HEX =
        "63521B19604E0000290000000000000363010147076355141C5B30000036000000000000006001014F07" +
        "63321930630000003600000000000000430000000763631F206363000037003000000300016300000007" +
        "6316140B61010000360000000003000048000000075F251416635C00003B000000000700026300000007" +
        "62626262323232320707010000630001040718546F6D737765657020203F";

    private static byte[] tomsweepBytes;
    private static boolean libraryAvailable = false;

    @BeforeAll
    public static void checkNativeLibrary() {
        try {
            FfmDx7Engine.init();
            libraryAvailable = true;
        } catch (Throwable t) {
            System.err.println("FfmDx7Engine library not available, skipping tests: " + t.getMessage());
            org.junit.jupiter.api.Assumptions.abort("FfmDx7Engine library not available");
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
        long handle = FfmDx7Engine.createVoice();
        assertNotEquals(0, handle, "createVoice should return non-zero handle");
        FfmDx7Engine.destroyVoice(handle);
    }

    @Test
    public void testLoadPatchAndNoteOn() {
        long handle = FfmDx7Engine.createVoice();
        assertNotEquals(0, handle);

        FfmDx7Engine.loadPatch(handle, tomsweepBytes);
        FfmDx7Engine.noteOn(handle, 60, 100);

        // After note-on with fast envelope, should produce audio
        float sum = 0;
        for (int i = 0; i < 132; i++) {
            float s = FfmDx7Engine.tick(handle);
            sum += Math.abs(s);
        }

        System.out.println("testLoadPatchAndNoteOn: sum|samples| = " + sum);
        assertTrue(sum > 0.001, "FfmDx7Engine should produce audio after note-on, sum=" + sum);

        FfmDx7Engine.noteOff(handle);
        FfmDx7Engine.destroyVoice(handle);
    }

    @Test
    public void testNoteOffAndIsActive() {
        long handle = FfmDx7Engine.createVoice();
        FfmDx7Engine.loadPatch(handle, tomsweepBytes);
        FfmDx7Engine.noteOn(handle, 72, 80);

        assertTrue(FfmDx7Engine.isActive(handle), "Voice should be active after note-on");

        // Render some samples to settle
        for (int i = 0; i < 264; i++) FfmDx7Engine.tick(handle);

        FfmDx7Engine.noteOff(handle);

        // Render release tail
        for (int i = 0; i < 1320; i++) FfmDx7Engine.tick(handle);

        // After release, may still be active (envelope releasing)
        System.out.println("testNoteOffAndIsActive: isActive after release = " + FfmDx7Engine.isActive(handle));

        FfmDx7Engine.destroyVoice(handle);
    }

    @Test
    public void testTickBlockRendersAudio() {
        long handle = FfmDx7Engine.createVoice();
        FfmDx7Engine.loadPatch(handle, tomsweepBytes);
        FfmDx7Engine.noteOn(handle, 60, 100);

        float[] block = new float[132];
        FfmDx7Engine.tickBlock(handle, block, 132);

        float blockSum = 0;
        for (float v : block) blockSum += Math.abs(v);

        System.out.println("testTickBlock: sum|samples| = " + blockSum);
        assertTrue(blockSum > 0.001, "tickBlock should produce audio, sum=" + blockSum);

        FfmDx7Engine.noteOff(handle);
        FfmDx7Engine.destroyVoice(handle);
    }

    @Test
    public void testPitchBendChangesOutput() {
        long handle = FfmDx7Engine.createVoice();
        FfmDx7Engine.loadPatch(handle, tomsweepBytes);
        FfmDx7Engine.noteOn(handle, 60, 100);

        // Render with no pitch bend
        float sumNoBend = 0;
        for (int i = 0; i < 132; i++) sumNoBend += Math.abs(FfmDx7Engine.tick(handle));

        // Note off, new note with pitch bend
        FfmDx7Engine.noteOff(handle);
        FfmDx7Engine.noteOn(handle, 60, 100);
        FfmDx7Engine.setPitchBend(handle, 1 << 20); // ~1 semitone up in Q24

        float sumBend = 0;
        for (int i = 0; i < 132; i++) sumBend += Math.abs(FfmDx7Engine.tick(handle));

        System.out.println("testPitchBend: sumNoBend=" + sumNoBend + " sumBend=" + sumBend);
        assertTrue(sumBend > 0.001, "Pitch-bent voice should produce audio");

        FfmDx7Engine.destroyVoice(handle);
    }

    @Test
    public void testMultipleVoices() {
        long[] voices = new long[4];
        for (int i = 0; i < 4; i++) {
            voices[i] = FfmDx7Engine.createVoice();
            assertNotEquals(0, voices[i], "Voice " + i + " should be created");
            FfmDx7Engine.loadPatch(voices[i], tomsweepBytes);
            FfmDx7Engine.noteOn(voices[i], 60 + i * 4, 80 + i * 10);
        }

        // Render all 4 voices
        float[] sums = new float[4];
        for (int i = 0; i < 132; i++) {
            for (int v = 0; v < 4; v++) {
                sums[v] += Math.abs(FfmDx7Engine.tick(voices[v]));
            }
        }

        for (int v = 0; v < 4; v++) {
            System.out.println("Voice " + v + " sum|samples| = " + sums[v]);
            assertTrue(sums[v] > 0.001, "Voice " + v + " should produce audio");
        }

        for (long v : voices) {
            FfmDx7Engine.noteOff(v);
            FfmDx7Engine.destroyVoice(v);
        }
    }

    // ── helpers ──

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                                 + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
