package org.chuck.core;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.*;

import org.junit.jupiter.api.Test;

public class SerialIOTest {

  private static boolean isNativeLibAvailable() {
    try {
      com.fazecast.jSerialComm.SerialPort.getCommPorts();
      return true;
    } catch (Throwable t) {
      // Catches UnsatisfiedLinkError, ExceptionInInitializerError (wrapping
      // FileNotFoundException on locked/wrong-arch cached DLL), and NoClassDefFoundError
      // on subsequent calls after a failed static initializer.
      return false;
    }
  }

  @Test
  public void testSerialIOInstantiation() {
    SerialIO serial = new SerialIO();
    assertNotNull(serial);
    serial.close();
  }

  @Test
  public void testSerialIOList() {
    assumeTrue(isNativeLibAvailable(), "jSerialComm native library not available on this platform");
    String[] ports = SerialIO.list();
    assertNotNull(ports);
    // Even if no ports are found, it should be an empty array or list of available ones
    System.out.println("Available ports: " + String.join(", ", ports));
  }
}
