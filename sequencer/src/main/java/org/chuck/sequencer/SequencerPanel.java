package org.chuck.sequencer;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import java.util.Random;
import javafx.geometry.Insets;
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
 * A TR-808 style visual sequencer for ChucK-Java. Features 8 tracks with real samples, Save/Load,
 * Randomness.
 */
public class SequencerPanel extends VBox {
  private final ChuckVM vm;
  private final int ROWS = 8;
  private final int COLS = 16;
  private final ToggleButton[][] grid = new ToggleButton[ROWS][COLS];
  private final Circle[] cursors = new Circle[COLS];

  // Real sample mapping
  private final String[] drumNames = {
    "Kick", "Snare", "HH-Closed", "HH-Open", "Clap", "Cowbell", "Click", "Snare-Hop"
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
    registerGlobals();
  }

  public void registerGlobals() {
    vm.setGlobalObject("seq_pattern", patternArray);
    vm.setGlobalObject("seq_probability", probabilityArray);
  }

  private void setupUI() {
    HBox header = new HBox(10);
    Label title = new Label("GRID SEQUENCER PRO");
    title.setStyle("-fx-text-fill: gold; -fx-font-weight: bold; -fx-font-size: 14;");
    header.getChildren().addAll(title);

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

    getChildren().addAll(header, new Separator(), gridPane, cursorBox);
  }

  private void initArrays() {
    patternArray = new ChuckArray(ChuckType.ARRAY, ROWS * COLS);
    probabilityArray = new ChuckArray(ChuckType.ARRAY, ROWS); // Per-track probability

    for (int i = 0; i < ROWS * COLS; i++) patternArray.setInt(i, 0L);
    for (int i = 0; i < ROWS; i++) probabilityArray.setFloat(i, 1.0); // 100% default
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
    if (currentStep >= 0 && currentStep < COLS) {
      cursors[currentStep].setFill(Color.TRANSPARENT);
    }
    currentStep = step % COLS;
    if (currentStep >= 0 && currentStep < COLS) {
      cursors[currentStep].setFill(Color.LIME);
    }
  }

  public void randomizeGrid() {
    Random rand = new Random();
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        boolean active = rand.nextDouble() < 0.25; // 25% density
        grid[r][c].setSelected(active);
        updateValue(r, c, active);
      }
    }
  }

  public void clearGrid() {
    for (int r = 0; r < ROWS; r++) {
      for (int c = 0; c < COLS; c++) {
        grid[r][c].setSelected(false);
        updateValue(r, c, false);
      }
    }
  }

  public void savePattern() {
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

  public void loadPattern() {
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
}
