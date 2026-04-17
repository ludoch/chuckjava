package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.prefs.Preferences;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;
import org.chuck.audio.ChuckAudio;
import org.chuck.audio.analysis.FFT;
import org.chuck.audio.util.Gain;
import org.chuck.audio.util.Scope;
import org.chuck.core.ChuckVM;

/**
 * Main IDE class for ChucK-Java. Refactored into components with full logic and all UI features
 * restored.
 */
public class ChuckIDE extends Application {
  private final Preferences prefs = Preferences.userNodeForPackage(ChuckIDE.class);
  private Stage stage;
  private ChuckVM vm;
  private ChuckAudio audio;

  // Components
  private EditorSupport editorSupport;
  private VisualizerPanel visualizerPanel;
  private StatusBar statusBar;
  private IDEProjectBrowser projectBrowser;
  private SequencerPanel sequencerPanel;
  private ControlSurface controlSurface;

  // UI Elements
  private TabPane tabPane;
  private TabPane leftTabPane;
  private ListView<ShredInfo> shredListView;
  private TextArea outputArea;
  private Button addShredBtnRef;
  private Button replaceBtnRef;
  private Menu recentMenu;
  private VBox footer;
  private PianoKeyboard pianoKeyboard;

  // MIDI
  private MidiMonitor midiMonitor;
  private MidiRecorder midiRecorder = new MidiRecorder();
  private Circle midiActivityIndicator;
  private Timeline midiActivityTimeline;

  // Master Gain
  private Gain masterGain;

  // State
  private boolean lockdownMode = false;
  private int stallFrameCount = 0;
  private long lastVmTimeSample = -1;
  private static final int STALL_FRAMES_LOCKDOWN = 60;
  private static final int MAX_RECENT = 10;
  private final List<String> recentFiles = new ArrayList<>();

  // Editor Preferences
  private int prefFontSize = 13;
  private boolean prefSmartIndent = true;
  private boolean prefUseSpaces = true;
  private int prefTabWidth = 4;
  private int prefSampleRate = 44100;

  @Override
  public void start(Stage primaryStage) {
    this.stage = primaryStage;
    loadPreferences();

    initVM();
    setupUI(primaryStage);

    // Initial tab
    addNewTab(
        "Untitled.ck",
        "/* Default SinOsc script */\n"
            + "SinOsc s => dac;\n"
            + "0.5 => s.gain;\n"
            + "440 => s.freq;\n"
            + "1::second => now;\n");

    if (audio != null) audio.start();
    visualizerPanel.start();
    startAnimationTimer();

    // SHUTDOWN HANDLER
    primaryStage.setOnCloseRequest(
        e -> {
          if (audio != null) audio.stop();
          if (vm != null) vm.clear();
          Platform.exit();
          System.exit(0);
        });
  }

  private void loadPreferences() {
    prefFontSize = prefs.getInt("editor.fontSize", 13);
    prefSmartIndent = prefs.getBoolean("editor.smartIndent", true);
    prefUseSpaces = prefs.getBoolean("editor.useSpaces", true);
    prefTabWidth = prefs.getInt("editor.tabWidth", 4);
    prefSampleRate = prefs.getInt("audio.srate", 44100);

    recentFiles.clear();
    for (int i = 0; i < MAX_RECENT; i++) {
      String path = prefs.get("recent." + i, null);
      if (path != null) recentFiles.add(path);
    }
  }

  private void initVM() {
    vm = new ChuckVM(prefSampleRate, 2);
    audio = new ChuckAudio(vm, 512, 2, (float) prefSampleRate);
    vm.setAudio(audio);
    vm.initIO(
        new java.io.PrintStream(new ConsoleOutputStream(this::print)),
        new java.io.PrintStream(new ConsoleOutputStream(this::print)));

    audio.setMasterGain(prefs.getFloat("audio.masterGain", 0.8f));
    editorSupport = new EditorSupport(prefs, stage);
  }

  private void setupUI(Stage primaryStage) {
    primaryStage.setTitle("ChucK-Java IDE");

    MenuBar menuBar = createMenuBar(primaryStage);
    ToolBar toolBar = createToolBar();

    // ── LEFT PANEL ──
    projectBrowser = new IDEProjectBrowser(new File("."), this::loadFileIntoEditor, this::print);
    Tab projectTab = new Tab("Project", projectBrowser);
    projectTab.setClosable(false);

    sequencerPanel = new SequencerPanel(vm);
    Tab seqTab = new Tab("Sequencer", sequencerPanel);
    seqTab.setClosable(false);

    controlSurface = new ControlSurface();
    controlSurface.setVm(vm);
    Tab controlTab = new Tab("Control", controlSurface);
    controlTab.setClosable(false);

    midiMonitor = new MidiMonitor();
    Tab midiTab = new Tab("MIDI", midiMonitor);
    midiTab.setClosable(false);

    PreferencesTab prefsTabComp = new PreferencesTab(prefs);
    prefsTabComp.setOnEditorSettingsChanged(this::applyPreferences);
    prefsTabComp.setOnAudioRestart(
        (sr, bs, out, in) -> {
          print("Audio configuration updated. Please restart IDE for hardware changes.\n");
          audio.setMasterGain(prefs.getFloat("audio.masterGain", 0.8f));
        });
    Tab settingsTab = new Tab("Settings", prefsTabComp);
    settingsTab.setClosable(false);

    leftTabPane = new TabPane(projectTab, seqTab, controlTab, midiTab, settingsTab);
    leftTabPane.setPrefWidth(320);

    // ── CENTER PANEL ──
    tabPane = new TabPane();
    statusBar = new StatusBar(vm, audio, prefSampleRate);
    tabPane
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, sel) -> {
              if (sel instanceof EditorTab et) {
                statusBar.setFileName(et.getText().replaceFirst("^\\* ", ""));
              }
            });

    // ── RIGHT PANEL ──
    shredListView = new ListView<>();
    shredListView.setCellFactory(lv -> new ShredListCell(this));
    VBox shredBox = new VBox(5, new Label(" Active Shreds"), shredListView);
    VBox.setVgrow(shredListView, Priority.ALWAYS);

    FFT analyzer = new FFT(1024);
    vm.getDacChannel(0).chuckTo(analyzer);
    Scope scope = new Scope(1024);
    vm.getDacChannel(0).chuckTo(scope);
    visualizerPanel = new VisualizerPanel(vm, audio, analyzer, scope);
    visualizerPanel.setPrefHeight(300);

    VBox rightPanel = new VBox(5, shredBox, visualizerPanel);
    rightPanel.setPrefWidth(350);
    rightPanel.setPadding(new Insets(5));

    // ── BOTTOM PANEL ──
    outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");

    pianoKeyboard = new PianoKeyboard();
    pianoKeyboard.setPrefHeight(100);

    midiActivityIndicator = new Circle(5, Color.DARKRED);
    midiActivityTimeline =
        new Timeline(
            new KeyFrame(Duration.ZERO, e -> midiActivityIndicator.setFill(Color.LIME)),
            new KeyFrame(Duration.millis(50), e -> midiActivityIndicator.setFill(Color.DARKRED)));

    setupMidiMonitors();

    Slider masterGainSlider = new Slider(0, 1.0, prefs.getFloat("audio.masterGain", 0.8f));
    masterGainSlider.setOrientation(Orientation.VERTICAL);
    masterGainSlider.setShowTickLabels(true);
    masterGainSlider
        .valueProperty()
        .addListener(
            (obs, old, val) -> {
              audio.setMasterGain(val.floatValue());
              prefs.putFloat("audio.masterGain", val.floatValue());
            });
    VBox masterBox = new VBox(5, new Label("Vol"), masterGainSlider);
    masterBox.setPadding(new Insets(5));
    masterBox.setAlignment(javafx.geometry.Pos.CENTER);

    SplitPane horizontalSplit = new SplitPane(leftTabPane, tabPane, rightPanel);
    horizontalSplit.setDividerPositions(0.2, 0.75);

    HBox bottomHBox = new HBox(5, outputArea, masterBox);
    HBox.setHgrow(outputArea, Priority.ALWAYS);

    footer = new VBox(bottomHBox, pianoKeyboard, statusBar);
    SplitPane verticalSplit = new SplitPane(horizontalSplit, footer);
    verticalSplit.setOrientation(Orientation.VERTICAL);
    verticalSplit.setDividerPositions(0.7);

    BorderPane root = new BorderPane();
    root.setTop(new VBox(menuBar, toolBar));
    root.setCenter(verticalSplit);

    Scene scene = new Scene(root, 1400, 950);
    applyInlineStyles(scene);
    setupHidFilters(scene);

    primaryStage.setScene(scene);
    primaryStage.show();
  }

  private void applyPreferences() {
    loadPreferences();
    for (Tab t : tabPane.getTabs()) {
      if (t instanceof EditorTab et) {
        et.getEditor()
            .setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + prefFontSize + ";");
      }
    }
    applyInlineStyles(stage.getScene());
    print("Preferences applied.\n");
  }

  private void addNewTab(String title, String content) {
    EditorTab tab =
        new EditorTab(
            title,
            content,
            editorSupport,
            statusBar,
            prefFontSize,
            prefSmartIndent,
            prefUseSpaces,
            prefTabWidth);
    tabPane.getTabs().add(tab);
    tabPane.getSelectionModel().select(tab);
  }

  private void loadFileIntoEditor(File f) {
    try {
      String content = Files.readString(f.toPath());
      addNewTab(f.getName(), content);
      if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
        et.setFile(f);
      }
      addToRecentFiles(f.getAbsolutePath());
    } catch (IOException e) {
      print("Error loading file: " + e.getMessage() + "\n");
    }
  }

  private void addToRecentFiles(String path) {
    recentFiles.remove(path);
    recentFiles.add(0, path);
    if (recentFiles.size() > MAX_RECENT) {
      recentFiles.subList(MAX_RECENT, recentFiles.size()).clear();
    }
    for (int i = 0; i < MAX_RECENT; i++) {
      if (i < recentFiles.size()) prefs.put("recent." + i, recentFiles.get(i));
      else prefs.remove("recent." + i);
    }
    updateRecentMenu();
  }

  private MenuBar createMenuBar(Stage stage) {
    MenuBar mb = new MenuBar();

    Menu fileMenu = new Menu("_File");
    MenuItem newItem = new MenuItem("New");
    newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
    newItem.setOnAction(e -> addNewTab("Untitled.ck", ""));

    MenuItem openItem = new MenuItem("Open...");
    openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
    openItem.setOnAction(
        e -> {
          FileChooser fc = new FileChooser();
          fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ChucK Files", "*.ck"));
          File f = fc.showOpenDialog(stage);
          if (f != null) loadFileIntoEditor(f);
        });

    recentMenu = new Menu("Open Recent");
    updateRecentMenu();

    MenuItem saveItem = new MenuItem("Save");
    saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
    saveItem.setOnAction(e -> saveCurrentTab());

    fileMenu.getItems().addAll(newItem, openItem, recentMenu, saveItem, new SeparatorMenuItem());

    Menu editMenu = new Menu("_Edit");
    MenuItem undoItem = new MenuItem("Undo");
    undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
    undoItem.setOnAction(
        e -> {
          if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
            et.getEditor().undo();
        });
    MenuItem redoItem = new MenuItem("Redo");
    redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
    redoItem.setOnAction(
        e -> {
          if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
            et.getEditor().redo();
        });
    MenuItem selectAll = new MenuItem("Select All");
    selectAll.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
    selectAll.setOnAction(
        e -> {
          if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et)
            et.getEditor().selectAll();
        });

    editMenu
        .getItems()
        .addAll(
            undoItem,
            redoItem,
            selectAll,
            new SeparatorMenuItem(),
            new MenuItem("Cut"),
            new MenuItem("Copy"),
            new MenuItem("Paste"));

    Menu viewMenu = new Menu("_View");
    MenuItem zoomIn = new MenuItem("Zoom In");
    zoomIn.setAccelerator(new KeyCodeCombination(KeyCode.EQUALS, KeyCombination.CONTROL_DOWN));
    zoomIn.setOnAction(
        e -> {
          prefFontSize++;
          applyPreferences();
        });
    MenuItem zoomOut = new MenuItem("Zoom Out");
    zoomOut.setAccelerator(new KeyCodeCombination(KeyCode.MINUS, KeyCombination.CONTROL_DOWN));
    zoomOut.setOnAction(
        e -> {
          prefFontSize = Math.max(8, prefFontSize - 1);
          applyPreferences();
        });

    CheckMenuItem showKeyboard = new CheckMenuItem("Show Keyboard");
    showKeyboard.setSelected(true);
    showKeyboard.setOnAction(
        e -> {
          if (showKeyboard.isSelected()) {
            if (!footer.getChildren().contains(pianoKeyboard))
              footer.getChildren().add(1, pianoKeyboard);
          } else {
            footer.getChildren().remove(pianoKeyboard);
          }
        });

    viewMenu.getItems().addAll(zoomIn, zoomOut, new SeparatorMenuItem(), showKeyboard);

    Menu helpMenu = new Menu("_Help");
    MenuItem githubItem = new MenuItem("GitHub Repository");
    githubItem.setOnAction(
        e -> getHostServices().showDocument("https://github.com/ludoch/chuckjava"));

    MenuItem aboutItem = new MenuItem("About ChucK-Java");
    aboutItem.setOnAction(
        e -> {
          Alert a =
              new Alert(
                  Alert.AlertType.INFORMATION,
                  "ChucK-Java IDE\nJDK 25 + Project Loom + Panama\nModular component architecture.");
          a.show();
        });
    helpMenu.getItems().addAll(githubItem, aboutItem);

    mb.getMenus()
        .addAll(
            fileMenu, editMenu, viewMenu, createTutorialMenu(), new Menu("_Examples"), helpMenu);
    loadExamples(new File("examples"), mb.getMenus().get(4));

    return mb;
  }

  private void updateRecentMenu() {
    if (recentMenu == null) return;
    recentMenu.getItems().clear();
    for (String path : recentFiles) {
      File f = new File(path);
      MenuItem item = new MenuItem(f.getName());
      item.setOnAction(e -> loadFileIntoEditor(f));
      recentMenu.getItems().add(item);
    }
  }

  private void loadExamples(File dir, Menu parent) {
    if (!dir.exists() || !dir.isDirectory()) return;
    File[] files = dir.listFiles();
    if (files == null) return;
    Arrays.sort(files);
    for (File f : files) {
      if (f.isDirectory()) {
        Menu sub = new Menu(f.getName());
        loadExamples(f, sub);
        if (!sub.getItems().isEmpty()) parent.getItems().add(sub);
      } else if (f.getName().endsWith(".ck")) {
        MenuItem item = new MenuItem(f.getName());
        item.setOnAction(e -> loadFileIntoEditor(f));
        parent.getItems().add(item);
      }
    }
  }

  private void saveCurrentTab() {
    if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
      File f = et.getFile();
      if (f == null) {
        FileChooser fc = new FileChooser();
        f = fc.showSaveDialog(stage);
      }
      if (f != null) {
        try {
          Files.writeString(f.toPath(), et.getEditor().getText());
          et.setFile(f);
          et.setSaved();
          addToRecentFiles(f.getAbsolutePath());
        } catch (IOException e) {
          print("Error saving: " + e.getMessage() + "\n");
        }
      }
    }
  }

  private Menu createTutorialMenu() {
    Menu m = new Menu("_Tutorial");
    addTutorialStep(
        m,
        "1. Getting Started",
        "First steps with SinOsc",
        "/* Welcome to ChucK-Java! In this step, we connect a Unit Generator (SinOsc)\n"
            + "   to the audio output (dac), set its frequency and gain, and wait for 1 second. */\n\n"
            + "SinOsc s => dac;\n0.5 => s.gain;\n440 => s.freq;\n1::second => now;");

    addTutorialStep(
        m,
        "2. Melody and Scales",
        "Sequence with arrays",
        "/* Music often uses scales. Here we use an array to define a melody\n"
            + "   and another to define the major scale intervals. */\n\n"
            + "SinOsc s => dac;\n0.5 => s.gain;\n[60, 62, 64, 65, 67, 69, 71, 72] @=> int major[];\n"
            + "for(0 => int i; ; i++) {\n  major[i % major.cap()] => Std.mtof => s.freq;\n  200::ms => now;\n}");

    addTutorialStep(
        m,
        "3. Functions",
        "Modularizing your code",
        "/* Functions allow you to reuse logic. */\n\n"
            + "SinOsc s => dac;\nfun void play(int note, dur d) {\n  note => Std.mtof => s.freq;\n  d => now;\n}\n"
            + "while(true) { play(60, 500::ms); play(67, 500::ms); }");

    addTutorialStep(
        m,
        "4. Strong Timing",
        "The power of 'now'",
        "/* ChucK's strongest feature is 'strong timing'.\n"
            + "   We can advance time by precise durations. */\n\n"
            + "Impulse i => dac;\nwhile(true) {\n  1.0 => i.next;\n  100::ms => now;\n}");

    addTutorialStep(
        m,
        "5. Concurrent Audio",
        "Sporking shreds",
        "/* Parallel execution with spork. */\n\n"
            + "fun void saw() { SawOsc s => dac; 0.2 => s.gain; while(true) { 60 => Std.mtof => s.freq; 1::second => now; } }\n"
            + "fun void sine() { SinOsc s => dac; 0.2 => s.gain; while(true) { 67 => Std.mtof => s.freq; 1::second => now; } }\n"
            + "spork ~ saw();\nspork ~ sine();\n1::week => now;");

    return m;
  }

  private void addTutorialStep(Menu menu, String title, String desc, String code) {
    MenuItem item = new MenuItem(title);
    item.setOnAction(
        e -> {
          addNewTab(title.replace(" ", "_") + ".ck", code);
          Alert a = new Alert(Alert.AlertType.INFORMATION);
          a.setTitle("Tutorial: " + title);
          a.setHeaderText(null);
          a.setContentText(desc);
          a.show();
        });
    menu.getItems().add(item);
  }

  private ToolBar createToolBar() {
    Button addBtn = new Button("Add Shred");
    addBtn.setStyle("-fx-background-color: #b8f0b8; -fx-font-weight: bold;");
    addBtn.setOnAction(e -> addShred());
    addShredBtnRef = addBtn;

    Button replaceBtn = new Button("Replace Shred");
    replaceBtn.setOnAction(e -> replaceShred());
    replaceBtnRef = replaceBtn;

    Button clearBtn = new Button("Clear VM");
    clearBtn.setStyle("-fx-background-color: #f0b8b8;");
    clearBtn.setOnAction(e -> vm.clear());

    return new ToolBar(addBtn, replaceBtn, new Separator(), clearBtn);
  }

  private void addShred() {
    if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
      String code = et.getEditor().getText();
      String name = et.getText().replaceFirst("^\\* ", "");
      String args = et.getArguments();
      int id = vm.run(code, name);
      if (id > 0) {
        et.setLastSporkedShredId(id);
        shredListView.getItems().add(new ShredInfo(id, name, vm.getShred(id)));
      }
    }
  }

  private void replaceShred() {
    if (tabPane.getSelectionModel().getSelectedItem() instanceof EditorTab et) {
      int lastId = et.getLastSporkedShredId();
      if (lastId > 0) vm.removeShred(lastId);
      addShred();
    }
  }

  private void startAnimationTimer() {
    new javafx.animation.AnimationTimer() {
      private long lastTextUpdate = 0;

      @Override
      public void handle(long now) {
        updateVMLogic();
        if (sequencerPanel != null) {
          sequencerPanel.setStep((int) vm.getGlobalInt("seq_current_step"));
        }
        statusBar.updateVUMeters();
        if (now - lastTextUpdate > 100_000_000L) {
          statusBar.updateVMText();
          statusBar.updateCpuLoad();
          updateShredList();
          lastTextUpdate = now;
        }
      }
    }.start();
  }

  private void updateVMLogic() {
    long nowSample = vm.getCurrentTime();
    boolean shredsActive = !shredListView.getItems().isEmpty();
    if (shredsActive) {
      if (nowSample == lastVmTimeSample) {
        if (++stallFrameCount >= STALL_FRAMES_LOCKDOWN) enterLockdown();
      } else {
        stallFrameCount = 0;
        if (lockdownMode) exitLockdown();
      }
    } else {
      stallFrameCount = 0;
      if (lockdownMode) exitLockdown();
    }
    lastVmTimeSample = nowSample;
  }

  private void updateShredList() {
    shredListView.getItems().removeIf(si -> si.shred == null || si.shred.isDone());
    shredListView.getItems().forEach(ShredInfo::updateDuration);
  }

  private void enterLockdown() {
    if (lockdownMode) return;
    lockdownMode = true;
    Platform.runLater(
        () -> {
          if (addShredBtnRef != null) addShredBtnRef.setDisable(true);
          if (replaceBtnRef != null) replaceBtnRef.setDisable(true);
        });
  }

  private void exitLockdown() {
    if (!lockdownMode) return;
    lockdownMode = false;
    Platform.runLater(
        () -> {
          if (addShredBtnRef != null) addShredBtnRef.setDisable(false);
          if (replaceBtnRef != null) replaceBtnRef.setDisable(false);
        });
  }

  private void print(String s) {
    Platform.runLater(
        () -> {
          outputArea.appendText(s);
          if (outputArea.getText().length() > 10000) outputArea.deleteText(0, 2000);
        });
  }

  private void setupMidiMonitors() {
    org.chuck.midi.ChuckMidiNative.addMonitor(
        (dev, msg) -> {
          pianoKeyboard.onMidiMessage(msg);
          if (midiActivityTimeline != null) midiActivityTimeline.playFromStart();
          if (midiMonitor != null) midiMonitor.onMidiMessage(dev, msg);
          if (midiRecorder != null) midiRecorder.onMidiMessage(dev, msg);
        });
    org.chuck.midi.ChuckMidiOutNative.addMonitor(
        (dev, msg) -> {
          pianoKeyboard.onMidiMessage("Out", msg);
          if (midiMonitor != null) midiMonitor.onMidiMessage("Out", msg);
          if (midiRecorder != null) midiRecorder.onMidiMessage("Out", msg);
        });
  }

  private void applyInlineStyles(Scene scene) {
    String css = editorSupport.generateSyntaxCss();
    scene
        .getStylesheets()
        .add(
            "data:text/css;base64,"
                + java.util.Base64.getEncoder()
                    .encodeToString(css.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
  }

  private void setupHidFilters(Scene scene) {
    scene.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          org.chuck.hid.HidMsg msg = new org.chuck.hid.HidMsg();
          msg.type = 1;
          msg.which = e.getCode().getCode();
          vm.dispatchHidMsg(msg);
        });
  }

  public static void main(String[] args) {
    launch(args);
  }

  private static class ConsoleOutputStream extends java.io.OutputStream {
    private final java.util.function.Consumer<String> printer;

    public ConsoleOutputStream(java.util.function.Consumer<String> printer) {
      this.printer = printer;
    }

    @Override
    public void write(int b) {
      printer.accept(String.valueOf((char) b));
    }
  }

  private static class ShredListCell extends ListCell<ShredInfo> {
    private final ChuckIDE ide;

    public ShredListCell(ChuckIDE ide) {
      this.ide = ide;
    }

    @Override
    protected void updateItem(ShredInfo item, boolean empty) {
      super.updateItem(item, empty);
      if (empty || item == null) {
        setGraphic(null);
        setText(null);
      } else {
        HBox hbox = new HBox(5, new Label("[" + item.id + "] " + item.name), new Region());
        HBox.setHgrow(hbox.getChildren().get(1), Priority.ALWAYS);
        Button remove = new Button("X");
        remove.setOnAction(e -> ide.vm.removeShred(item.id));
        hbox.getChildren().add(remove);
        setGraphic(hbox);
      }
    }
  }
}
