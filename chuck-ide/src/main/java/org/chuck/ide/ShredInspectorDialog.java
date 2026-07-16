package org.chuck.ide;

import java.util.Date;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

/** Dialog for inspecting live runtime properties of an active or completed ChucK shred. */
public class ShredInspectorDialog {

  public static void show(ChuckIDE ide, ShredInfo item) {
    if (item == null || item.shred == null) return;

    Stage dialog = new Stage();
    dialog.initModality(Modality.WINDOW_MODAL);
    dialog.setTitle("Shred Inspector - [" + item.id + "] " + item.name);

    Label titleLabel = new Label("Shred Details: #" + item.id);
    titleLabel.setFont(Font.font("System", FontWeight.BOLD, 16));

    GridPane grid = new GridPane();
    grid.setHgap(12);
    grid.setVgap(8);
    grid.setPadding(new Insets(10, 0, 10, 0));

    addProperty(grid, 0, "Source Script:", item.name);
    addProperty(grid, 1, "Spork Timestamp:", new Date(item.startTimeMillis).toString());
    addProperty(grid, 2, "Elapsed Duration:", item.durationProp.get());

    String status = "Active / Running";
    if (item.shred.isDone()) {
      status = "Done / Terminated";
    } else if (item.shred.isWaiting()) {
      status = "Yielded / Waiting on time or event";
    }
    addProperty(grid, 3, "VM Execution State:", status);

    int instrCount = 0;
    if (item.shred.getCode() != null) {
      instrCount = item.shred.getCode().getNumInstructions();
    }
    addProperty(grid, 4, "Code Instructions:", String.valueOf(instrCount));

    int spDepth = item.shred.mem.getSp();
    addProperty(grid, 5, "Memory Stack Pointer:", String.valueOf(spDepth));

    Button killBtn = new Button("Kill Shred X");
    killBtn.setStyle("-fx-background-color: #ff6666; -fx-text-fill: white; -fx-font-weight: bold;");
    killBtn.setDisable(item.shred.isDone());
    killBtn.setOnAction(
        e -> {
          ide.getVM().removeShred(item.id);
          dialog.close();
        });

    Button closeBtn = new Button("Close");
    closeBtn.setOnAction(e -> dialog.close());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);
    HBox buttonBox = new HBox(10, killBtn, spacer, closeBtn);
    buttonBox.setPadding(new Insets(10, 0, 0, 0));

    VBox root = new VBox(10, titleLabel, new Separator(), grid, new Separator(), buttonBox);
    root.setPadding(new Insets(16));

    Scene scene = new Scene(root, 480, 320);
    dialog.setScene(scene);
    dialog.show();
  }

  private static void addProperty(GridPane grid, int row, String labelText, String valueText) {
    Label l = new Label(labelText);
    l.setFont(Font.font("System", FontWeight.BOLD, 12));
    Label v = new Label(valueText);
    v.setStyle("-fx-font-family: 'Monospaced';");
    grid.add(l, 0, row);
    grid.add(v, 1, row);
  }
}
