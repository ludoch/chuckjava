package org.chuck.network;

import static org.junit.jupiter.api.Assertions.*;

import java.net.DatagramSocket;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.chuck.core.ChuckVM;
import org.junit.jupiter.api.Test;

public class OscEngineTest {

  private int findFreePort() throws Exception {
    try (DatagramSocket s = new DatagramSocket(0)) {
      return s.getLocalPort();
    }
  }

  @Test
  void testOscLoopbackAndBundleParsing() throws Exception {
    ChuckVM vm = new ChuckVM(44100, 2);
    int port = findFreePort();

    OscIn oscIn = new OscIn(vm);
    oscIn.port(port);
    oscIn.addAddress("/test/freq, f");
    oscIn.addAddress("/test/note, i s");

    CountDownLatch latch = new CountDownLatch(2);
    oscIn.addListener(e -> latch.countDown());

    OscOut oscOut = new OscOut();
    oscOut.dest("127.0.0.1", port);

    // Send simple message 1
    oscOut.start("/test/freq").add(440.0f).send();

    // Send bundle containing message 2 and an ignored message
    OscBundle bundle = new OscBundle();
    OscMsg m1 = new OscMsg();
    m1.address = "/test/note";
    m1.addInt(60);
    m1.addString("C4");
    bundle.add(m1);

    OscMsg mIgnored = new OscMsg();
    mIgnored.address = "/ignored/path";
    mIgnored.addFloat(1.23f);
    bundle.add(mIgnored);

    oscOut.send(bundle);

    boolean received = latch.await(3, TimeUnit.SECONDS);
    assertTrue(received, "Should receive both /test/freq and /test/note within 3 seconds");

    OscMsg r1 = new OscMsg();
    assertTrue(oscIn.recv(r1), "Should pop first message from queue");
    assertEquals("/test/freq", r1.address());
    assertEquals(",f", r1.typetag());
    assertEquals(440.0f, r1.getFloat(0), 0.001f);

    OscMsg r2 = new OscMsg();
    assertTrue(oscIn.recv(r2), "Should pop second message from queue");
    assertEquals("/test/note", r2.address());
    assertEquals(",is", r2.typetag());
    assertEquals(60, r2.getInt(0));
    assertEquals("C4", r2.getString(1));

    assertFalse(oscIn.recv(new OscMsg()), "Queue should now be empty (/ignored/path filtered out)");

    oscIn.close();
  }
}
