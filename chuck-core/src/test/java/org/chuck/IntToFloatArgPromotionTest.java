package org.chuck;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

/**
 * Regression test: passing an int argument to a float parameter must resolve the function (ChucK's
 * implicit int-&gt;float promotion). Previously such calls failed to match the typed function key
 * and silently degraded to a null-object {@code CallMethod("unknown")}, throwing a runtime
 * NullPointerException — which broke any function with a float param plus a locally declared UGen
 * (e.g. the comparison/06_event_broadcast.ck and 07_shred_spork.ck parity cases).
 */
public class IntToFloatArgPromotionTest {

  private String runAndCapture(String code) throws Exception {
    ChuckVM vm = new ChuckVM(44100);
    AtomicReference<String> output = new AtomicReference<>("");
    vm.addPrintListener(s -> output.updateAndGet(old -> old + s));
    vm.run(code, "test");
    long start = System.currentTimeMillis();
    while (vm.getActiveShredCount() > 0 && (System.currentTimeMillis() - start) < 3000) {
      vm.advanceTime(4410);
      Thread.sleep(1);
    }
    vm.shutdown();
    return output.get();
  }

  @Test
  public void intArgToFloatParamWithLocalUGen() throws Exception {
    // Before the fix this threw NullPointerException and produced no output.
    String code =
        "fun void p(float f){ SinOsc s => blackhole; f => s.freq; <<< s.freq() >>>; }\n" + "p(440);";
    String out = runAndCapture(code);
    assertTrue(out.contains("440.0"), "Expected the UGen freq 440.0 but got: '" + out + "'");
  }

  @Test
  public void intArgIsPromotedToFloatValue() throws Exception {
    // Verify the promoted argument is a real float, not a misread int slot.
    String out = runAndCapture("fun void p(float f){ <<< f * 2.0 >>>; }\np(21);");
    assertTrue(out.contains("42.0"), "Expected 42.0 but got: '" + out + "'");
  }

  @Test
  public void mixedIntAndStringArgs() throws Exception {
    // float-then-string params with an int first arg — the 06/07 parity case shape.
    String code =
        "fun void play(float f, string name){ SinOsc s => blackhole; f => s.freq;"
            + " <<< name, s.freq() >>>; }\n"
            + "play(440, \"hi\");";
    String out = runAndCapture(code);
    assertTrue(out.contains("hi"), "Expected 'hi' in output but got: '" + out + "'");
    assertTrue(out.contains("440.0"), "Expected 440.0 in output but got: '" + out + "'");
  }
}
