package org.chuck.audio;

import java.util.ArrayList;
import java.util.List;
import org.chuck.audio.filter.*;
import org.chuck.audio.fx.*;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/** Automated DSP Precision Auditor. */
public class DspPrecisionAuditTest {

  private static final float SAMPLE_RATE = 44100.0f;

  @BeforeAll
  public static void setup() {
    new ChuckVM((int) SAMPLE_RATE);
  }

  @Test
  public void auditPrecision() {
    List<String> failures = new ArrayList<>();
    List<String> successes = new ArrayList<>();

    // 1. ResonZ at extreme settings
    ResonZ rz = new ResonZ(SAMPLE_RATE);
    rz.setFreq(10.0f); // Very low freq
    rz.setQ(50.0f); // High resonance
    checkSilenceConvergence(rz, "ResonZ (Extreme)", failures, successes);

    // 2. BiQuad at extreme settings
    BiQuad bq = new BiQuad(SAMPLE_RATE);
    bq.setPfreq(20.0); // Very low freq
    bq.setPrad(0.99); // High resonance radius
    checkSilenceConvergence(bq, "BiQuad (Extreme)", failures, successes);

    // 3. Upgraded ones (Should still pass)
    checkSilenceConvergence(new ShelfEQ(SAMPLE_RATE), "ShelfEQ (Upgraded)", failures, successes);
    checkSilenceConvergence(new SVFilter(SAMPLE_RATE), "SVFilter (Upgraded)", failures, successes);
    checkSilenceConvergence(new Comb(1000, false), "Comb (Upgraded)", failures, successes);
    checkSilenceConvergence(new AllPass(1000, false), "AllPass (Upgraded)", failures, successes);
    checkSilenceConvergence(new Echo(1000, SAMPLE_RATE), "Echo (Upgraded)", failures, successes);
    checkSilenceConvergence(new JCRev(SAMPLE_RATE), "JCRev (Upgraded)", failures, successes);

    System.err.println("\n--- DSP PRECISION AUDIT REPORT ---");
    if (!successes.isEmpty()) {
      System.err.println("PASSING:");
      for (String s : successes) System.err.println(" [OK] " + s);
    }

    if (!failures.isEmpty()) {
      System.err.println("\nAT RISK (Limit Cycle detected):");
      for (String f : failures) {
        System.err.println(" [!!] " + f);
      }
    }
    System.err.println("----------------------------------\n");
  }

  private void checkSilenceConvergence(
      ChuckUGen ugen, String name, List<String> failures, List<String> successes) {
    ugen.tick(1.0f, 0); // Impulse

    // Run for 20 seconds of silence
    for (int i = 0; i < 44100 * 20; i++) {
      ugen.tick(0.0f, i + 1);
    }

    float lastOut = Math.abs(ugen.lastOut);
    if (lastOut == 0.0f) {
      successes.add(name);
    } else {
      failures.add(String.format("%s: Internal state stuck at %e", name, lastOut));
    }
  }
}
