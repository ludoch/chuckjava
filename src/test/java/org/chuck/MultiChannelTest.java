package org.chuck;

import static org.junit.jupiter.api.Assertions.*;

import org.chuck.audio.*;
import org.chuck.audio.analysis.*;
import org.chuck.audio.filter.*;
import org.chuck.audio.fx.*;
import org.chuck.audio.osc.*;
import org.chuck.audio.stk.*;
import org.chuck.audio.util.*;
import org.junit.jupiter.api.Test;

public class MultiChannelTest {

  @Test
  public void testPan4() {
    Pan4 p = new Pan4();
    Step s = new Step();
    s.next(1.0);
    s.chuck(p);

    // Pan center-ish (maps to middle of 0..3)
    p.pan(0.0f);
    // Maps 0.0 to index 1.5 -> i0=1, i1=2, frac=0.5
    p.tick(0);

    assertEquals(0.0f, p.getChannelLastOut(0), 1e-6);
    assertEquals(0.5f, p.getChannelLastOut(1), 1e-6);
    assertEquals(0.5f, p.getChannelLastOut(2), 1e-6);
    assertEquals(0.0f, p.getChannelLastOut(3), 1e-6);

    // Pan left
    p.pan(-1.0f); // index 0
    p.tick(1);
    assertEquals(1.0f, p.getChannelLastOut(0), 1e-6);
    assertEquals(0.0f, p.getChannelLastOut(1), 1e-6);

    // Pan right
    p.pan(1.0f); // index 3
    p.tick(2);
    assertEquals(0.0f, p.getChannelLastOut(2), 1e-6);
    assertEquals(1.0f, p.getChannelLastOut(3), 1e-6);
  }

  @Test
  public void testMix4() {
    Mix4 m = new Mix4();
    Step s = new Step();
    s.next(1.0);
    s.chuck(m);

    m.tick(0);
    for (int i = 0; i < 4; i++) {
      assertEquals(1.0f, m.getChannelLastOut(i), 1e-6);
    }
  }
}
