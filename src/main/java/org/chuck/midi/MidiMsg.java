package org.chuck.midi;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Holds one MIDI message. Matches ChucK's MidiMsg convention (data1, data2, data3) but also
 * supports variable-length messages (Sysex) via an internal byte array.
 */
public class MidiMsg extends ChuckObject {
  public int data1 = 0;
  public int data2 = 0;
  public int data3 = 0;

  /** Optional buffer for variable-length messages (Sysex). */
  private byte[] data = null;

  public MidiMsg() {
    super(ChuckType.OBJECT);
  }

  /** Sets the raw data buffer for this message. */
  public void setData(byte[] data) {
    this.data = data;
    if (data != null && data.length > 0) {
      this.data1 = data[0] & 0xFF;
      this.data2 = data.length > 1 ? data[1] & 0xFF : 0;
      this.data3 = data.length > 2 ? data[2] & 0xFF : 0;
    }
  }

  /** Returns the raw bytes of this message. */
  public byte[] getData() {
    if (data != null) return data;
    return new byte[] {(byte) data1, (byte) data2, (byte) data3};
  }

  /** Returns the length of this message. */
  public int size() {
    return data != null ? data.length : 3;
  }

  /** Convenience: true when this is a Note-On with velocity > 0. */
  public boolean isNoteOn() {
    return (data1 & 0xF0) == 0x90 && data3 > 0;
  }

  /** Convenience: true when this is a Note-Off or Note-On with velocity 0. */
  public boolean isNoteOff() {
    return (data1 & 0xF0) == 0x80 || ((data1 & 0xF0) == 0x90 && data3 == 0);
  }

  /** MIDI channel, 0-indexed. */
  public int channel() {
    return data1 & 0x0F;
  }

  @Override
  public String toString() {
    if (data != null && data.length > 3) {
      StringBuilder sb = new StringBuilder("MidiMsg[");
      for (int i = 0; i < data.length; i++) {
        sb.append(String.format("%02X", data[i] & 0xFF));
        if (i < data.length - 1) sb.append(" ");
      }
      sb.append("]");
      return sb.toString();
    }
    return String.format("MidiMsg[%02X %02X %02X]", data1, data2, data3);
  }
}
