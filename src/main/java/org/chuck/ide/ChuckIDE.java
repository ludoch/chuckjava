package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
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

/** Main IDE class for ChucK-Java. Refactored into components with consolidated layout. */
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

  // UI Elements
  private TabPane tabPane;
  private TabPane leftTabPane;
  private ListView<ShredInfo> shredListView;
  private TextArea outputArea;
  private Button addShredBtnRef;
  private Button replaceBtnRef;

  // MIDI
  private PianoKeyboard pianoKeyboard;
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
  private static final int STALL_FRAMES_LOCKDOWN = 40;

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

    // Default script
    addNewTab(
        "Untitled.ck",
        "/* Default SinOsc script */\n"
            + "SinOsc s => dac;\n"
            + "0.5 => s.gain;\n"
            + "440 => s.freq;\n"
            + "1::second => now;\n");

    visualizerPanel.start();
    startAnimationTimer();
  }

  private void loadPreferences() {
    prefFontSize = prefs.getInt("editor.fontSize", 13);
    prefSmartIndent = prefs.getBoolean("editor.smartIndent", true);
    prefUseSpaces = prefs.getBoolean("editor.useSpaces", true);
    prefTabWidth = prefs.getInt("editor.tabWidth", 4);
    prefSampleRate = prefs.getInt("audio.srate", 44100);
  }

  private void initVM() {
    vm = new ChuckVM(prefSampleRate, 2);
    audio = new ChuckAudio(vm, 512, 2, (float) prefSampleRate);
    vm.setAudio(audio);
    vm.initIO(
        new java.io.PrintStream(new ConsoleOutputStream(this::print)),
        new java.io.PrintStream(new ConsoleOutputStream(this::print)));

    masterGain = new Gain();
    masterGain.setGain(prefs.getFloat("audio.masterGain", 0.8f));
    vm.dac.chuckTo(masterGain);
    masterGain.chuckTo(vm.blackhole);

    editorSupport = new EditorSupport(prefs, stage);
  }

  private void setupUI(Stage primaryStage) {
    primaryStage.setTitle("ChucK-Java IDE");

    MenuBar menuBar = createMenuBar(primaryStage);
    ToolBar toolBar = createToolBar();

    // ── LEFT PANEL: Consolidated utility tabs ──
    projectBrowser = new IDEProjectBrowser(new File("."), this::loadFileIntoEditor, this::print);
    Tab projectTab = new Tab("Project", projectBrowser);
    projectTab.setClosable(false);

    sequencerPanel = new SequencerPanel(vm);
    Tab seqTab = new Tab("Sequencer", sequencerPanel);
    seqTab.setClosable(false);

    shredListView = new ListView<>();
    shredListView.setCellFactory(lv -> new ShredListCell(this));
    Tab shredsTab = new Tab("Shreds", shredListView);
    shredsTab.setClosable(false);

    midiMonitor = new MidiMonitor();
    Tab midiTab = new Tab("MIDI", midiMonitor);
    midiTab.setClosable(false);

    PreferencesTab prefsTabComp = new PreferencesTab(prefs);
    Tab settingsTab = new Tab("Settings", prefsTabComp);
    settingsTab.setClosable(false);

    leftTabPane = new TabPane(projectTab, seqTab, shredsTab, midiTab, settingsTab);
    leftTabPane.setPrefWidth(320);

    // ── CENTER PANEL: Editor tabs ──
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

    // ── BOTTOM PANEL: Console, Visualizers, Piano ──
    outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");

    FFT analyzer = new FFT(1024);
    vm.getDacChannel(0).chuckTo(analyzer);
    Scope scope = new Scope(1024);
    vm.getDacChannel(0).chuckTo(scope);
    visualizerPanel = new VisualizerPanel(vm, audio, analyzer, scope);
    visualizerPanel.setPrefWidth(350);

    pianoKeyboard = new PianoKeyboard();
    pianoKeyboard.setPrefHeight(100);

    midiActivityIndicator = new Circle(5, Color.DARKRED);
    midiActivityTimeline =
        new Timeline(
            new KeyFrame(Duration.ZERO, e -> midiActivityIndicator.setFill(Color.LIME)),
            new KeyFrame(Duration.millis(50), e -> midiActivityIndicator.setFill(Color.DARKRED)));

    setupMidiMonitors();

    // Master Gain
    Slider masterGainSlider = new Slider(0, 1.0, masterGain.getGain());
    masterGainSlider.setOrientation(Orientation.VERTICAL);
    masterGainSlider.setShowTickLabels(true);
    masterGainSlider
        .valueProperty()
        .addListener(
            (obs, old, val) -> {
              masterGain.setGain(val.floatValue());
              prefs.putFloat("audio.masterGain", val.floatValue());
            });
    VBox masterBox = new VBox(5, new Label("Vol"), masterGainSlider);
    masterBox.setPadding(new Insets(5));
    masterBox.setAlignment(javafx.geometry.Pos.CENTER);

    // Layout Assembly
    SplitPane mainSplit = new SplitPane(leftTabPane, tabPane);
    mainSplit.setDividerPositions(0.25);

    HBox bottomHBox = new HBox(5, outputArea, visualizerPanel, masterBox);
    HBox.setHgrow(outputArea, Priority.ALWAYS);

    VBox footer = new VBox(bottomHBox, pianoKeyboard, statusBar);
    SplitPane verticalSplit = new SplitPane(mainSplit, footer);
    verticalSplit.setOrientation(Orientation.VERTICAL);
    verticalSplit.setDividerPositions(0.7);

    BorderPane root = new BorderPane();
    root.setTop(new VBox(menuBar, toolBar));
    root.setCenter(verticalSplit);

    Scene scene = new Scene(root, 1350, 900);
    applyInlineStyles(scene);
    setupHidFilters(scene);

    primaryStage.setScene(scene);
    primaryStage.show();
    print("ChucK-Java Engine Online — JDK 25 | Project Loom | Panama\n");
  }

  private void applyPreferences() {
    loadPreferences();
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
    } catch (IOException e) {
      print("Error loading file: " + e.getMessage() + "\n");
    }
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

    MenuItem saveItem = new MenuItem("Save");
    saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
    saveItem.setOnAction(e -> saveCurrentTab());

    fileMenu.getItems().addAll(newItem, openItem, saveItem, new SeparatorMenuItem());

    Menu tutorialMenu = createTutorialMenu();
    Menu examplesMenu = new Menu("_Examples");
    loadExamples(new File("examples"), examplesMenu);

    Menu helpMenu = new Menu("_Help");
    MenuItem aboutItem = new MenuItem("About ChucK-Java");
    aboutItem.setOnAction(
        e -> {
          Alert a =
              new Alert(
                  Alert.AlertType.INFORMATION,
                  "ChucK-Java IDE\nJDK 25 + Project Loom + Panama\nBased on Princeton ChucK.");
          a.show();
        });
    helpMenu.getItems().add(aboutItem);

    mb.getMenus()
        .addAll(
            fileMenu, new Menu("_Edit"), new Menu("_View"), tutorialMenu, examplesMenu, helpMenu);
    return mb;
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
        fc.setInitialFileName(et.getText().replaceFirst("^\\* ", ""));
        f = fc.showSaveDialog(stage);
      }
      if (f != null) {
        try {
          Files.writeString(f.toPath(), et.getEditor().getText());
          et.setFile(f);
          et.setSaved();
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
        "/* Welcome to ChucK-Java! */\n"
            + "SinOsc s => dac;\n"
            + "0.5 => s.gain;\n"
            + "1::second => now;");
    addTutorialStep(
        m,
        "2. Melody and Scales",
        "Sequence with arrays",
        "/* Music often uses scales. */\n"
            + "SinOsc s => dac;\n"
            + "0.5 => s.gain;\n"
            + "[60, 62, 64, 65, 67, 69, 71, 72] @=> int major[];\n"
            + "for(0 => int i; ; i++) {\n"
            + "  major[i % major.cap()] => Std.mtof => s.freq;\n"
            + "  200::ms => now;\n"
            + "}");
    addTutorialStep(
        m,
        "3. Functions",
        "Modularizing your code",
        "/* Functions in ChucK */\n"
            + "SinOsc s => dac;\n"
            + "fun void play(int note, dur d) {\n"
            + "  note => Std.mtof => s.freq;\n"
            + "  d => now;\n"
            + "}\n"
            + "while(true) { play(60, 500::ms); play(67, 500::ms); }");
    addTutorialStep(
        m,
        "4. Strong Timing",
        "The power of 'now'",
        "/* Advance time with precision. */\n"
            + "Impulse i => dac;\n"
            + "while(true) {\n"
            + "  1.0 => i.next;\n"
            + "  100::ms => now;\n"
            + "}");
    addTutorialStep(
        m,
        "5. Concurrent Audio",
        "Sporking shreds",
        "/* Parallel execution */\n"
            + "fun void saw() { SawOsc s => dac; 0.2 => s.gain; while(true) { 60 => Std.mtof => s.freq; 1::second => now; } }\n"
            + "fun void sine() { SinOsc s => dac; 0.2 => s.gain; while(true) { 67 => Std.mtof => s.freq; 1::second => now; } }\n"
            + "spork ~ saw();\n"
            + "spork ~ sine();\n"
            + "1::week => now;");
    return m;
  }

  private void addTutorialStep(Menu menu, String title, String desc, String code) {
    MenuItem item = new MenuItem(title);
    item.setOnAction(
        e -> {
          addNewTab(title.replace(" ", "_") + ".ck", code);
          Alert a = new Alert(Alert.AlertType.INFORMATION, desc);
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
    if (!shredListView.getItems().isEmpty()) {
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
    lockdownMode = true;
    if (addShredBtnRef != null) addShredBtnRef.setDisable(true);
    if (replaceBtnRef != null) replaceBtnRef.setDisable(true);
  }

  private void exitLockdown() {
    lockdownMode = false;
    if (addShredBtnRef != null) addShredBtnRef.setDisable(false);
    if (replaceBtnRef != null) replaceBtnRef.setDisable(false);
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
