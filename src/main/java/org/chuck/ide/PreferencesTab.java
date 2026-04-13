package org.chuck.ide;

import java.util.List;
import java.util.prefs.Preferences;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import org.chuck.audio.ChuckAudio;

/**
 * A tab component for the ChucK-Java IDE that manages Audio, Visualizer, and Editor preferences.
 */
public class PreferencesTab extends ScrollPane {

  @FunctionalInterface
  public interface AudioRestartHandler {
    void handle(int sampleRate, int bufferSize, String outputDevice, String inputDevice);
  }

  private final Preferences prefs;
  private AudioRestartHandler audioRestartHandler;

  // Editor Callbacks
  private Runnable onEditorSettingsChanged;

  // Visualizer Callbacks
  private java.util.function.Consumer<Integer> onFFTSizeChanged;
  private java.util.function.Consumer<Integer> onScopeWindowChanged;

  public PreferencesTab(Preferences prefs) {
    this.prefs = prefs;
    setFitToWidth(true);
    setPadding(new Insets(10));

    VBox container = new VBox(15);
    container.setPadding(new Insets(5));

    // --- Audio Engine Section ---
    TitledPane audioSection = createAudioSection();

    // --- Visualizers Section ---
    TitledPane visSection = createVisualizerSection();

    // --- Editor Section ---
    TitledPane editorSection = createEditorSection();

    container.getChildren().addAll(audioSection, visSection, editorSection);
    setContent(container);
  }

  private TitledPane createAudioSection() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));

    ChoiceBox<Integer> srBox =
        new ChoiceBox<>(FXCollections.observableArrayList(22050, 44100, 48000, 88200, 96000));
    srBox.setValue(prefs.getInt("audio.sampleRate", 44100));

    ChoiceBox<Integer> bufBox =
        new ChoiceBox<>(FXCollections.observableArrayList(128, 256, 512, 1024, 2048));
    bufBox.setValue(prefs.getInt("audio.bufferSize", 512));

    List<String> outDevices = ChuckAudio.getOutputDeviceNames();
    ChoiceBox<String> outDevBox = new ChoiceBox<>(FXCollections.observableArrayList(outDevices));
    String currentOut = prefs.get("audio.outputDevice", "");
    outDevBox.setValue(
        outDevices.contains(currentOut)
            ? currentOut
            : (outDevices.isEmpty() ? "" : outDevices.get(0)));

    List<String> inDevices = ChuckAudio.getInputDeviceNames();
    ChoiceBox<String> inDevBox = new ChoiceBox<>(FXCollections.observableArrayList(inDevices));
    String currentIn = prefs.get("audio.inputDevice", "");
    inDevBox.setValue(
        inDevices.contains(currentIn) ? currentIn : (inDevices.isEmpty() ? "" : inDevices.get(0)));

    Button applyBtn = new Button("Apply Audio Settings");
    applyBtn.setMaxWidth(Double.MAX_VALUE);
    applyBtn.setOnAction(
        e -> {
          if (audioRestartHandler != null) {
            audioRestartHandler.handle(
                srBox.getValue(), bufBox.getValue(), outDevBox.getValue(), inDevBox.getValue());
          }
        });

    grid.add(new Label("Sample Rate:"), 0, 0);
    grid.add(srBox, 1, 0);
    grid.add(new Label("Buffer Size:"), 0, 1);
    grid.add(bufBox, 1, 1);
    grid.add(new Label("Output Device:"), 0, 2);
    grid.add(outDevBox, 1, 2);
    grid.add(new Label("Input Device:"), 0, 3);
    grid.add(inDevBox, 1, 3);
    grid.add(applyBtn, 0, 4, 2, 1);

    TitledPane pane = new TitledPane("Audio Engine", grid);
    pane.setCollapsible(false);
    return pane;
  }

  private TitledPane createVisualizerSection() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));

    ChoiceBox<Integer> fftBox =
        new ChoiceBox<>(FXCollections.observableArrayList(256, 512, 1024, 2048, 4096));
    fftBox.setValue(prefs.getInt("vis.fftSize", 512));
    fftBox
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putInt("vis.fftSize", newV);
              if (onFFTSizeChanged != null) onFFTSizeChanged.accept(newV);
            });

    Spinner<Integer> scopeSpinner = new Spinner<>(64, 4096, prefs.getInt("vis.scopeWindow", 512));
    scopeSpinner.setEditable(true);
    scopeSpinner
        .valueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putInt("vis.scopeWindow", newV);
              if (onScopeWindowChanged != null) onScopeWindowChanged.accept(newV);
            });

    grid.add(new Label("FFT Size:"), 0, 0);
    grid.add(fftBox, 1, 0);
    grid.add(new Label("Scope Window:"), 0, 1);
    grid.add(scopeSpinner, 1, 1);

    TitledPane pane = new TitledPane("Visualizers", grid);
    pane.setCollapsible(false);
    return pane;
  }

  private TitledPane createEditorSection() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));

    Spinner<Integer> fontSpinner = new Spinner<>(8, 48, prefs.getInt("editor.fontSize", 13));
    fontSpinner.setEditable(true);
    fontSpinner
        .valueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putInt("editor.fontSize", newV);
              if (onEditorSettingsChanged != null) onEditorSettingsChanged.run();
            });

    Spinner<Integer> tabSpinner = new Spinner<>(1, 16, prefs.getInt("editor.tabWidth", 4));
    tabSpinner.setEditable(true);
    tabSpinner
        .valueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putInt("editor.tabWidth", newV);
              if (onEditorSettingsChanged != null) onEditorSettingsChanged.run();
            });

    CheckBox useSpacesCb = new CheckBox("Use Spaces");
    useSpacesCb.setSelected(prefs.getBoolean("editor.useSpaces", true));
    useSpacesCb
        .selectedProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putBoolean("editor.useSpaces", newV);
              if (onEditorSettingsChanged != null) onEditorSettingsChanged.run();
            });

    CheckBox smartIndentCb = new CheckBox("Smart Indent");
    smartIndentCb.setSelected(prefs.getBoolean("editor.smartIndent", true));
    smartIndentCb
        .selectedProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putBoolean("editor.smartIndent", newV);
              if (onEditorSettingsChanged != null) onEditorSettingsChanged.run();
            });

    grid.add(new Label("Font Size:"), 0, 0);
    grid.add(fontSpinner, 1, 0);
    grid.add(new Label("Tab Width:"), 0, 1);
    grid.add(tabSpinner, 1, 1);
    grid.add(useSpacesCb, 0, 2, 2, 1);
    grid.add(smartIndentCb, 0, 3, 2, 1);

    TitledPane pane = new TitledPane("Editor", grid);
    pane.setCollapsible(false);
    return pane;
  }

  public void setOnAudioRestart(AudioRestartHandler handler) {
    this.audioRestartHandler = handler;
  }

  public void setOnEditorSettingsChanged(Runnable handler) {
    this.onEditorSettingsChanged = handler;
  }

  public void setOnFFTSizeChanged(java.util.function.Consumer<Integer> handler) {
    this.onFFTSizeChanged = handler;
  }

  public void setOnScopeWindowChanged(java.util.function.Consumer<Integer> handler) {
    this.onScopeWindowChanged = handler;
  }
}
