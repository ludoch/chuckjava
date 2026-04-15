package org.chuck.midi;

/**
 * MidiMpe: MIDI Polyphonic Expression (MPE) voice manager. Extends MidiPoly to handle per-note
 * pitch bend, channel pressure, and timbre. Assumes each active note is on its own MIDI channel
 * (Zone 1: channels 2-16).
 */
public class MidiMpe extends MidiPoly {
  private int bendRangeSemitones = 48; // Standard MPE pitch bend range

  public MidiMpe() {
    super();
  }

  /** Set the MPE pitch bend range in semitones (default 48). */
  public void bendRange(int semitones) {
    this.bendRangeSemitones = semitones;
  }

  @Override
  public void onMessage(MidiMsg msg) {
    int status = msg.data1 & 0xF0;
    int channel = msg.data1 & 0x0F;
    int data1 = msg.data2;
    int data2 = msg.data3;

    if (status == 0x90 && data2 > 0) {
      noteOn(data1, data2 / 127.0f, channel);
    } else if (status == 0x80 || (status == 0x90 && data2 == 0)) {
      noteOff(data1, channel);
    } else if (status == 0xE0) { // Pitch Bend
      int val = (data2 << 7) | data1;
      double bendNormalized = val / 16383.0; // 0 to 1.0, 0.5 is center
      applyPitchBend(channel, bendNormalized);
    } else if (status == 0xD0) { // Channel Pressure (Aftertouch)
      double pressure = data1 / 127.0;
      applyPressure(channel, pressure);
    } else if (status == 0xB0 && data1 == 74) { // MPE Timbre (CC 74)
      double timbre = data2 / 127.0;
      applyTimbre(channel, timbre);
    }
  }

  @Override
  protected void noteOff(int note, int channel) {
    ugenLock.lock();
    try {
      for (Voice v : voicePool) {
        // In MPE, note-off must match the channel it was started on
        if (v.activeNote == note && v.activeChannel == channel) {
          v.ugen.next(0);
          try {
            v.ugen.getClass().getMethod("noteOff", float.class).invoke(v.ugen, 1.0f);
          } catch (Exception ignored) {
          }

          v.activeNote = -1;
          v.activeChannel = -1;
        }
      }
    } finally {
      ugenLock.unlock();
    }
  }

  private void applyPitchBend(int channel, double bend) {
    ugenLock.lock();
    try {
      for (Voice v : voicePool) {
        if (v.activeChannel == channel && v.activeNote != -1) {
          double bendAmount = (bend - 0.5) * 2.0; // -1.0 to 1.0
          double bendSemitones = bendAmount * bendRangeSemitones;

          double freq = 440.0 * Math.pow(2.0, ((v.activeNote + bendSemitones) - 69) / 12.0);

          if (tuningMap != null && v.activeNote >= 0 && v.activeNote < 128) {
            double baseFreq = tuningMap[v.activeNote];
            freq = baseFreq * Math.pow(2.0, bendSemitones / 12.0);
          }

          try {
            v.ugen.getClass().getMethod("setFreq", float.class).invoke(v.ugen, (float) freq);
          } catch (Exception ignored) {
          }
        }
      }
    } finally {
      ugenLock.unlock();
    }
  }

  private void applyPressure(int channel, double pressure) {
    ugenLock.lock();
    try {
      for (Voice v : voicePool) {
        if (v.activeChannel == channel && v.activeNote != -1) {
          try {
            // STK instruments often use 'pressure' or 'setModulation'
            v.ugen.getClass().getMethod("pressure", float.class).invoke(v.ugen, (float) pressure);
          } catch (Exception ignored) {
          }
        }
      }
    } finally {
      ugenLock.unlock();
    }
  }

  private void applyTimbre(int channel, double timbre) {
    ugenLock.lock();
    try {
      for (Voice v : voicePool) {
        if (v.activeChannel == channel && v.activeNote != -1) {
          try {
            // Attempt to map timbre to something useful if the instrument supports it, e.g. filter
            // cutoff
            v.ugen
                .getClass()
                .getMethod("setCutoff", float.class)
                .invoke(v.ugen, (float) (timbre * 10000.0));
          } catch (Exception ignored) {
          }
        }
      }
    } finally {
      ugenLock.unlock();
    }
  }
}
