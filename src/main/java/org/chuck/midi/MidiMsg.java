package org.chuck.midi;

import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * Holds one MIDI message as three raw bytes, matching ChucK's MidiMsg convention: data1 = status
 * byte (e.g. 0x90 = note-on ch 1) data2 = first data (note number 0-127, or controller number)
 * data3 = second data (velocity 0-127, or controller value)
 */
public class MidiMsg extends ChuckObject {
  public int data1 = 0;
  public int data2 = 0;
  public int data3 = 0;

  public MidiMsg() {
    super(ChuckType.OBJECT);
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
    return String.format("MidiMsg[%02X %02X %02X]", data1, data2, data3);
  }
}
