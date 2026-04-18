package org.chuck;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class SequencerAudioTest {

  @Test
  public void testSequencerProducesSound() throws Exception {
    int srate = 44100;
    ChuckVM vm = new ChuckVM(srate, 2);

    // 1. Setup global pattern (Kick on step 0)
    ChuckArray pattern = new ChuckArray(ChuckType.ARRAY, 128);
    for (int i = 0; i < 128; i++) pattern.setInt(i, 0L);
    pattern.setInt(0, 1L); // Track 0 (Kick), Step 0
    vm.setGlobalObject("seq_pattern", pattern);

    // Probability 1.0 for all tracks
    ChuckArray probs = new ChuckArray(ChuckType.ARRAY, 8);
    for (int i = 0; i < 8; i++) probs.setFloat(i, 1.0);
    vm.setGlobalObject("seq_probability", probs);

    // 2. Run engine code (simplified but identical logic)
    String code =
        """
            SndBuf kick => dac;
            "examples/data/kick.wav" => kick.read;

            // Logic loop
            if (Machine.getGlobalObject("seq_pattern") $ int[] @=> int data[]) {
                if (data[0] > 0) {
                    0 => kick.pos;
                }
            }
            100::ms => now;
            """;

    int id = vm.run(code, "test_engine.ck");
    assertTrue(id > 0, "Engine failed to compile");

    // 3. Advance time and check peak
    double maxPeak = 0;
    for (int i = 0; i < 100; i++) {
      vm.advanceTime(441); // 10ms steps
      float out = Math.abs(vm.getChannelLastOut(0));
      if (out > maxPeak) maxPeak = out;
    }

    System.out.println("[TEST] Max Peak: " + maxPeak);
    assertTrue(maxPeak > 0.01, "Sequencer is silent! Peak was: " + maxPeak);
  }
}
