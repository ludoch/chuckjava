package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ChuckStdTest {

  @Test
  public void testMtof() {
    // MIDI note 69 is A4 (440 Hz)
    assertEquals(440.0, Std.mtof(69.0), 1e-4);

    // MIDI note 60 is Middle C (261.625 Hz)
    assertEquals(261.6255, Std.mtof(60.0), 1e-4);
  }

  @Test
  public void testFtom() {
    // Inverse operations
    assertEquals(69.0, Std.ftom(440.0), 1e-4);
    assertEquals(60.0, Std.ftom(261.6255), 1e-4);
  }

  @Test
  public void testItoa() {
    assertEquals("42", Std.itoa(42));
    assertEquals("-7", Std.itoa(-7));
    assertEquals("0", Std.itoa(0));
  }

  @Test
  public void testFtoa() {
    assertEquals("3.14", Std.ftoa(3.14159, 2));
    assertEquals("1.0000", Std.ftoa(1.0, 4));
    assertEquals("-0", Std.ftoa(-0.0, 0));
  }

  @Test
  public void testFtoi() {
    assertEquals(3L, Std.ftoi(3.9));
    assertEquals(-2L, Std.ftoi(-2.7));
    assertEquals(0L, Std.ftoi(0.0));
  }

  @Test
  public void testSgn() {
    assertEquals(1.0, Std.sgn(5.0), 1e-9);
    assertEquals(-1.0, Std.sgn(-3.0), 1e-9);
    assertEquals(0.0, Std.sgn(0.0), 1e-9);
  }

  @Test
  public void testScalef() {
    // Map 0.5 from [0,1] to [0,100] → 50
    assertEquals(50.0, Std.scalef(0.5, 0.0, 1.0, 0.0, 100.0), 1e-9);
    // Map 0 from [0,10] to [-1,1] → -1
    assertEquals(-1.0, Std.scalef(0.0, 0.0, 10.0, -1.0, 1.0), 1e-9);
    // Equal src range returns dstMin
    assertEquals(5.0, Std.scalef(3.0, 3.0, 3.0, 5.0, 10.0), 1e-9);
  }

  @Test
  public void testAbs() {
    assertEquals(5L, Std.abs(-5L));
    assertEquals(5L, Std.abs(5L));
    assertEquals(0L, Std.abs(0L));
  }

  @Test
  public void testStringTokenizer() {
    StringTokenizer tok = new StringTokenizer();
    tok.set("hello world foo");
    assertTrue(tok.more());
    assertEquals("hello", tok.next());
    assertEquals("world", tok.next());
    assertTrue(tok.more());
    assertEquals("foo", tok.next());
    assertFalse(tok.more());
    assertEquals("", tok.next()); // past end
    tok.reset();
    assertTrue(tok.more());
  }

  @Test
  public void testChuckEventCanWait() {
    ChuckEvent evt = new ChuckEvent();
    assertTrue(evt.can_wait());
  }

  @Test
  public void testStdMtofInstruction() {
    ChuckVM vm = new ChuckVM(44100);
    ChuckCode code = new ChuckCode("TestMtof");
    code.addInstruction(new PushFloat(69.0));
    code.addInstruction(new StdMtof());

    ChuckShred shred = new ChuckShred(code);
    vm.spork(shred);

    // Run shred
    vm.advanceTime(1);

    assertTrue(shred.isDone());
    // Verify result is on stack
    assertEquals(440.0, shred.reg.popDouble(), 1e-4);
  }
}
