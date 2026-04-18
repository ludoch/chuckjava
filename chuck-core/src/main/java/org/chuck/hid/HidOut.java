package org.chuck.hid;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * ChucK HidOut — sends HID output (force-feedback, LEDs, etc.). This is a stub implementation: real
 * HID output requires platform-native USB HID report sending (not available in standard Java SE).
 * open() and send() report failure (return 0) so ChucK scripts can check the return value and
 * degrade gracefully.
 */
public class HidOut extends ChuckObject {
  private boolean opened = false;
  private int deviceNum = -1;

  public HidOut() {
    super(ChuckType.OBJECT);
  }

  /**
   * Attempt to open a HID output device by index. Always returns 0 (not supported) in this
   * implementation.
   */
  public long open(long num) {
    deviceNum = (int) num;
    // Stub: native HID output not supported on JVM without additional libraries
    opened = false;
    return 0L;
  }

  /** Close the device. */
  public void close() {
    opened = false;
    deviceNum = -1;
  }

  /** Send a HID output message. Always returns 0 (not supported) in this stub. */
  public long send(HidMsg msg) {
    return 0L;
  }

  /** Device name, empty if not opened. */
  public String name() {
    return opened ? "HidOut-" + deviceNum : "";
  }

  /** Device index passed to open(), or -1 if not opened. */
  public long num() {
    return deviceNum;
  }

  /** Returns 1 if successfully opened, 0 otherwise. */
  public long isOpen() {
    return opened ? 1L : 0L;
  }
}
