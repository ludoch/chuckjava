package org.chuck.midi;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sound.midi.*;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * ChucK MidiFileIn — reads Standard MIDI (.mid) files for sequencing. Usage in ChucK: MidiFileIn
 * mf; MidiMsg msg; mf.open("song.mid"); while (mf.more()) { mf.read(msg); <<< msg.data1, msg.data2,
 * msg.data3 >>>; } mf.close();
 */
public class MidiFileIn extends ChuckObject {
  private Sequence sequence;
  private final List<TimedMessage> messages = new ArrayList<>();
  private int cursor = 0;
  private boolean opened = false;

  private record TimedMessage(long tick, byte[] data) {}

  public MidiFileIn() {
    super(ChuckType.OBJECT);
  }

  /**
   * Open a MIDI file. Returns 1 on success, 0 on failure. All tracks are merged into a single
   * chronologically sorted message list.
   */
  public long open(String filename) {
    try {
      sequence = MidiSystem.getSequence(new File(filename));
      messages.clear();
      cursor = 0;
      for (Track track : sequence.getTracks()) {
        for (int i = 0; i < track.size(); i++) {
          MidiEvent ev = track.get(i);
          MidiMessage mm = ev.getMessage();
          if (mm instanceof ShortMessage || mm instanceof SysexMessage) {
            messages.add(new TimedMessage(ev.getTick(), mm.getMessage()));
          }
        }
      }
      messages.sort(Comparator.comparingLong(TimedMessage::tick));
      opened = true;
      return 1L;
    } catch (Exception e) {
      return 0L;
    }
  }

  /** Read the next MIDI message into msg. Returns 1 if a message was read, 0 if EOF. */
  public long read(MidiMsg msg) {
    if (!opened || cursor >= messages.size()) return 0L;
    byte[] data = messages.get(cursor++).data();
    msg.data1 = data.length > 0 ? (data[0] & 0xFF) : 0;
    msg.data2 = data.length > 1 ? (data[1] & 0xFF) : 0;
    msg.data3 = data.length > 2 ? (data[2] & 0xFF) : 0;
    return 1L;
  }

  /** Rewind to the beginning of the file. */
  public void rewind() {
    cursor = 0;
  }

  /** Close the file and free resources. */
  public void close() {
    sequence = null;
    messages.clear();
    opened = false;
    cursor = 0;
  }

  /** Returns 1 if there are more messages to read, 0 if at EOF. */
  public long more() {
    return (opened && cursor < messages.size()) ? 1L : 0L;
  }

  /** Total number of MIDI messages across all tracks. */
  public long size() {
    return messages.size();
  }

  /** Number of tracks in the file. */
  public long numTracks() {
    return sequence != null ? sequence.getTracks().length : 0L;
  }

  /** Tick resolution (pulses per quarter note) of the file. */
  public long resolution() {
    return sequence != null ? sequence.getResolution() : 0L;
  }

  public boolean isOpened() {
    return opened;
  }
}
