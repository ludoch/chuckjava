package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.prefs.Preferences;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.chuck.audio.ChuckAudio;
import org.chuck.audio.analysis.FFT;
import org.chuck.audio.util.Gain;
import org.chuck.audio.util.Scope;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckVM;

/** Main IDE class for ChucK-Java, now refactored into smaller components. */
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

    // Initial tab
    addNewTab("Untitled.ck", "");

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
    // Fixed: required: org.chuck.core.ChuckVM,int,int,float
    audio = new ChuckAudio(vm, 512, 2, (float) prefSampleRate);
    vm.setAudio(audio);
    vm.initIO(
        new java.io.PrintStream(new ConsoleOutputStream(this::print)),
        new java.io.PrintStream(new ConsoleOutputStream(this::print)));

    masterGain = new Gain();
    masterGain.setGain(prefs.getFloat("audio.masterGain", 0.8f));
    // Fixed: use chuckTo instead of connect
    vm.dac.chuckTo(masterGain);
    masterGain.chuckTo(vm.blackhole);

    editorSupport = new EditorSupport(prefs, stage);
  }

  private void setupUI(Stage primaryStage) {
    primaryStage.setTitle("ChucK-Java IDE");

    // Menu and Toolbar
    MenuBar menuBar = createMenuBar(primaryStage);
    ToolBar toolBar = createToolBar();

    // Project Browser
    projectBrowser = new IDEProjectBrowser(new File("."), this::loadFileIntoEditor, this::print);
    projectBrowser.setPrefWidth(200);

    // Tab Pane for Editors
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

    // Visualizers
    FFT analyzer = new FFT(1024);
    vm.getDacChannel(0).chuckTo(analyzer);
    Scope scope = new Scope(1024);
    vm.getDacChannel(0).chuckTo(scope);
    visualizerPanel = new VisualizerPanel(vm, audio, analyzer, scope);

    // Output and MIDI
    outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");

    pianoKeyboard = new PianoKeyboard();
    pianoKeyboard.setPrefHeight(100);

    midiMonitor = new MidiMonitor();
    setupMidiMonitors();

    // Right Panel: Shred List
    shredListView = new ListView<>();
    shredListView.setCellFactory(lv -> new ShredListCell(this));
    VBox rightPanel = new VBox(5, new Label(" Active Shreds"), shredListView);
    rightPanel.setPrefWidth(200);

    // Left TabPane: Project Browser and Sequencer
    Tab projectTab = new Tab("Project", projectBrowser);
    projectTab.setClosable(false);

    sequencerPanel = new SequencerPanel(vm);
    Tab seqTab = new Tab("Sequencer", sequencerPanel);
    seqTab.setClosable(false);

    leftTabPane = new TabPane(projectTab, seqTab);
    leftTabPane.setPrefWidth(250);

    // Layout
    SplitPane hSplit = new SplitPane(leftTabPane, tabPane, rightPanel);
    hSplit.setDividerPositions(0.20, 0.80);

    VBox bottomPanel = new VBox(new SplitPane(outputArea, pianoKeyboard), statusBar);
    SplitPane vSplit = new SplitPane(hSplit, bottomPanel);
    vSplit.setOrientation(Orientation.VERTICAL);
    vSplit.setDividerPositions(0.75);

    BorderPane root = new BorderPane();
    root.setTop(new VBox(menuBar, toolBar));
    root.setRight(visualizerPanel);
    root.setCenter(vSplit);

    Scene scene = new Scene(root, 1350, 850);
    applyInlineStyles(scene);
    setupHidFilters(scene);

    primaryStage.setScene(scene);
    primaryStage.show();
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
      print("Error loading file: " + e.getMessage());
    }
  }

  private MenuBar createMenuBar(Stage stage) {
    MenuBar mb = new MenuBar();
    Menu fileMenu = new Menu("_File");
    MenuItem newItem = new MenuItem("New");
    newItem.setOnAction(e -> addNewTab("Untitled.ck", ""));
    fileMenu.getItems().addAll(newItem, new SeparatorMenuItem());

    MenuItem openItem = new MenuItem("Open...");
    openItem.setOnAction(
        e -> {
          FileChooser fc = new FileChooser();
          File f = fc.showOpenDialog(stage);
          if (f != null) loadFileIntoEditor(f);
        });
    fileMenu.getItems().add(openItem);

    mb.getMenus().addAll(fileMenu, new Menu("_Edit"), new Menu("_View"), createTutorialMenu());
    return mb;
  }

  private Menu createTutorialMenu() {
    Menu m = new Menu("_Tutorial");
    addTutorialStep(
        m,
        "1. Getting Started",
        "Welcome to ChucK-Java!",
        "/* Welcome to ChucK! */\nSinOsc s => dac;\n0.5 => s.gain;\n1::second => now;");
    addTutorialStep(
        m,
        "2. Melody",
        "Using arrays for sequencing.",
        "/* Melody loop */\nSinOsc s => dac;\n[60, 62, 64, 65, 67] @=> int scale[];\nfor(0=>int i;;i++) {\n  scale[i%scale.cap()] => Std.mtof => s.freq;\n  200::ms => now;\n}");
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
    addBtn.setOnAction(e -> addShred());
    addShredBtnRef = addBtn;

    Button replaceBtn = new Button("Replace Shred");
    replaceBtn.setOnAction(e -> replaceShred());
    replaceBtnRef = replaceBtn;

    return new ToolBar(addBtn, replaceBtn);
  }

  private void addShred() {
    Tab sel = tabPane.getSelectionModel().getSelectedItem();
    if (sel instanceof EditorTab et) {
      String code = et.getEditor().getText();
      String name = et.getText().replaceFirst("^\\* ", "");
      int id = vm.run(code, name);
      if (id > 0) {
        et.setLastSporkedShredId(id);
        ChuckShred s = vm.getShred(id);
        shredListView.getItems().add(new ShredInfo(id, name, s));
      }
    }
  }

  private void replaceShred() {
    Tab sel = tabPane.getSelectionModel().getSelectedItem();
    if (sel instanceof EditorTab et) {
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

        // Sync visual sequencer cursor
        if (sequencerPanel != null) {
          int step = (int) vm.getGlobalInt("seq_current_step");
          sequencerPanel.setStep(step);
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
    scene
        .getStylesheets()
        .add(
            "data:text/css;base64,"
                + java.util.Base64.getEncoder()
                    .encodeToString(
                        editorSupport
                            .generateSyntaxCss()
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8)));
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
