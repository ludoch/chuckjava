package org.chuck.ide;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;
import org.chuck.midi.MidiFileOut;
import org.chuck.midi.MidiMsg;

/**
 * Global IDE MIDI Recorder. Captures all incoming MIDI monitor messages and saves them to a
 * timestamped .mid file in the 'recordings' directory.
 */
public class MidiRecorder {
  private static final Logger logger = Logger.getLogger(MidiRecorder.class.getName());
  private MidiFileOut mfo;
  private boolean recording = false;
  private long startTimeMillis;

  public void start() {
    if (recording) return;

    File dir = new File("recordings");
    if (!dir.exists()) dir.mkdirs();

    String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
    String filename = "recordings/midi_capture_" + timestamp + ".mid";

    mfo = new MidiFileOut();
    mfo.open(filename);
    mfo.setBpm(120.0f, 0.0); // Default 120 BPM
    mfo.addTrack("IDE Capture");

    startTimeMillis = System.currentTimeMillis();
    recording = true;
    logger.info("MIDI Recording started: " + filename);
  }

  public void stop() {
    if (!recording) return;
    recording = false;
    mfo.close();
    mfo = null;
    logger.info("MIDI Recording stopped.");
  }

  public boolean isRecording() {
    return recording;
  }

  public void onMidiMessage(MidiMsg msg) {
    if (!recording) return;

    // Use wall-clock time relative to start of recording
    double when = (System.currentTimeMillis() - startTimeMillis) / 1000.0;

    // Clone and write to track 1
    MidiMsg copy = new MidiMsg();
    copy.data1 = msg.data1;
    copy.data2 = msg.data2;
    copy.data3 = msg.data3;
    copy.setData(msg.getData());
    copy.when = when;

    mfo.write(1, copy);
  }
}
