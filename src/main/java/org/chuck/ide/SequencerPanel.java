package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/**
 * A TR-808 style visual sequencer for ChucK-Java. This panel controls a shared ChuckArray which the
 * ChucK engine reads in real-time.
 */
public class SequencerPanel extends VBox {
  private final ChuckVM vm;
  private final int ROWS = 8;
  private final int COLS = 16;
  private final ToggleButton[][] grid = new ToggleButton[ROWS][COLS];
  private final Circle[] cursors = new Circle[COLS];
  private final String[] drumNames = {
    "Kick", "Snare", "HH-Closed", "HH-Open", "Tom", "Rim", "Clap", "Cowbell"
  };

  private ChuckArray patternArray;
  private int currentStep = -1;

  public SequencerPanel(ChuckVM vm) {
    this.vm = vm;
    setSpacing(10);
    setPadding(new Insets(10));
    setStyle("-fx-background-color: #333; -fx-border-color: #555; -fx-border-width: 1;");

    setupUI();
    initPattern();
  }

  private void setupUI() {
    Label title = new Label("GRID SEQUENCER");
    title.setStyle("-fx-text-fill: gold; -fx-font-weight: bold; -fx-font-size: 14;");

    GridPane gridPane = new GridPane();
    gridPane.setHgap(4);
    gridPane.setVgap(4);
    gridPane.setAlignment(Pos.CENTER);

    for (int r = 0; r < ROWS; r++) {
      Label lbl = new Label(drumNames[r]);
      lbl.setStyle("-fx-text-fill: #ccc; -fx-font-size: 10; -fx-font-family: 'Monospaced';");
      lbl.setPrefWidth(70);
      gridPane.add(lbl, 0, r);

      for (int c = 0; c < COLS; c++) {
        ToggleButton btn = new ToggleButton();
        btn.setPrefSize(25, 20);

        // Visual grouping (every 4 steps)
        int group = c / 4;
        String color = (group % 2 == 0) ? "#555" : "#444";
        btn.setStyle("-fx-background-color: " + color + "; -fx-border-color: #222;");

        final int row = r;
        final int col = c;
        btn.setOnAction(e -> updateValue(row, col, btn.isSelected()));

        grid[r][c] = btn;
        gridPane.add(btn, c + 1, r);
      }
    }

    // Step cursors (indicators)
    HBox cursorBox = new HBox(4);
    cursorBox.setAlignment(Pos.CENTER);
    Region spacer = new Region();
    spacer.setPrefWidth(74);
    cursorBox.getChildren().add(spacer);
    for (int c = 0; c < COLS; c++) {
      Circle dot = new Circle(3, Color.TRANSPARENT);
      dot.setStroke(Color.GRAY);
      cursors[c] = dot;
      cursorBox.getChildren().add(dot);
    }

    // Controls
    Button launchBtn = new Button("Launch Engine");
    launchBtn.setStyle(
        "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
    launchBtn.setTooltip(new Tooltip("Start the ChucK script that plays this grid"));
    launchBtn.setOnAction(e -> launchEngine());

    Button saveBtn = new Button("Save Pattern...");
    saveBtn.setOnAction(e -> savePattern());

    Button loadBtn = new Button("Load Pattern...");
    loadBtn.setOnAction(e -> loadPattern());

    Button clearBtn = new Button("Clear All");
    clearBtn.setOnAction(e -> clearGrid());

    HBox controls =
        new HBox(10, launchBtn, new Separator(Orientation.VERTICAL), saveBtn, loadBtn, clearBtn);
    controls.setAlignment(Pos.CENTER);

    getChildren().addAll(title, gridPane, cursorBox, new Separator(), controls);
  }

  private void initPattern() {
    patternArray = new ChuckArray(ChuckType.ARRAY, ROWS * COLS);
    for (int i = 0; i < ROWS * COLS; i++) patternArray.setInt(i, 0L);
    vm.setGlobalObject("seq_pattern", patternArray);
  }

  private void updateValue(int row, int col, boolean selected) {
    int idx = row * COLS + col;
    patternArray.setInt(idx, selected ? 1L : 0L);

    // Feedback color
    String base = ((col / 4) % 2 == 0) ? "#555" : "#444";
    if (selected) {
      grid[row][col].setStyle("-fx-background-color: #00FF00; -fx-border-color: #222;");
    } else {
      grid[row][col].setStyle("-fx-background-color: " + base + "; -fx-border-color: #222;");
    }
  }

  public void setStep(int step) {
    Platform.runLater(
        () -> {
          if (currentStep >= 0 && currentStep < COLS) {
            cursors[currentStep].setFill(Color.TRANSPARENT);
          }
          currentStep = step % COLS;
          if (currentStep >= 0 && currentStep < COLS) {
            cursors[currentStep].setFill(Color.LIME);
          }
        });
  }

  private void clearGrid() {
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        grid[r][c].setSelected(false);
        updateValue(r, c, false);
      }
    }
  }

  private void savePattern() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Save Pattern");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pattern Files", "*.txt"));
    File f = fc.showSaveDialog(getScene().getWindow());
    if (f != null) {
      StringBuilder sb = new StringBuilder();
      for (int r = 0; r < ROWS; r++) {
        for (int c = 0; c < COLS; c++) {
          sb.append(grid[r][c].isSelected() ? "1" : "0");
        }
        sb.append("\n");
      }
      try {
        Files.writeString(f.toPath(), sb.toString());
      } catch (IOException ignored) {
      }
    }
  }

  private void loadPattern() {
    FileChooser fc = new FileChooser();
    fc.setTitle("Load Pattern");
    fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pattern Files", "*.txt"));
    File f = fc.showOpenDialog(getScene().getWindow());
    if (f != null) {
      try {
        List<String> lines = Files.readAllLines(f.toPath());
        for (int r = 0; r < ROWS && r < lines.size(); r++) {
          String line = lines.get(r);
          for (int c = 0; c < COLS && c < line.length(); c++) {
            boolean active = line.charAt(c) == '1';
            grid[r][c].setSelected(active);
            updateValue(r, c, active);
          }
        }
      } catch (IOException ignored) {
      }
    }
  }

  private void launchEngine() {
    String engineCode =
        """
        /*
           CHUCK GRID SEQUENCER ENGINE
           ---------------------------
           This script connects the UI grid to real sound.
           It reads the 'seq_pattern' array shared by the IDE.
        */

        // 1. Setup Drum Kit (using standard samples)
        SndBuf drums[8];
        Gain master => dac;
        0.6 => master.gain;

        // Load the samples
        "examples/data/kick.wav" => drums[0].read;
        "examples/data/snare.wav" => drums[1].read;
        "examples/data/hihat.wav" => drums[2].read;
        "examples/data/hihat-open.wav" => drums[3].read;

        // Fallback for others using built-in sounds if wavs not available
        for(0 => int i; i < 8; i++) {
            drums[i] => master;
            drums[i].samples() => drums[i].pos; // set to end (silent)
        }

        // 2. Timing
        125::ms => dur T; // 16th notes at 120 BPM
        T - (now % T) => now; // Sync to global beat

        0 => int step;
        while(true) {
            // Tell the UI which step we are on (for the green cursor)
            Machine.setGlobalInt("seq_current_step", step % 16);

            // Read patterns from the IDE's shared array
            if (Machine.getGlobalObject("seq_pattern") $ Array != null) {
                Machine.getGlobalObject("seq_pattern") $ Array @=> Array data;

                for(0 => int r; r < 8; r++) {
                    // Calculate index: Row * 16 steps + current step
                    if (data.getInt(r * 16 + (step % 16)) > 0) {
                        0 => drums[r].pos; // Trigger!
                    }
                }
            }

            T => now;
            step++;
        }
        """;
    vm.run(engineCode, "SequencerEngine.ck");
  }
}
