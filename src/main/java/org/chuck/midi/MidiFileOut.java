package org.chuck.midi;

import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.chuck.core.ChuckObject;
import org.chuck.core.ChuckType;

/** MidiFileOut: Allows writing standard MIDI files (.mid) from ChucK. */
public class MidiFileOut extends ChuckObject {
  private String filename;
  private List<MidiMsg> trackData = new ArrayList<>();
  private int ticksPerQuarter = 480;

  public MidiFileOut() {
    super(ChuckType.OBJECT);
  }

  public int open(String path) {
    this.filename = path;
    this.trackData.clear();
    return 1;
  }

  public void write(MidiMsg msg) {
    if (filename == null) return;

    // Clone the message to capture current state
    MidiMsg copy = new MidiMsg();
    copy.data1 = msg.data1;
    copy.data2 = msg.data2;
    copy.data3 = msg.data3;
    copy.setData(msg.getData());
    copy.when = msg.when; // Use when as absolute time in seconds for now

    trackData.add(copy);
  }

  public void close() {
    if (filename == null) return;

    try (BufferedOutputStream out = new BufferedOutputStream(new FileOutputStream(filename))) {
      // 1. Header Chunk
      out.write("MThd".getBytes());
      writeInt32(out, 6); // length
      writeInt16(out, 0); // format 0 (single track)
      writeInt16(out, 1); // number of tracks
      writeInt16(out, ticksPerQuarter); // division

      // 2. Track Chunk
      byte[] trackBytes = encodeTrack();
      out.write("MTrk".getBytes());
      writeInt32(out, trackBytes.length);
      out.write(trackBytes);

    } catch (IOException e) {
      System.err.println("MidiFileOut: Error saving " + filename + ": " + e.getMessage());
    }
    filename = null;
  }

  private byte[] encodeTrack() throws IOException {
    java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();

    long lastTick = 0;
    double secondsToTicks = ticksPerQuarter * 2.0; // Assume 120bpm default (0.5s per quarter)

    for (MidiMsg msg : trackData) {
      // Calculate delta time
      long currentTick = (long) (msg.when * secondsToTicks * 1000); // very rough mapping
      // Note: in a real ChucK implementation, we'd use 'now' diffs.
      // For this port, if 'when' is 0, we'll just use a small delta to separate notes.
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
