package org.chuck.ide;

import java.util.List;
import java.util.prefs.Preferences;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import org.chuck.audio.ChuckAudio;
import org.chuck.midi.MidiIn;
import org.chuck.midi.MidiOut;
import org.chuck.midi.RtMidi;

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

  // Theme Callbacks
  private java.util.function.Consumer<String> onThemeChanged;
  private Runnable onColorsChanged;

  public PreferencesTab(Preferences prefs) {
    this.prefs = prefs;
    setFitToWidth(true);
    setPadding(new Insets(10));

    VBox container = new VBox(15);
    container.setPadding(new Insets(5));

    // --- Audio Engine Section ---
    TitledPane audioSection = createAudioSection();

    // --- MIDI Section ---
    TitledPane midiSection = createMidiSection();

    // --- Visualizers Section ---
    TitledPane visSection = createVisualizerSection();

    // --- Editor Section ---
    TitledPane editorSection = createEditorSection();

    // --- Syntax Colors Section ---
    TitledPane colorSection = createColorSection();

    // --- Virtual MIDI Section ---
    TitledPane virtualMidiSection = createVirtualMidiSection();

    container
        .getChildren()
        .addAll(
            audioSection, midiSection, virtualMidiSection, visSection, editorSection, colorSection);
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

    CheckBox parallelCb = new CheckBox("Parallel Shred Execution");
    parallelCb.setSelected(prefs.getBoolean("engine.parallel", false));
    parallelCb
        .selectedProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putBoolean("engine.parallel", newV);
            });

    grid.add(new Label("Sample Rate:"), 0, 0);
    grid.add(srBox, 1, 0);
    grid.add(new Label("Buffer Size:"), 0, 1);
    grid.add(bufBox, 1, 1);
    grid.add(new Label("Output Device:"), 0, 2);
    grid.add(outDevBox, 1, 2);
    grid.add(new Label("Input Device:"), 0, 3);
    grid.add(inDevBox, 1, 3);
    grid.add(parallelCb, 0, 4, 2, 1);
    grid.add(applyBtn, 0, 5, 2, 1);

    TitledPane pane = new TitledPane("Audio Engine", grid);
    pane.setCollapsible(false);
    return pane;
  }

  private TitledPane createMidiSection() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));

    // Status Row
    boolean nativeOk = RtMidi.isAvailable();
    Circle statusDot = new Circle(6, nativeOk ? Color.GREEN : Color.RED);
    Label statusLabel =
        new Label(
            nativeOk
                ? "Native MIDI (RtMidi) Active"
                : "Native MIDI Not Found (JavaSound Fallback)");
    HBox statusBox = new HBox(8, statusDot, statusLabel);
    statusBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

    // Library Path Row
    TextField libPathField = new TextField(prefs.get("midi.libPath", ""));
    libPathField.setPromptText("Path to rtmidi.dll / .so / .dylib");
    Button browseBtn = new Button("...");
    browseBtn.setOnAction(
        e -> {
          javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
          dc.setTitle("Select RtMidi Library Directory");
          java.io.File folder = dc.showDialog(getScene().getWindow());
          if (folder != null) {
            libPathField.setText(folder.getAbsolutePath());
            prefs.put("midi.libPath", folder.getAbsolutePath());
          }
        });

    Button helpBtn = new Button("?");
    helpBtn.setTooltip(new Tooltip("How to get RtMidi?"));
    helpBtn.setOnAction(
        e -> {
          Alert alert = new Alert(Alert.AlertType.INFORMATION);
          alert.setTitle("RtMidi Setup Help");
          alert.setHeaderText("How to get the Native MIDI Library");

          String os = System.getProperty("os.name").toLowerCase();
          String instructions = "";
          if (os.contains("mac")) {
            instructions =
                "macOS Setup:\n"
                    + "1. Install Homebrew (brew.sh)\n"
                    + "2. Run: brew install rtmidi\n"
                    + "3. The library is usually in /opt/homebrew/lib (Silicon) or /usr/local/lib (Intel).\n"
                    + "4. Browse to that folder and select it.";
          } else if (os.contains("win")) {
            instructions =
                "Windows Setup:\n"
                    + "1. Download a pre-compiled rtmidi.dll (e.g., from a reputable project like Bespoke Synth or VCV Rack).\n"
                    + "2. OR use 'vcpkg install rtmidi:x64-windows' if you have vcpkg.\n"
                    + "3. Place the DLL in a folder and browse to it here.";
          } else {
            instructions =
                "Linux Setup:\n"
                    + "1. Run: sudo apt-get install librtmidi-dev (or equivalent)\n"
                    + "2. The library is usually in /usr/lib/x86_64-linux-gnu/\n"
                    + "3. Browse to that folder and select it.";
          }

          TextArea area = new TextArea(instructions);
          area.setEditable(false);
          area.setWrapText(true);
          area.setPrefHeight(150);
          alert.getDialogPane().setContent(area);
          alert.showAndWait();
        });

    libPathField.textProperty().addListener((obs, ov, nv) -> prefs.put("midi.libPath", nv));
    HBox pathBox = new HBox(5, libPathField, browseBtn, helpBtn);
    javafx.scene.layout.HBox.setHgrow(libPathField, javafx.scene.layout.Priority.ALWAYS);

    // API Selection (Native only)
    ChoiceBox<RtMidi.Api> apiBox = new ChoiceBox<>();
    if (nativeOk) {
      apiBox.setItems(FXCollections.observableArrayList(RtMidi.getCompiledApis()));
      apiBox.getItems().add(0, RtMidi.Api.UNSPECIFIED);

      int savedApiId = prefs.getInt("midi.api", RtMidi.Api.UNSPECIFIED.id);
      apiBox.setValue(RtMidi.Api.fromId(savedApiId));
      apiBox
          .getSelectionModel()
          .selectedItemProperty()
          .addListener(
              (obs, ov, nv) -> {
                if (nv != null) prefs.putInt("midi.api", nv.id);
              });
    } else {
      apiBox.setDisable(true);
    }

    // Input Device List (Read-only discovery)
    ListView<String> inList = new ListView<>(FXCollections.observableArrayList(MidiIn.list()));
    inList.setPrefHeight(100);

    // Output Device List (Read-only discovery)
    ListView<String> outList = new ListView<>(FXCollections.observableArrayList(MidiOut.list()));
    outList.setPrefHeight(100);

    Button refreshBtn = new Button("Refresh Devices");
    refreshBtn.setOnAction(
        e -> {
          RtMidi.reinit();
          inList.setItems(FXCollections.observableArrayList(MidiIn.list()));
          outList.setItems(FXCollections.observableArrayList(MidiOut.list()));

          // Update status label/dot if it changed
          boolean nowOk = RtMidi.isAvailable();
          statusDot.setFill(nowOk ? Color.GREEN : Color.RED);
          statusLabel.setText(
              nowOk ? "Native MIDI (RtMidi) Active" : "Native MIDI Not Found (JavaSound Fallback)");
          if (nowOk && apiBox.isDisabled()) {
            apiBox.setDisable(false);
            apiBox.setItems(FXCollections.observableArrayList(RtMidi.getCompiledApis()));
            apiBox.getItems().add(0, RtMidi.Api.UNSPECIFIED);
            apiBox.setValue(RtMidi.Api.UNSPECIFIED);
          }
        });

    // Filters
    CheckBox sysexCb = new CheckBox("Ignore Sysex");
    sysexCb.setSelected(prefs.getBoolean("midi.ignoreSysex", false));
    sysexCb
        .selectedProperty()
        .addListener((obs, ov, nv) -> prefs.putBoolean("midi.ignoreSysex", nv));

    CheckBox timeCb = new CheckBox("Ignore Timing");
    timeCb.setSelected(prefs.getBoolean("midi.ignoreTime", true));
    timeCb.selectedProperty().addListener((obs, ov, nv) -> prefs.putBoolean("midi.ignoreTime", nv));

    grid.add(statusBox, 0, 0, 2, 1);
    grid.add(new Label("Native Lib Path:"), 0, 1);
    grid.add(pathBox, 1, 1);
    grid.add(new Label("Preferred API:"), 0, 2);
    grid.add(apiBox, 1, 2);
    grid.add(new Label("Input Ports:"), 0, 3);
    grid.add(inList, 1, 3);
    grid.add(new Label("Output Ports:"), 0, 4);
    grid.add(outList, 1, 4);
    grid.add(refreshBtn, 1, 5);

    HBox filters = new HBox(15, sysexCb, timeCb);
    grid.add(new Label("Filters:"), 0, 6);
    grid.add(filters, 1, 6);

    TitledPane pane = new TitledPane("MIDI Settings", grid);
    pane.setCollapsible(false);
    return pane;
  }

  private TitledPane createVirtualMidiSection() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));

    boolean nativeOk = RtMidi.isAvailable();
    String os = System.getProperty("os.name").toLowerCase();
    boolean supportsVirtual = nativeOk && (os.contains("mac") || os.contains("linux"));

    Label infoLabel =
        new Label(
            supportsVirtual
                ? "Create virtual ports for other apps to see."
                : "Virtual ports not supported on Windows JavaSound.");
    infoLabel.setStyle("-fx-font-style: italic; -fx-text-fill: #666;");

    TextField nameField = new TextField("ChucK-Java");
    Button createInBtn = new Button("Create Virtual IN");
    Button createOutBtn = new Button("Create Virtual OUT");

    createInBtn.setDisable(!supportsVirtual);
    createOutBtn.setDisable(!supportsVirtual);

    createInBtn.setOnAction(
        e -> {
          MidiIn min = new MidiIn(null); // Headless monitor
          min.openVirtual(nameField.getText());
          Alert alert =
              new Alert(
                  Alert.AlertType.INFORMATION,
                  "Virtual Input Port '" + nameField.getText() + "' created.");
          alert.show();
        });

    createOutBtn.setOnAction(
        e -> {
          MidiOut mout = new MidiOut();
          mout.openVirtual(nameField.getText());
          Alert alert =
              new Alert(
                  Alert.AlertType.INFORMATION,
                  "Virtual Output Port '" + nameField.getText() + "' created.");
          alert.show();
        });

    grid.add(infoLabel, 0, 0, 2, 1);
    grid.add(new Label("Port Name:"), 0, 1);
    grid.add(nameField, 1, 1);
    grid.add(createInBtn, 0, 2);
    grid.add(createOutBtn, 1, 2);

    TitledPane pane = new TitledPane("Virtual MIDI Ports", grid);
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

    CheckBox autoSaveCb = new CheckBox("Auto-save on Run");
    autoSaveCb.setSelected(prefs.getBoolean("editor.autoSave", false));
    autoSaveCb
        .selectedProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.putBoolean("editor.autoSave", newV);
              if (onEditorSettingsChanged != null) onEditorSettingsChanged.run();
            });

    grid.add(new Label("Font Size:"), 0, 0);
    grid.add(fontSpinner, 1, 0);
    grid.add(new Label("Tab Width:"), 0, 1);
    grid.add(tabSpinner, 1, 1);
    grid.add(useSpacesCb, 0, 2, 2, 1);
    grid.add(smartIndentCb, 0, 3, 2, 1);
    grid.add(autoSaveCb, 0, 4, 2, 1);

    TitledPane pane = new TitledPane("Editor", grid);
    pane.setCollapsible(false);
    return pane;
  }

  private TitledPane createColorSection() {
    GridPane grid = new GridPane();
    grid.setHgap(10);
    grid.setVgap(8);
    grid.setPadding(new Insets(10));

    ChoiceBox<String> themeBox =
        new ChoiceBox<>(
            FXCollections.observableArrayList(
                "Default Light", "Dark (VS Code)", "Classic ChucK", "Monokai"));
    themeBox.setValue(prefs.get("color.theme", "Default Light"));
    themeBox
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.put("color.theme", newV);
              if (onThemeChanged != null) onThemeChanged.accept(newV);
            });

    grid.add(new Label("Theme Preset:"), 0, 0);
    grid.add(themeBox, 1, 0);

    grid.add(new Label("Doc Comments:"), 0, 1);
    grid.add(createColorPicker("color.doc", "#6a9955"), 1, 1);

    grid.add(new Label("Comments:"), 0, 2);
    grid.add(createColorPicker("color.comment", "#6a9955"), 1, 2);

    grid.add(new Label("Annotations:"), 0, 3);
    grid.add(createColorPicker("color.annotation", "#c792ea"), 1, 3);

    grid.add(new Label("Keywords:"), 0, 4);
    grid.add(createColorPicker("color.keyword", "#c586c0"), 1, 4);

    grid.add(new Label("Types (UGens):"), 0, 5);
    grid.add(createColorPicker("color.type", "#4ec9b0"), 1, 5);

    grid.add(new Label("Built-ins:"), 0, 6);
    grid.add(createColorPicker("color.builtin", "#9cdcfe"), 1, 6);

    grid.add(new Label("Literals:"), 0, 7);
    grid.add(createColorPicker("color.boolean", "#569cd6"), 1, 7);

    grid.add(new Label("Strings:"), 0, 8);
    grid.add(createColorPicker("color.string", "#ce9178"), 1, 8);

    grid.add(new Label("Numbers:"), 0, 9);
    grid.add(createColorPicker("color.number", "#b5cea8"), 1, 9);

    grid.add(new Label("Operators:"), 0, 10);
    grid.add(createColorPicker("color.chuckop", "#d4d4d4"), 1, 10);

    grid.add(new Label("Editor Background:"), 0, 11);
    grid.add(createColorPicker("color.background", "#FFFFFF"), 1, 11);

    TitledPane pane = new TitledPane("Syntax Colors", grid);
    pane.setCollapsible(false);
    return pane;
  }

  private ColorPicker createColorPicker(String key, String def) {
    ColorPicker cp = new ColorPicker(Color.web(prefs.get(key, def)));
    cp.valueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              prefs.put(key, toHex(newV));
              if (onColorsChanged != null) onColorsChanged.run();
            });
    return cp;
  }

  private String toHex(Color c) {
    return String.format(
        "#%02x%02x%02x",
        (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
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

  public void setOnColorsChanged(Runnable handler) {
    this.onColorsChanged = handler;
  }

  public void setOnThemeChanged(java.util.function.Consumer<String> handler) {
    this.onThemeChanged = handler;
  }
}
