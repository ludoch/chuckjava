package org.chuck.ide;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;
import javafx.scene.layout.Pane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import org.chuck.midi.MidiMsg;

/** A JavaFX component that visualizes a piano keyboard. Highlights keys based on MIDI input. */
public class PianoKeyboard extends Pane {
  private static final int NUM_KEYS = 88;
  private static final int START_NOTE = 21; // A0

  private final Rectangle[] keys = new Rectangle[NUM_KEYS];
  private final boolean[] isBlack = new boolean[NUM_KEYS];
  private final Set<Integer> activeNotes = ConcurrentHashMap.newKeySet();

  // CC & Pitch Bend state
  private final double[] ccValues = new double[128];
  private double pitchBendNormalized = 0.5; // 0 to 1.0, 0.5 is center

  private final Rectangle pitchBendBar = new Rectangle(5, 60);
  private final Label midiInfoLabel = new Label("MIDI: Ready");

  public PianoKeyboard() {
    setPrefHeight(85);
    setMinHeight(85);

    double whiteKeyWidth = 15;
    double blackKeyWidth = 10;
    double whiteKeyHeight = 60;
    double blackKeyHeight = 35;

    double x = 0;

    // Pass 1: White keys
    for (int i = 0; i < NUM_KEYS; i++) {
      int note = START_NOTE + i;
      int noteInOctave = note % 12;
      isBlack[i] =
          (noteInOctave == 1
              || noteInOctave == 3
              || noteInOctave == 6
              || noteInOctave == 8
              || noteInOctave == 10);

      if (!isBlack[i]) {
        Rectangle rect = new Rectangle(x, 0, whiteKeyWidth, whiteKeyHeight);
        rect.setFill(Color.WHITE);
        rect.setStroke(Color.BLACK);
        keys[i] = rect;
        getChildren().add(rect);
        x += whiteKeyWidth;
      }
    }

    // Pass 2: Black keys (overlay)
    x = 0;
    for (int i = 0; i < NUM_KEYS; i++) {
      int note = START_NOTE + i;
      if (!isBlack[i]) {
        x += whiteKeyWidth;
      } else {
        double bx = x - (blackKeyWidth / 2.0);
        Rectangle rect = new Rectangle(bx, 0, blackKeyWidth, blackKeyHeight);
        rect.setFill(Color.BLACK);
        rect.setStroke(Color.BLACK);
        keys[i] = rect;
        getChildren().add(rect);
      }
    }

    // Controls overlay
    pitchBendBar.setFill(Color.CYAN);
    pitchBendBar.setStroke(Color.DARKCYAN);
    pitchBendBar.setY(0);
    pitchBendBar.setX(10); // Simple fixed position

    midiInfoLabel.setStyle(
        "-fx-font-family: 'Monospaced'; -fx-font-size: 10; -fx-text-fill: #666;");
    midiInfoLabel.setLayoutY(65);
    midiInfoLabel.setLayoutX(5);

    getChildren().addAll(pitchBendBar, midiInfoLabel);
  }

  public void onMidiMessage(MidiMsg msg) {
    int status = msg.data1 & 0xF0;
    int chan = msg.data1 & 0x0F;
    int note = msg.data2;
    int velocity = msg.data3;

    if (status == 0x90 && velocity > 0) {
      noteOn(note);
    } else if (status == 0x80 || (status == 0x90 && velocity == 0)) {
      noteOff(note);
    } else if (status == 0xB0) {
      ccValues[note] = velocity / 127.0;
      updateInfo("CC " + note + ": " + velocity + " (Ch " + (chan + 1) + ")");
    } else if (status == 0xE0) {
      int val = (velocity << 7) | note;
      pitchBendNormalized = val / 16383.0;
      updatePitchBendUI();
      updateInfo("Pitch Bend: " + val + " (Ch " + (chan + 1) + ")");
    }
  }

  private void updateInfo(String text) {
    Platform.runLater(() -> midiInfoLabel.setText("MIDI: " + text));
  }

  private void updatePitchBendUI() {
    Platform.runLater(
        () -> {
          double h = 60 * pitchBendNormalized;
          pitchBendBar.setHeight(h);
          pitchBendBar.setY(60 - h);
        });
  }

  private void noteOn(int note) {
    int idx = note - START_NOTE;
    if (idx >= 0 && idx < NUM_KEYS) {
      activeNotes.add(note);
      updateKeyColor(idx, true);
    }
  }

  private void noteOff(int note) {
    int idx = note - START_NOTE;
    if (idx >= 0 && idx < NUM_KEYS) {
      activeNotes.remove(note);
      updateKeyColor(idx, false);
    }
  }

  private void updateKeyColor(int idx, boolean active) {
    Platform.runLater(
        () -> {
          if (active) {
            keys[idx].setFill(Color.ORANGE);
          } else {
            keys[idx].setFill(isBlack[idx] ? Color.BLACK : Color.WHITE);
          }
        });
  }
}
