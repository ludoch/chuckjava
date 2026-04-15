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
import org.chuck.core.ChuckVM;

/**
 * MidiFileOut: Allows writing standard MIDI files (.mid) from ChucK. Supports Multi-track (Format
 * 1), Tempo changes, Markers, and NRPN.
 */
public class MidiFileOut extends ChuckObject {
  private String filename;

  // A Track is just a list of MidiMsg events.
  private List<List<MidiMsg>> tracks = new ArrayList<>();
  private int currentTrackIndex = 0;

  private int ticksPerQuarter = 480;

  // Tempo Map for converting absolute seconds to ticks
  private static class TempoChange {
    double seconds;
    float bpm;
    long tick;
  }

  private List<TempoChange> tempoChanges = new ArrayList<>();
  private float initialBpm = 120.0f;

  public MidiFileOut() {
    super(ChuckType.OBJECT);
  }

  private double getCurrentTimeInSeconds() {
    if (ChuckVM.CURRENT_VM.isBound()) {
      ChuckVM vm = ChuckVM.CURRENT_VM.get();
      return (double) vm.getCurrentTime() / vm.getSampleRate();
    }
    return 0;
  }

  public int open(String path) {
    this.filename = path;
    this.tracks.clear();
    this.tempoChanges.clear();
    // Create the default track (Track 0)
    this.tracks.add(new ArrayList<>());
    this.currentTrackIndex = 0;
    this.initialBpm = 120.0f;
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
      addTrackName(idx, name, 0);
    }
    return idx;
  }

  /** Adds a track name meta event to a specific track. */
  public void addTrackName(int trackIndex, String name, double when) {
    if (trackIndex < 0 || trackIndex >= tracks.size()) return;
    MidiMsg meta = new MidiMsg();
    byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
    byte[] data = new byte[3 + nameBytes.length];
    data[0] = (byte) 0xFF;
    data[1] = (byte) 0x03; // Track Name
    data[2] = (byte) nameBytes.length; // Length (TODO: handle > 127)
    System.arraycopy(nameBytes, 0, data, 3, nameBytes.length);
    meta.setData(data);
    meta.when = when;
    write(trackIndex, meta);
  }

  /** Sets the BPM (Tempo) at the current time. */
  public void setBpm(float bpm) {
    setBpm(bpm, getCurrentTimeInSeconds());
  }

  /** Sets the BPM (Tempo) at a specific time. */
  public void setBpm(float bpm, double when) {
    if (when <= 0 && tempoChanges.isEmpty()) {
      initialBpm = bpm;
    }

    TempoChange tc = new TempoChange();
    tc.seconds = when;
    tc.bpm = bpm;
    tempoChanges.add(tc);

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
    meta.when = when;
    write(0, meta); // Tempo changes usually go in Track 0
  }

  /** Set Time Signature at current time. */
  public void setTimeSig(int num, int den) {
    setTimeSig(num, den, getCurrentTimeInSeconds());
  }

  /** Set Time Signature at specific time. */
  public void setTimeSig(int num, int den, double when) {
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
    meta.when = when;
    write(0, meta);
  }

  /** Adds a marker at the current time. */
  public void addMarker(String text) {
    addMarker(text, getCurrentTimeInSeconds());
  }

  /** Adds a marker at a specific time. */
  public void addMarker(String text, double when) {
    MidiMsg meta = new MidiMsg();
    byte[] textBytes = text.getBytes(StandardCharsets.UTF_8);
    byte[] data = new byte[3 + textBytes.length];
    data[0] = (byte) 0xFF;
    data[1] = (byte) 0x06; // Marker
    data[2] = (byte) textBytes.length;
    System.arraycopy(textBytes, 0, data, 3, textBytes.length);
    meta.setData(data);
    meta.when = when;
    write(0, meta);
  }

  /** Sends an NRPN sequence to the current track. */
  public void nrpn(int channel, int parameter, int value) {
    nrpn(currentTrackIndex, channel, parameter, value, getCurrentTimeInSeconds());
  }

  /** Sends an NRPN sequence to the current track at a specific time. */
  public void nrpn(int channel, int parameter, int value, double when) {
    nrpn(currentTrackIndex, channel, parameter, value, when);
  }

  /** Sends an NRPN sequence to a specific track. */
  public void nrpn(int track, int channel, int parameter, int value, double when) {
    // NRPN MSB (CC 99)
    writeCC(track, channel, 99, (parameter >> 7) & 0x7F, when);
    // NRPN LSB (CC 98)
    writeCC(track, channel, 98, parameter & 0x7F, when);
    // Data Entry MSB (CC 6)
    writeCC(track, channel, 6, (value >> 7) & 0x7F, when);
    // Data Entry LSB (CC 38)
    writeCC(track, channel, 38, value & 0x7F, when);
  }

  private void writeCC(int track, int channel, int cc, int val, double when) {
    MidiMsg msg = new MidiMsg();
    msg.data1 = 0xB0 | (channel & 0x0F);
    msg.data2 = cc & 0x7F;
    msg.data3 = val & 0x7F;
    msg.when = when;
    write(track, msg);
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

    // 0. Build the finalized tempo map
    buildTempoMap();

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
    tempoChanges.clear();
  }

  private void buildTempoMap() {
    // Sort tempo changes by time
    tempoChanges.sort(Comparator.comparingDouble(tc -> tc.seconds));

    // If no tempo change at 0, add the initial one
    if (tempoChanges.isEmpty() || tempoChanges.get(0).seconds > 0) {
      TempoChange initial = new TempoChange();
      initial.seconds = 0;
      initial.bpm = initialBpm;
      initial.tick = 0;
      tempoChanges.add(0, initial);
    }

    // Calculate ticks for each tempo change
    long currentTick = 0;
    double currentTime = 0;
    float currentBpm = initialBpm;

    for (int i = 0; i < tempoChanges.size(); i++) {
      TempoChange tc = tempoChanges.get(i);
      double duration = tc.seconds - currentTime;
      if (duration > 0) {
        double quartersPerSecond = currentBpm / 60.0;
        currentTick += (long) (duration * ticksPerQuarter * quartersPerSecond);
      }
      tc.tick = currentTick;
      currentTime = tc.seconds;
      currentBpm = tc.bpm;
    }
  }

  private long secondsToTicks(double seconds) {
    if (tempoChanges.isEmpty()) return 0;

    // Find the tempo change immediately preceding or at this time
    TempoChange last = tempoChanges.get(0);
    for (int i = 1; i < tempoChanges.size(); i++) {
      TempoChange next = tempoChanges.get(i);
      if (seconds < next.seconds) break;
      last = next;
    }

    double durationSinceLast = seconds - last.seconds;
    double quartersPerSecond = last.bpm / 60.0;
    return last.tick + (long) (durationSinceLast * ticksPerQuarter * quartersPerSecond);
  }

  private byte[] encodeTrack(List<MidiMsg> trackData) throws IOException {
    ByteArrayOutputStream baos = new ByteArrayOutputStream();

    long lastTick = 0;

    for (MidiMsg msg : trackData) {
      // Calculate tick using the tempo map
      long currentTick = secondsToTicks(msg.when);
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
