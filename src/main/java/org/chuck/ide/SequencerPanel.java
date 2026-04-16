package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/**
 * A TR-808 style visual sequencer for ChucK-Java. Features 8 tracks with real samples, Save/Load,
 * Randomness, and Detachable view.
 */
public class SequencerPanel extends VBox {
  private final ChuckVM vm;
  private final int ROWS = 8;
  private final int COLS = 16;
  private final ToggleButton[][] grid = new ToggleButton[ROWS][COLS];
  private final Circle[] cursors = new Circle[COLS];
  private Stage detachedStage;
  private int lastEngineShredId = -1;
  private final Label statusLabel = new Label("Engine: Stopped");

  // Real sample mapping
  private final String[] drumNames = {
    "Kick", "Snare", "HH-Closed", "HH-Open", "Clap", "Cowbell", "Click", "Snare-Hop"
  };
  private final String[] drumPaths = {
    "examples/data/kick.wav",
    "examples/data/snare.wav",
    "examples/data/hihat.wav",
    "examples/data/hihat-open.wav",
    "examples/book/digital-artists/audio/clap_01.wav",
    "examples/book/digital-artists/audio/cowbell_01.wav",
    "examples/book/digital-artists/audio/click_01.wav",
    "examples/data/snare-hop.wav"
  };

  private ChuckArray patternArray;
  private ChuckArray probabilityArray;
  private int currentStep = -1;

  public SequencerPanel(ChuckVM vm) {
    this.vm = vm;
    setSpacing(10);
    setPadding(new Insets(10));
    setStyle("-fx-background-color: #2b2b2b; -fx-border-color: #555; -fx-border-width: 1;");

    setupUI();
    initArrays();
  }

  private void setupUI() {
    HBox header = new HBox(10);
    Label title = new Label("GRID SEQUENCER PRO");
    title.setStyle("-fx-text-fill: gold; -fx-font-weight: bold; -fx-font-size: 14;");
    Region spacer1 = new Region();
    HBox.setHgrow(spacer1, Priority.ALWAYS);

    Button detachBtn = new Button("Detach ⧉");
    detachBtn.setStyle("-fx-font-size: 10; -fx-base: #444;");
    detachBtn.setOnAction(e -> detachWindowInternal());

    header.getChildren().addAll(title, spacer1, detachBtn);

    GridPane gridPane = new GridPane();
    gridPane.setHgap(4);
    gridPane.setVgap(4);
    gridPane.setAlignment(Pos.CENTER);

    for (int r = 0; r < ROWS; r++) {
      Label lbl = new Label(drumNames[r]);
      lbl.setStyle("-fx-text-fill: #aaa; -fx-font-size: 10; -fx-font-family: 'Monospaced';");
      lbl.setPrefWidth(80);
      gridPane.add(lbl, 0, r);

      for (int c = 0; c < COLS; c++) {
        ToggleButton btn = new ToggleButton();
        btn.setPrefSize(28, 22);

        int group = c / 4;
        String color = (group % 2 == 0) ? "#444" : "#333";
        btn.setStyle("-fx-background-color: " + color + "; -fx-border-color: #111;");

        final int row = r;
        final int col = c;
        btn.setOnAction(e -> updateValue(row, col, btn.isSelected()));

        grid[r][c] = btn;
        gridPane.add(btn, c + 1, r);
      }
    }

    HBox cursorBox = new HBox(4);
    cursorBox.setAlignment(Pos.CENTER);
    Region cursorSpacer = new Region();
    cursorSpacer.setPrefWidth(84);
    cursorBox.getChildren().add(cursorSpacer);
    for (int c = 0; c < COLS; c++) {
      Circle dot = new Circle(3, Color.TRANSPARENT);
      dot.setStroke(Color.GRAY);
      cursors[c] = dot;
      cursorBox.getChildren().add(dot);
    }

    // Advanced Controls
    Button launchBtn = new Button("Launch Engine");
    launchBtn.setStyle(
        "-fx-background-color: #2e7d32; -fx-text-fill: white; -fx-font-weight: bold;");
    launchBtn.setOnAction(e -> launchEngine());

    Button randomBtn = new Button("Randomize");
    randomBtn.setOnAction(e -> randomizeGrid());

    Button saveBtn = new Button("Save...");
    saveBtn.setOnAction(e -> savePattern());

    Button loadBtn = new Button("Load...");
    loadBtn.setOnAction(e -> loadPattern());

    Button clearBtn = new Button("Clear");
    clearBtn.setOnAction(e -> clearGrid());

    statusLabel.setStyle("-fx-text-fill: #888; -fx-font-size: 10;");

    HBox controls =
        new HBox(
            8,
            launchBtn,
            new Separator(Orientation.VERTICAL),
            randomBtn,
            clearBtn,
            saveBtn,
            loadBtn,
            new Separator(Orientation.VERTICAL),
            statusLabel);
    controls.setAlignment(Pos.CENTER);

    getChildren().addAll(header, new Separator(), gridPane, cursorBox, new Separator(), controls);
  }

  private void initArrays() {
    patternArray = new ChuckArray(ChuckType.ARRAY, ROWS * COLS);
    probabilityArray = new ChuckArray(ChuckType.ARRAY, ROWS); // Per-track probability

    for (int i = 0; i < ROWS * COLS; i++) patternArray.setInt(i, 0L);
    for (int i = 0; i < ROWS; i++) probabilityArray.setFloat(i, 1.0); // 100% default

    vm.setGlobalObject("seq_pattern", patternArray);
    vm.setGlobalObject("seq_probability", probabilityArray);
  }

  private void updateValue(int row, int col, boolean selected) {
    int idx = row * COLS + col;
    patternArray.setInt(idx, selected ? 1L : 0L);

    String base = ((col / 4) % 2 == 0) ? "#444" : "#333";
    if (selected) {
      grid[row][col].setStyle("-fx-background-color: #4CAF50; -fx-border-color: #111;");
    } else {
      grid[row][col].setStyle("-fx-background-color: " + base + "; -fx-border-color: #111;");
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

  private void randomizeGrid() {
    Random rand = new Random();
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        boolean active = rand.nextDouble() < 0.25; // 25% density
        grid[r][c].setSelected(active);
        updateValue(r, c, active);
      }
    }
  }

  private void clearGrid() {
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        grid[r][c].setSelected(false);
        updateValue(r, c, false);
      }
    }
  }

  public void detachWindow(Tab parentTab, TabPane parentPane) {
    if (detachedStage != null) {
      detachedStage.toFront();
      return;
    }

    detachedStage = new Stage();
    detachedStage.setTitle("ChucK Sequencer Detached");

    // Remove from tab UI
    parentTab.setContent(new StackPane(new Label("Sequencer is detached in a separate window.")));

    Scene scene = new Scene(this, 600, 380);
    detachedStage.setScene(scene);

    detachedStage.setOnCloseRequest(
        e -> {
          parentTab.setContent(this);
          detachedStage = null;
          parentPane.getSelectionModel().select(parentTab);
        });

    detachedStage.show();
  }

  private void detachWindowInternal() {
    Node p = getParent();
    while (p != null && !(p instanceof TabPane)) {
      p = p.getParent();
    }
    if (p instanceof TabPane tp) {
      for (Tab t : tp.getTabs()) {
        if (t.getContent() == this) {
          detachWindow(t, tp);
          return;
        }
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
    if (lastEngineShredId != -1) {
      vm.removeShred(lastEngineShredId);
    }

    StringBuilder sb = new StringBuilder();
    sb.append("/* CHUCK GRID SEQUENCER PRO ENGINE */\n");
    sb.append("SndBuf kit[").append(ROWS).append("];\n");
    sb.append("Gain master => dac;\n0.6 => master.gain;\n\n");

    for (int i = 0; i < ROWS; i++) {
      // Use absolute path for reliability
      File f = new File(drumPaths[i]);
      String abs = f.getAbsolutePath().replace("\\", "/");
      sb.append("\"").append(abs).append("\" => kit[").append(i).append("].read;\n");
      sb.append("kit[").append(i).append("] => master;\n");
      sb.append("kit[").append(i).append("].samples() => kit[").append(i).append("].pos;\n");
    }

    sb.append(
        """

        125::ms => dur T;
        T - (now % T) => now;

        0 => int step;
        while(true) {
            Machine.setGlobalInt("seq_current_step", step % 16);

            if (Machine.getGlobalObject("seq_pattern") $ int[] @=> int data[]) {
                if (Machine.getGlobalObject("seq_probability") $ float[] @=> float probs[]) {
                    for(0 => int r; r < 8; r++) {
                        if (data[r * 16 + (step % 16)] > 0) {
                            // Check per-track probability
                            if (Math.randomf() <= probs[r]) {
                                0 => kit[r].pos;
                            }
                        }
                    }
                }
            }

            T => now;
            step++;
        }
        """);
    lastEngineShredId = vm.run(sb.toString(), "SequencerEngine.ck");
    if (lastEngineShredId > 0) {
      statusLabel.setText("Engine: RUNNING");
      statusLabel.setStyle("-fx-text-fill: #4CAF50; -fx-font-weight: bold;");
    } else {
      statusLabel.setText("Engine: ERROR");
      statusLabel.setStyle("-fx-text-fill: #F44336;");
    }
  }
}
