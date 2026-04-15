package org.chuck.midi;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/** MidiFileOut: Allows writing standard MIDI files (.mid) from ChucK. */
public class MidiFileOut extends ChuckObject {
  private String filename;

  // A Track is just a list of MidiMsg events.
  private List<List<MidiMsg>> tracks = new ArrayList<>();
  private int currentTrackIndex = 0;

  private int ticksPerQuarter = 480;
  private float bpm = 120.0f; // Default BPM

  public MidiFileOut() {
    super(ChuckType.OBJECT);
  }

  public int open(String path) {
    this.filename = path;
    this.tracks.clear();
    // Create the default track (Track 0)
    this.tracks.add(new ArrayList<>());
    this.currentTrackIndex = 0;
    return 1;
  }

  /** Adds a new track and returns its index. Sets it as the current track. */
  public int addTrack() {
    List<MidiMsg> newTrack = new ArrayList<>();
    tracks.add(newTrack);
    currentTrackIndex = tracks.size() - 1;
    return currentTrackIndex;
  }

  /** Adds a new track with a specific name. Sets it as the current track. */
  public int addTrack(String name) {
    int idx = addTrack();
    if (name != null && !name.isEmpty()) {
      // Add track name meta event (FF 03 len text)
      MidiMsg meta = new MidiMsg();
      byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
      byte[] data = new byte[3 + nameBytes.length];
      data[0] = (byte) 0xFF;
      data[1] = (byte) 0x03; // Track Name
      data[2] = (byte) nameBytes.length; // Length (assume small name)
      System.arraycopy(nameBytes, 0, data, 3, nameBytes.length);
      meta.setData(data);
      meta.when = 0; // Usually at the beginning
      write(idx, meta);
    }
    return idx;
  }

  /** Sets the BPM (Tempo) by adding a Meta Event to Track 0 at time 0. */
  public void setBpm(float bpm) {
    this.bpm = bpm;
    int microsecondsPerQuarter = (int) (60000000.0f / bpm);
    MidiMsg meta = new MidiMsg();
    byte[] data = new byte[6];
    data[0] = (byte) 0xFF;
    data[1] = (byte) 0x51; // Set Tempo
    data[2] = 0x03; // Length
    data[3] = (byte) ((microsecondsPerQuarter >> 16) & 0xFF);
    data[4] = (byte) ((microsecondsPerQuarter >> 8) & 0xFF);
    data[5] = (byte) (microsecondsPerQuarter & 0xFF);
    meta.setData(data);
    meta.when = 0;
    write(0, meta);
  }

  /** Set Time Signature by adding a Meta Event to Track 0 at time 0. */
  public void setTimeSig(int num, int den) {
    // den is typically expressed as a power of 2 (e.g. 2 for quarter note, 3 for eighth)
    int denLog = (int) (Math.log(den) / Math.log(2));
    MidiMsg meta = new MidiMsg();
    byte[] data = new byte[7];
    data[0] = (byte) 0xFF;
    data[1] = (byte) 0x58; // Time Sig
    data[2] = 0x04; // Length
    data[3] = (byte) (num & 0xFF);
    data[4] = (byte) (denLog & 0xFF);
    data[5] = 24; // MIDI clocks per metronome click
    data[6] = 8; // 32nd notes per 24 MIDI clocks
    meta.setData(data);
    meta.when = 0;
    write(0, meta);
  }

  /** Writes a message to the current track. */
  public void write(MidiMsg msg) {
    write(currentTrackIndex, msg);
  }

  /** Writes a message to a specific track. */
  public void write(int trackIndex, MidiMsg msg) {
    if (filename == null) return;
    if (trackIndex < 0 || trackIndex >= tracks.size()) return;

    // Clone the message to capture current state
    MidiMsg copy = new MidiMsg();
    copy.data1 = msg.data1;
    copy.data2 = msg.data2;
    copy.data3 = msg.data3;
    copy.setData(msg.getData());
    copy.when = msg.when; // absolute time in seconds

    tracks.get(trackIndex).add(copy);
  }

  public void close() {
    if (filename == null) return;

    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
      int format = tracks.size() > 1 ? 1 : 0;

      // 1. Header Chunk
      out.write("MThd".getBytes());
      writeInt32(out, 6); // length
      writeInt16(out, format); // format 0 or 1
      writeInt16(out, tracks.size()); // number of tracks
      writeInt16(out, ticksPerQuarter); // division

      // 2. Track Chunks
      for (List<MidiMsg> track : tracks) {
        // Sort events in the track by their absolute time (when)
        track.sort(Comparator.comparingDouble(m -> m.when));

        byte[] trackBytes = encodeTrack(track);
        out.write("MTrk".getBytes());
        writeInt32(out, trackBytes.length);
        out.write(trackBytes);
      }

    } catch (IOException e) {
      System.err.println("MidiFileOut: Error saving " + filename + ": " + e.getMessage());
    }
    filename = null;
  }

  private byte[] encodeTrack(List<MidiMsg> trackData) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    long lastTick = 0;
    // Ticks per second = Ticks per quarter * Quarters per second
    double quartersPerSecond = bpm / 60.0;
    double ticksPerSecond = ticksPerQuarter * quartersPerSecond;

    for (MidiMsg msg : trackData) {
      // Calculate delta time
      long currentTick = (long) (msg.when * ticksPerSecond);
      long delta = Math.max(0, currentTick - lastTick);
      writeVarInt(baos, delta);

      byte[] raw = msg.getData();
      baos.write(raw);

      lastTick = currentTick;
    }

    // End of Track
    writeVarInt(baos, 0);
    baos.write(new byte[] {(byte) 0xFF, 0x2F, 0x00});

    return baos.toByteArray();
  }

  private void writeInt32(java.io.OutputStream out, int v) throws IOException {
    out.write((v >>> 24) & 0xFF);
    out.write((v >>> 16) & 0xFF);
    out.write((v >>> 8) & 0xFF);
    out.write(v & 0xFF);
  }

  private void writeInt16(java.io.OutputStream out, int v) throws IOException {
    out.write((v >>> 8) & 0xFF);
    out.write(v & 0xFF);
  }

  private void writeVarInt(java.io.OutputStream out, long v) throws IOException {
    long buffer = v & 0x7F;
    while ((v >>= 7) > 0) {
      buffer <<= 8;
      buffer |= ((v & 0x7F) | 0x80);
    }
    while (true) {
      out.write((int) (buffer & 0xFF));
      if ((buffer & 0x80) != 0) buffer >>>= 8;
      else break;
    }
  }
}
