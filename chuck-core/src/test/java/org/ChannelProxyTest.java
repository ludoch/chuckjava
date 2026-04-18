package org.chuck;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.chuck.audio.ChuckUGen;
import org.chuck.audio.util.Pan2;
import org.chuck.audio.util.Pan4;
import org.chuck.audio.util.Step;
import org.junit.jupiter.api.Test;

public class ChannelProxyTest {

  @Test
  public void testStereoProxies() {
    Pan2 p = new Pan2();
    p.pan(-1.0f); // All to left

    Step s = new Step();
    s.next(1.0);
    s.chuck(p);

    ChuckUGen left = p.left();
    ChuckUGen right = p.right();

    assertNotNull(left);
    assertNotNull(right);

    // Tick through proxies
    assertEquals(1.0f, left.tick(0), 1e-6);
    assertEquals(0.0f, right.tick(0), 1e-6);
  }

  @Test
  public void testChanProxy() {
    Pan4 p = new Pan4();
    p.pan(1.0f); // All to index 3

    Step s = new Step();
    s.next(1.0);
    s.chuck(p);

    ChuckUGen c3 = p.chan(3);
    ChuckUGen c0 = p.chan(0);

    assertEquals(1.0f, c3.tick(0), 1e-6);
    assertEquals(0.0f, c0.tick(0), 1e-6);
  }
}
