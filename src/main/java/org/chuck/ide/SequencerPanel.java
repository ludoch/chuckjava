package org.chuck.ide;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.chuck.core.ChuckArray;
import org.chuck.core.ChuckType;
import org.chuck.core.ChuckVM;

/** A TR-808 style visual sequencer for ChucK-Java. */
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
      lbl.setStyle("-fx-text-fill: #ccc; -fx-font-size: 10;");
      lbl.setPrefWidth(70);
      gridPane.add(lbl, 0, r + 1);

      for (int c = 0; c < COLS; c++) {
        ToggleButton btn = new ToggleButton();
        btn.setPrefSize(25, 20);

        // Group styling
        int group = c / 4;
        String color = (group % 2 == 0) ? "#555" : "#444";
        btn.setStyle("-fx-background-color: " + color + "; -fx-border-color: #222;");

        final int row = r;
        final int col = c;
        btn.setOnAction(e -> updateValue(row, col, btn.isSelected()));

        grid[r][c] = btn;
        gridPane.add(btn, c + 1, r + 1);
      }
    }

    // Step cursors
    HBox cursorBox = new HBox(4);
    cursorBox.setAlignment(Pos.CENTER);
    Region spacer = new Region();
    spacer.setPrefWidth(74); // align with names
    cursorBox.getChildren().add(spacer);
    for (int c = 0; c < COLS; c++) {
      Circle dot = new Circle(3, Color.TRANSPARENT);
      dot.setStroke(Color.GRAY);
      cursors[c] = dot;
      cursorBox.getChildren().add(dot);
    }

    Button launchBtn = new Button("Launch Sequencer Engine");
    launchBtn.setStyle(
        "-fx-background-color: #4CAF50; -fx-text-fill: white; -fx-font-weight: bold;");
    launchBtn.setOnAction(e -> launchEngine());

    getChildren().addAll(title, gridPane, cursorBox, launchBtn);
  }

  private void initPattern() {
    patternArray = new ChuckArray(ChuckType.ARRAY, ROWS * COLS);
    for (int i = 0; i < ROWS * COLS; i++) patternArray.setInt(i, 0L);
    vm.setGlobalObject("seq_pattern", patternArray);
  }

  private void updateValue(int row, int col, boolean selected) {
    int idx = row * COLS + col;
    patternArray.setInt(idx, selected ? 1L : 0L);

    // Toggle color
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
          if (currentStep >= 0) {
            cursors[currentStep].setFill(Color.TRANSPARENT);
          }
          currentStep = step % COLS;
          cursors[currentStep].setFill(Color.LIME);
        });
  }

  private void launchEngine() {
    String engineCode =
        """
        /* GRID SEQUENCER ENGINE */
        MidiPoly poly => dac;
        poly.setInstrument("HevyMetl");

        [36, 38, 42, 46, 45, 37, 39, 56] @=> int midiNotes[];

        .125::second => dur T;
        T - (now % T) => now;

        0 => int step;
        while(true) {
            Machine.setGlobalInt("seq_current_step", step % 16);

            if (Machine.getGlobalObject("seq_pattern") $ ChuckArray != null) {
                Machine.getGlobalObject("seq_pattern") $ ChuckArray @=> ChuckArray data;
                for(0 => int r; r < 8; r++) {
                    if (data.getInt(r * 16 + (step % 16)) > 0) {
                        MidiMsg msg;
                        0x90 => msg.data1; midiNotes[r] => msg.data2; 100 => msg.data3;
                        poly.onMessage(msg);
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
