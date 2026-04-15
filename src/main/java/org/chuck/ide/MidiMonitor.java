package org.chuck.ide;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.chuck.midi.MidiMsg;

/** A UI component that displays a history of incoming MIDI messages. */
public class MidiMonitor extends VBox {
  private final TableView<MidiEntry> table;
  private final ObservableList<MidiEntry> entries = FXCollections.observableArrayList();
  private static final int MAX_ENTRIES = 200;

  public MidiMonitor() {
    setSpacing(5);
    setPadding(new Insets(5));

    table = new TableView<>(entries);
    table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    VBox.setVgrow(table, Priority.ALWAYS);

    TableColumn<MidiEntry, String> timeCol = new TableColumn<>("Time");
    timeCol.setCellValueFactory(new PropertyValueFactory<>("time"));
    timeCol.setPrefWidth(80);

    TableColumn<MidiEntry, String> typeCol = new TableColumn<>("Type");
    typeCol.setCellValueFactory(new PropertyValueFactory<>("type"));
    typeCol.setPrefWidth(100);

    TableColumn<MidiEntry, Integer> chanCol = new TableColumn<>("Ch");
    chanCol.setCellValueFactory(new PropertyValueFactory<>("channel"));
    chanCol.setPrefWidth(40);

    TableColumn<MidiEntry, Integer> d1Col = new TableColumn<>("Data 1");
    d1Col.setCellValueFactory(new PropertyValueFactory<>("data1"));
    d1Col.setPrefWidth(60);

    TableColumn<MidiEntry, Integer> d2Col = new TableColumn<>("Data 2");
    d2Col.setCellValueFactory(new PropertyValueFactory<>("data2"));
    d2Col.setPrefWidth(60);

    table.getColumns().addAll(timeCol, typeCol, chanCol, d1Col, d2Col);

    Button clearBtn = new Button("Clear");
    clearBtn.setOnAction(e -> entries.clear());

    getChildren().addAll(table, clearBtn);
  }

  public void onMidiMessage(MidiMsg msg) {
    Platform.runLater(
        () -> {
          entries.add(0, new MidiEntry(msg));
          if (entries.size() > MAX_ENTRIES) {
            entries.remove(MAX_ENTRIES, entries.size());
          }
        });
  }

  public static class MidiEntry {
    private final String time;
    private final String type;
    private final int channel;
    private final int data1;
    private final int data2;

    public MidiEntry(MidiMsg msg) {
      this.time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"));
      int status = msg.data1 & 0xF0;
      this.channel = (msg.data1 & 0x0F) + 1;
      this.data1 = msg.data2;
      this.data2 = msg.data3;

      switch (status) {
        case 0x80:
          this.type = "Note Off";
          break;
        case 0x90:
          this.type = (msg.data3 > 0) ? "Note On" : "Note Off";
          break;
        case 0xA0:
          this.type = "Poly Aftertouch";
          break;
        case 0xB0:
          this.type = "Control Change";
          break;
        case 0xC0:
          this.type = "Program Change";
          break;
        case 0xD0:
          this.type = "Channel Aftertouch";
          break;
        case 0xE0:
          this.type = "Pitch Bend";
          break;
        case 0xF0:
          this.type = "System";
          break;
        default:
          this.type = String.format("0x%02X", status);
          break;
      }
    }

    public String getTime() {
      return time;
    }

    public String getType() {
      return type;
    }

    public int getChannel() {
      return channel;
    }

    public int getData1() {
      return data1;
    }

    public int getData2() {
      return data2;
    }
  }
}
