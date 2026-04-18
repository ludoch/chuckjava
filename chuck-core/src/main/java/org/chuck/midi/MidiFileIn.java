package org.chuck.midi;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.sound.midi.*;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/**
 * ChucK MidiFileIn — reads Standard MIDI (.mid) files for sequencing. Supports multi-track reading
 * and provides timing information.
 */
public class MidiFileIn extends ChuckObject {
  private Sequence sequence;
  private List<List<TimedMessage>> trackMessages = new ArrayList<>();
  private List<TimedMessage> mergedMessages = new ArrayList<>();
  private int[] trackCursors;
  private int mergedCursor = 0;
  private boolean opened = false;
  private double bpm = 120.0;

  private static class TimedMessage {
    long tick;
    byte[] data;
    double deltaSeconds; // time since previous message in the same track

    TimedMessage(long tick, byte[] data) {
      this.tick = tick;
      this.data = data;
    }
  }

  public MidiFileIn() {
    super(ChuckType.OBJECT);
  }

  public long open(String filename) {
    try {
      sequence = MidiSystem.getSequence(new File(filename));
      trackMessages.clear();
      mergedMessages.clear();

      int resolution = sequence.getResolution(); // Ticks per quarter
      double currentBpm = 120.0;
      this.bpm = 120.0;
      boolean bpmSet = false;

      Track[] tracks = sequence.getTracks();
      trackCursors = new int[tracks.length];

      for (int t = 0; t < tracks.length; t++) {
        Track track = tracks[t];
        List<TimedMessage> messages = new ArrayList<>();
        long lastTick = 0;

        for (int i = 0; i < track.size(); i++) {
          MidiEvent ev = track.get(i);
          MidiMessage mm = ev.getMessage();

          // Check for tempo meta event
          if (mm instanceof MetaMessage meta && meta.getType() == 0x51) {
            byte[] data = meta.getData();
            int mspq = ((data[0] & 0xFF) << 16) | ((data[1] & 0xFF) << 8) | (data[2] & 0xFF);
            currentBpm = 60000000.0 / mspq;
            if (!bpmSet) {
              this.bpm = currentBpm;
              bpmSet = true;
            }
          }

          if (mm instanceof ShortMessage
              || mm instanceof SysexMessage
              || mm instanceof MetaMessage) {
            TimedMessage tm = new TimedMessage(ev.getTick(), mm.getMessage());

            // Calculate delta time in seconds (approximate using current BPM)
            // Note: Accurate delta requires a full tempo map. For now, we use a simple approach.
            double secondsPerTick = 60.0 / (currentBpm * resolution);
            tm.deltaSeconds = (tm.tick - lastTick) * secondsPerTick;

            messages.add(tm);
            mergedMessages.add(tm);
            lastTick = tm.tick;
          }
        }
        trackMessages.add(messages);
      }

      mergedMessages.sort(Comparator.comparingLong(tm -> tm.tick));
      opened = true;
      return 1L;
    } catch (Exception e) {
      return 0L;
    }
  }

  /** Read the next MIDI message from the merged list into msg. */
  public long read(MidiMsg msg) {
    if (!opened || mergedCursor >= mergedMessages.size()) return 0L;
    return fillMsg(msg, mergedMessages.get(mergedCursor++));
  }

  /** Read the next MIDI message from a specific track into msg. */
  public long read(MidiMsg msg, int trackIndex) {
    if (!opened || trackIndex < 0 || trackIndex >= trackMessages.size()) return 0L;
    List<TimedMessage> messages = trackMessages.get(trackIndex);
    int cursor = trackCursors[trackIndex];
    if (cursor >= messages.size()) return 0L;

    long res = fillMsg(msg, messages.get(cursor));
    trackCursors[trackIndex]++;
    return res;
  }

  private long fillMsg(MidiMsg msg, TimedMessage tm) {
    byte[] data = tm.data;
    msg.setData(data);
    msg.when = tm.deltaSeconds; // Return delta time in seconds
    return 1L;
  }

  public void rewind() {
    mergedCursor = 0;
    if (trackCursors != null) {
      for (int i = 0; i < trackCursors.length; i++) trackCursors[i] = 0;
    }
  }

  public void close() {
    sequence = null;
    trackMessages.clear();
    mergedMessages.clear();
    opened = false;
    mergedCursor = 0;
  }

  public long more() {
    return (opened && mergedCursor < mergedMessages.size()) ? 1L : 0L;
  }

  public long size() {
    return mergedMessages.size();
  }

  public long numTracks() {
    return trackMessages.size();
  }

  public long resolution() {
    return sequence != null ? sequence.getResolution() : 0L;
  }

  public long tpq() {
    return resolution();
  }

  public double bpm() {
    return bpm;
  }

  public boolean isOpened() {
    return opened;
  }
}
