package org.chuck.audio.stk;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class Modal4Test {

  private ChuckVM vm;

  @BeforeEach
  void setUp() {
    System.setProperty("chuck.audio.dummy", "true");
    vm = new ChuckVM(44100, 1); // Mono
  }

  @AfterEach
  void tearDown() {
    if (vm != null) vm.shutdown();
  }

  @Test
  void testModalStrikeAndDecayPreservation() {
    Modal4 instrument = new Modal4();
    instrument.preset(Modal4.PRESET_MARIMBA);
    instrument.freq(440.0f);
    instrument.amp(1.0f);

    // Strike the marimba with full force
    instrument.strike(1.0f);

    // Render first segment of the decay tail
    float[] tail1 = new float[2000];
    instrument.tick(tail1, 0, 2000, 0L);

    double energy1 = 0.0;
    boolean hasAudio = false;
    for (float val : tail1) {
      if (Math.abs(val) > 0.0001f) {
        hasAudio = true;
      }
      energy1 += val * val;
    }

    assertTrue(hasAudio, "Modal4 strike must produce active physical resonance sound waves");

    // Render second segment of the decay tail
    float[] tail2 = new float[2000];
    instrument.tick(tail2, 0, 2000, 2000L);

    double energy2 = 0.0;
    for (float val : tail2) {
      energy2 += val * val;
    }

    System.out.println("=== MODAL SYNTHESIS DECAY TAIL ENERGY ===");
    System.out.println("  Initial tail segment energy: " + energy1);
    System.out.println("  Decayed tail segment energy: " + energy2);
    System.out.println("=========================================");

    // Physical resonance modes must decay exponentially!
    assertTrue(
        energy2 < energy1,
        "Modal physical resonances must naturally decay over time: " + energy2 + " vs " + energy1);
  }

  @Test
  void testStickHardnessTransientFrequencyChange() {
    // ── Soft Mallet Strike ──
    Modal4 softInst = new Modal4();
    softInst.preset(Modal4.PRESET_VIBRAPHONE);
    softInst.freq(220.0f);
    softInst.hardness(0.1f); // Soft mallet!
    softInst.amp(1.0f);
    softInst.strike(1.0f);

    float[] softOut = new float[100]; // capture the strike click transient!
    softInst.tick(softOut, 0, 100, 0L);

    // Calculate maximum rate of change (first derivative) to measure soft click low-frequency
    // weight
    double maxSoftDelta = 0.0;
    for (int i = 1; i < softOut.length; i++) {
      maxSoftDelta = Math.max(maxSoftDelta, Math.abs(softOut[i] - softOut[i - 1]));
    }

    // ── Hard Mallet Strike ──
    Modal4 hardInst = new Modal4();
    hardInst.preset(Modal4.PRESET_VIBRAPHONE);
    hardInst.freq(220.0f);
    hardInst.hardness(0.9f); // Hard mallet!
    hardInst.amp(1.0f);
    hardInst.strike(1.0f);

    float[] hardOut = new float[100];
    hardInst.tick(hardOut, 0, 100, 0L);

    double maxHardDelta = 0.0;
    for (int i = 1; i < hardOut.length; i++) {
      maxHardDelta = Math.max(maxHardDelta, Math.abs(hardOut[i] - hardOut[i - 1]));
    }

    System.out.println("=== MODAL MALLET STICK HARDNESS TRANSIENT CLICK ===");
    System.out.println("  Soft mallet maximum click delta: " + maxSoftDelta);
    System.out.println("  Hard mallet maximum click delta: " + maxHardDelta);
    System.out.println("==================================================");

    // A hard mallet must generate a dramatically sharper high-frequency click/transient transient!
    assertTrue(
        maxHardDelta > maxSoftDelta * 1.5,
        "Hard mallet strike must generate a significantly sharper high-frequency transient click: "
            + maxHardDelta
            + " vs "
            + maxSoftDelta);
  }

  @Test
  void testPresetDecayProportions() {
    // ── Wood Block (Shortest Sustain) ──
    Modal4 block = new Modal4();
    block.preset(Modal4.PRESET_WOODBLOCK);
    block.freq(400.0f);
    block.amp(1.0f);
    block.strike(1.0f);

    float[] blockOut = new float[3000];
    block.tick(blockOut, 0, 3000, 0L);

    double blockEnergy = 0.0;
    for (float val : blockOut) {
      blockEnergy += val * val;
    }

    // ── Vibraphone (Longest Sustain) ──
    Modal4 vibra = new Modal4();
    vibra.preset(Modal4.PRESET_VIBRAPHONE);
    vibra.freq(400.0f);
    vibra.amp(1.0f);
    vibra.strike(1.0f);

    float[] vibraOut = new float[3000];
    vibra.tick(vibraOut, 0, 3000, 0L);

    double vibraEnergy = 0.0;
    for (float val : vibraOut) {
      vibraEnergy += val * val;
    }

    // Vibraphones have a long metal decay sustain, wood blocks have a quick wooden dead bounce!
    assertTrue(
        vibraEnergy > blockEnergy * 5.0,
        "Vibraphones must sustain significantly longer than wood blocks: "
            + vibraEnergy
            + " vs "
            + blockEnergy);
  }
}
