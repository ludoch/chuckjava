package org.chuck.samples;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import org.junit.jupiter.api.Test;

public class ResourceTest {
  @Test
  public void testExamplesExist() {
    InputStream is = getClass().getResourceAsStream("/examples/hanoi.ck");
    assertNotNull(is, "Could not find /examples/hanoi.ck in classpath");
  }

  @Test
  public void testDataExists() {
    InputStream is = getClass().getResourceAsStream("/examples/data/kick.wav");
    assertNotNull(is, "Could not find /examples/data/kick.wav in classpath");
  }
}
