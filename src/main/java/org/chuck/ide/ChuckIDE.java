package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuBar;
import javafx.scene.control.MenuItem;
import javafx.scene.control.Separator;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.SplitPane;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ToolBar;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.chuck.audio.ChuckAudio;
import org.chuck.audio.UAnaBlob;
import org.chuck.audio.analysis.FFT;
import org.chuck.audio.util.Gain;
import org.chuck.audio.util.Scope;
import org.chuck.core.ChuckCode;
import org.chuck.core.ChuckDSL;
import org.chuck.core.ChuckShred;
import org.chuck.core.ChuckVM;
import org.chuck.core.doc;
import org.chuck.hid.HidMsg;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Professional desktop IDE for ChucK-Java (JDK 25). Features syntax highlighting (RichTextFX), line
 * numbers, keyboard shortcuts, shred management, WAV recording, and a file browser.
 */
public class ChuckIDE extends Application {

  /** Completion item: the text to insert, a short label to display, and its kind badge. */
  private record CompItem(String insertText, String label, String kind) {}

  private static final List<String> TYPE_CANDIDATES =
      List.of(
          // Primitives / control
          "int",
          "float",
          "dur",
          "time",
          "string",
          "void",
          "auto",
          "if",
          "else",
          "while",
          "for",
          "repeat",
          "do",
          "until",
          "return",
          "break",
          "continue",
          "new",
          "spork",
          "fun",
          "class",
          "extends",
          "public",
          "private",
          "static",
          "abstract",
          "interface",
          "loop",
          "switch",
          "case",
          "default",
          // Built-in events / types
          "Event",
          "Object",
          "UGen",
          "UAna",
          // Oscillators
          "SinOsc",
          "SawOsc",
          "TriOsc",
          "SqrOsc",
          "PulseOsc",
          "Phasor",
          "Noise",
          "Impulse",
          "Step",
          "BlitSaw",
          "BlitSquare",
          "Blit",
          // Filters
          "LPF",
          "HPF",
          "BPF",
          "BRF",
          "ResonZ",
          "BiQuad",
          "OnePole",
          "OneZero",
          "TwoPole",
          "TwoZero",
          "PoleZero",
          // Effects
          "Echo",
          "Delay",
          "DelayL",
          "DelayA",
          "DelayP",
          "Chorus",
          "JCRev",
          "AllPass",
          "Pan2",
          "Gain",
          // Envelopes
          "ADSR",
          "Adsr",
          "Envelope",
          // STK instruments
          "Mandolin",
          "Clarinet",
          "Plucked",
          "Rhodey",
          "Wurley",
          "BeeThree",
          "HevyMetl",
          "PercFlut",
          "TubeBell",
          "FMVoices",
          "Bowed",
          "StifKarp",
          "Moog",
          "Flute",
          "Sitar",
          "Brass",
          "Saxofony",
          "Shakers",
          "ModalBar",
          "VoicForm",
          // Analysis (UAna)
          "FFT",
          "IFFT",
          "DCT",
          "IDCT",
          "ZCR",
          "RMS",
          "Centroid",
          "MFCC",
          "SFM",
          "Kurtosis",
          "AutoCorr",
          "XCorr",
          "Chroma",
          "FeatureCollector",
          // Buffers / I/O
          "SndBuf",
          "WvOut",
          "LiSa",
          "MidiIn",
          "MidiMsg",
          "MidiFileIn",
          "OscIn",
          "OscOut",
          "OscMsg",
          "OscEvent",
          "Hid",
          "HidMsg",
          "HidOut",
          "IO",
          "FileIO",
          "StringTokenizer",
          "ConsoleInput",
          "KBHit",
          "SerialIO",
          // AI / ML
          "KNN",
          "KNN2",
          "SVM",
          "MLP",
          "HMM",
          "PCA",
          // Builtins
          "dac",
          "adc",
          "blackhole",
          "now",
          "second",
          "ms",
          "samp",
          "minute",
          "hour",
          "day",
          "week",
          "Std",
          "Math",
          "Machine",
          "me",
          "chout",
          "cherr",
          "newline",
          // Literals
          "true",
          "false",
          "null",
          "maybe",
          "pi");

  private static final List<String> MEMBER_CANDIDATES =
      List.of(
          "freq",
          "gain",
          "noteOn",
          "noteOff",
          "last",
          "id",
          "exit",
          "arg",
          "numArgs",
          "add",
          "remove",
          "clear",
          "duration",
          "record",
          "play",
          "pos",
          "rate",
          "loop",
          "rampUp",
          "rampDown",
          "coeffs",
          "mtof",
          "ftom",
          "rand",
          "randf",
          "random",
          "sin",
          "cos",
          "pow",
          "sqrt",
          "abs",
          "floor",
          "ceil");

  private static final List<String> DURATION_CANDIDATES =
      List.of("second", "ms", "samp", "minute", "hour", "day", "week");

  // ── Syntax highlighting patterns ───────────────────────────────────────────
  private static final String KEYWORD_PATTERN =
      "\\b(if|else|while|for|repeat|return|break|continue|new|spork|fun|class|"
          + "extends|public|private|static|void|abstract|interface|loop|do|until|"
          + "auto|switch|case|default)\\b";
  private static final String TYPE_PATTERN =
      "\\b(int|float|dur|time|string|complex|polar|vec2|vec3|vec4|"
          + "SinOsc|SawOsc|TriOsc|SqrOsc|PulseOsc|Phasor|Noise|Impulse|Step|BlitSaw|BlitSquare|Blit|"
          + "LPF|HPF|BPF|BRF|ResonZ|BiQuad|OnePole|OneZero|TwoPole|TwoZero|PoleZero|"
          + "Echo|Delay|DelayL|DelayA|DelayP|Chorus|JCRev|AllPass|Pan2|Gain|"
          + "ADSR|Adsr|Envelope|"
          + "Mandolin|Clarinet|Plucked|Rhodey|Wurley|BeeThree|HevyMetl|PercFlut|TubeBell|FMVoices|"
          + "Bowed|StifKarp|Moog|Flute|Sitar|Brass|Saxofony|Shakers|ModalBar|VoicForm|"
          + "FFT|IFFT|DCT|IDCT|ZCR|RMS|Centroid|MFCC|SFM|Kurtosis|AutoCorr|XCorr|Chroma|FeatureCollector|"
          + "SndBuf|WvOut|LiSa|MidiIn|MidiMsg|MidiFileIn|"
          + "OscIn|OscOut|OscMsg|OscEvent|Hid|HidMsg|HidOut|"
          + "IO|FileIO|StringTokenizer|ConsoleInput|KBHit|SerialIO|"
          + "KNN|KNN2|SVM|MLP|HMM|PCA|"
          + "Event|Object|UGen|UAna)\\b";
  private static final String BUILTIN_PATTERN =
      "\\b(dac|adc|blackhole|now|second|ms|samp|minute|hour|day|week|"
          + "Std|Math|Machine|me|chout|cherr|newline)\\b";
  private static final String BOOLEAN_PATTERN = "\\b(true|false|null|maybe|pi|e|sqrt2)\\b";
  private static final String ANNOTATION_PATTERN =
      "@(doc|operator|construct|destruct|this|return)\\b";
  private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";
  private static final String STRING_PATTERN = "\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"";
  private static final String COMMENT_PATTERN = "//[^\n]*|/\\*.*?\\*/";
  private static final String CHUCK_OP_PATTERN = "=>|@=>|::|<=>|!=>";

  private static final Pattern HIGHLIGHT_PATTERN =
      Pattern.compile(
          "(?<COMMENT>"
              + COMMENT_PATTERN
              + ")"
              + "|(?<ANNOTATION>"
              + ANNOTATION_PATTERN
              + ")"
              + "|(?<KEYWORD>"
              + KEYWORD_PATTERN
              + ")"
              + "|(?<TYPE>"
              + TYPE_PATTERN
              + ")"
              + "|(?<BUILTIN>"
              + BUILTIN_PATTERN
              + ")"
              + "|(?<BOOLEAN>"
              + BOOLEAN_PATTERN
              + ")"
              + "|(?<STRING>"
              + STRING_PATTERN
              + ")"
              + "|(?<NUMBER>"
              + NUMBER_PATTERN
              + ")"
              + "|(?<CHUCKOP>"
              + CHUCK_OP_PATTERN
              + ")",
          Pattern.DOTALL);

  // ── State ──────────────────────────────────────────────────────────────────
  private ChuckVM vm;
  private ChuckAudio audio;
  private TabPane tabPane;
  private TextArea outputArea;

  // Model for active shreds
  public static class ShredInfo {
    public final int id;
    public final String name;
    public final long startTimeMillis;
    public final ChuckShred shred;
    public final javafx.beans.property.StringProperty durationProp =
        new javafx.beans.property.SimpleStringProperty("0.0s");

    public ShredInfo(int id, String name, ChuckShred shred) {
      this.id = id;
      this.name = name;
      this.shred = shred;
      this.startTimeMillis = System.currentTimeMillis();
    }

    public void updateDuration() {
      double secs = (System.currentTimeMillis() - startTimeMillis) / 1000.0;
      String s = String.format("%.1fs", secs);
      if (!s.equals(durationProp.get())) {
        durationProp.set(s);
      }
    }
  }

  /** A user-declared variable or function found by scanning the current editor text. */
  private record UserSymbol(String name, String type, String signature) {}

  private ListView<ShredInfo> shredListView;
  private Label statusLabel;
  private TreeView<File> fileBrowser;
  private Stage stage;

  // Track files per tab
  private final Map<Tab, File> tabToFile = new java.util.HashMap<>();

  // Track editor wrapper (StackPane with flash overlay) per tab
  private final Map<Tab, StackPane> tabToWrapper = new java.util.HashMap<>();

  // OTF: track which shred was sporked from each tab (last one wins)
  private final Map<Tab, Integer> tabToShredId = new java.util.HashMap<>();

  // Script arguments text field per tab (feeds me.arg(0), me.args(), etc.)
  private final Map<Tab, javafx.scene.control.TextField> tabToArgs = new java.util.HashMap<>();

  // Editor preferences (live values, persisted via java.util.prefs.Preferences)
  private int prefFontSize = 13;
  private boolean prefSmartIndent = true;
  private boolean prefUseSpaces = true;
  private int prefTabWidth = 4;

  // Audio preferences (take effect on next IDE launch)
  private int prefSampleRate = 44100;
  private int prefBufferSize = 512;
  private String prefOutputDevice = "";
  private String prefInputDevice = "";

  // VU meter bars (updated in animation timer)
  private javafx.scene.control.ProgressBar vuLeft;
  private javafx.scene.control.ProgressBar vuRight;

  // Dirty-tracking: original text saved per tab (for unsaved-changes prompt)
  private final Map<Tab, String> tabSavedText = new java.util.HashMap<>();

  // Toolbar button refs (needed for lockdown disable/enable)
  private Button addShredBtnRef;
  private Button replaceBtnRef;

  // Stall / lockdown detection (mirrors miniAudicle's 2-second stall timeout)
  private long lastVmTimeSample = -1;
  private int stallFrameCount = 0;
  private static final int STALL_FRAMES_LOCKDOWN = 40; // ~2 s at 20 Hz animation-timer rate
  private boolean lockdownMode = false;
  private Label stallWarningLabel;

  // Visualizers
  @SuppressWarnings("unused")
  private Pane visContainer; // Container for dynamic resizing

  private Canvas visualizerCanvas;
  private Canvas scopeCanvas;
  private FFT analyzer;
  private Scope scope;
  private javafx.animation.AnimationTimer visTimer;

  // Completion
  private Popup currentPopup;

  // Recent files
  private static final int MAX_RECENT = 10;
  private final Preferences prefs = Preferences.userNodeForPackage(ChuckIDE.class);
  private Menu recentFilesMenu;

  // Doc hover popup
  private Popup docHoverPopup;

  // Master Controls
  private Label vmTimeLabel;
  private Slider masterGainSlider;
  private Gain masterGain;

  @Override
  public void start(Stage primaryStage) {
    this.stage = primaryStage;

    // Load persisted editor preferences
    prefFontSize = prefs.getInt("editor.fontSize", 13);
    prefSmartIndent = prefs.getBoolean("editor.smartIndent", true);
    prefUseSpaces = prefs.getBoolean("editor.useSpaces", true);
    prefTabWidth = prefs.getInt("editor.tabWidth", 4);

    // Load persisted audio preferences
    prefSampleRate = prefs.getInt("audio.sampleRate", 44100);
    prefBufferSize = prefs.getInt("audio.bufferSize", 512);
    prefOutputDevice = prefs.get("audio.outputDevice", "");
    prefInputDevice = prefs.get("audio.inputDevice", "");

    vm = new ChuckVM(prefSampleRate);
    vm.addPrintListener(this::print);

    // Master gain is a scalar multiplier applied in ChuckAudio (not a UGen in the signal path).
    // Wiring the Gain UGen into the DAC pull-graph would leave it un-ticked → silence.
    masterGain = new Gain();

    audio = new ChuckAudio(vm, prefBufferSize, 2, prefSampleRate);
    audio.setOutputDeviceName(prefOutputDevice);
    audio.setInputDeviceName(prefInputDevice);

    List<String> rawArgs = getParameters().getRaw();
    for (String arg : rawArgs) {
      if (arg.startsWith("--verbose:")) {
        try {
          int v = Integer.parseInt(arg.substring("--verbose:".length()));
          audio.setVerbose(v);
        } catch (NumberFormatException ignored) {
        }
      }
    }

    audio.start();

    // Setup hidden analyzers for visualizers (mono)
    analyzer = new FFT(512);
    scope = new Scope(512);

    vm.getDacChannel(0).chuckTo(analyzer);
    vm.getDacChannel(0).chuckTo(scope);
    vm.blackhole.addSource(analyzer);
    vm.blackhole.addSource(scope);

    primaryStage.setTitle("ChucK-Java IDE (JDK 25)");

    MenuBar menuBar = createMenuBar(primaryStage);
    ToolBar toolBar = createToolBar();

    // ── Tabbed editor ──
    tabPane = new TabPane();

    boolean loadedAny = false;
    for (String arg : rawArgs) {
      if (arg.startsWith("-")) continue; // Skip flags
      File f = new File(arg);
      if (f.exists() && f.isFile()) {
        loadFileIntoEditor(f);
        loadedAny = true;
      }
    }

    if (!loadedAny) {
      addNewTab(
          "Untitled.ck",
          "// Welcome to ChucK-Java!\nSinOsc s => dac;\n0.5 => s.gain;\n440 => s.freq;\n1::second => now;");
    }

    // ── Left Panel (Project & UGens) ──
    fileBrowser = createFileBrowser();
    VBox projectBox = new VBox(fileBrowser);
    VBox.setVgrow(fileBrowser, Priority.ALWAYS);

    UGenBrowser ugenBrowser = new UGenBrowser();
    ugenBrowser.setOnInsert(
        snippet -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) {
            ed.insertText(ed.getCaretPosition(), snippet);
          }
        });

    TabPane leftTabPane = new TabPane();
    Tab projectTab = new Tab("Project", projectBox);
    projectTab.setClosable(false);
    Tab ugenTab = new Tab("UGens", ugenBrowser);
    ugenTab.setClosable(false);
    leftTabPane.getTabs().addAll(projectTab, ugenTab);

    VBox leftPanel = new VBox(leftTabPane);
    VBox.setVgrow(leftTabPane, Priority.ALWAYS);
    leftPanel.setPrefWidth(240);

    // ── Shred panel ──
    shredListView = new ListView<>();
    shredListView.setCellFactory(
        lv ->
            new ListCell<>() {
              private final HBox root = new HBox(5);
              private final Label idNameLabel = new Label();
              private final Label durationLabel = new Label();
              private final Button removeBtn = new Button("-");
              private final Region spacer = new Region();

              {
                idNameLabel.setStyle("-fx-font-weight: bold;");
                durationLabel.setTextFill(Color.GRAY);
                durationLabel.setStyle("-fx-font-family: 'Monospaced';");

                removeBtn.setStyle(
                    "-fx-padding: 1 6 1 6; -fx-background-color: #f0b8b8; -fx-font-weight: bold; -fx-text-fill: white; -fx-background-radius: 3;");
                removeBtn.setFocusTraversable(false);

                HBox.setHgrow(spacer, Priority.ALWAYS);
                root.getChildren().addAll(idNameLabel, durationLabel, spacer, removeBtn);
                root.setPadding(new Insets(2));
                root.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
              }

              @Override
              protected void updateItem(ShredInfo item, boolean empty) {
                super.updateItem(item, empty);
                durationLabel.textProperty().unbind();
                if (empty || item == null) {
                  setGraphic(null);
                  setText(null);
                } else {
                  idNameLabel.setText(item.id + ": " + item.name);
                  durationLabel
                      .textProperty()
                      .bind(javafx.beans.binding.Bindings.concat("[", item.durationProp, "]"));
                  removeBtn.setOnAction(
                      e -> {
                        vm.removeShred(item.id);
                        shredListView.getItems().remove(item);
                        updateStatus();
                      });
                  setGraphic(root);
                }
              }
            });

    // Visualizer Canvases (wrapped in StackPanes for resizing)
    visualizerCanvas = new Canvas();
    scopeCanvas = new Canvas();

    StackPane specStack = new StackPane(visualizerCanvas);
    specStack.setStyle("-fx-background-color: black;");
    specStack.setPrefHeight(80);
    visualizerCanvas.widthProperty().bind(specStack.widthProperty());
    visualizerCanvas.heightProperty().bind(specStack.heightProperty());

    StackPane scopeStack = new StackPane(scopeCanvas);
    scopeStack.setStyle("-fx-background-color: black;");
    scopeStack.setPrefHeight(80);
    scopeCanvas.widthProperty().bind(scopeStack.widthProperty());
    scopeCanvas.heightProperty().bind(scopeStack.heightProperty());

    VBox visBox =
        new VBox(2, new Label("Spectrum"), specStack, new Label("Oscilloscope"), scopeStack);
    visBox.setStyle("-fx-background-color: #222; -fx-padding: 5;");
    VBox.setVgrow(specStack, Priority.ALWAYS);
    VBox.setVgrow(scopeStack, Priority.ALWAYS);
    for (javafx.scene.Node n : visBox.getChildren()) {
      if (n instanceof Label l) l.setTextFill(Color.LIGHTGRAY);
    }

    // Master Controls
    vmTimeLabel = new Label("Time: 0.000s");
    vmTimeLabel.setStyle("-fx-font-family: 'Monospaced'; -fx-font-weight: bold;");

    masterGainSlider = new Slider(0, 1, 0.8);
    masterGainSlider.setShowTickMarks(true);
    masterGainSlider
        .valueProperty()
        .addListener(
            (obs, oldV, newV) -> {
              audio.setMasterGain(newV.floatValue());
            });

    // VU meters — live peak level indicators, updated by the animation timer
    vuLeft = new javafx.scene.control.ProgressBar(0.01);
    vuRight = new javafx.scene.control.ProgressBar(0.01);
    vuLeft.setMinWidth(100);
    vuRight.setMinWidth(100);
    vuLeft.setMaxWidth(Double.MAX_VALUE);
    vuRight.setMaxWidth(Double.MAX_VALUE);
    vuLeft.setStyle("-fx-accent: limegreen;");
    vuRight.setStyle("-fx-accent: limegreen;");

    HBox vuBox = new HBox(4, new Label("L"), vuLeft, new Label("R"), vuRight);
    vuBox.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    HBox.setHgrow(vuLeft, Priority.ALWAYS);
    HBox.setHgrow(vuRight, Priority.ALWAYS);

    VBox masterControls =
        new VBox(
            5, new Label("Master Gain"), masterGainSlider, new Label("Level"), vuBox, vmTimeLabel);
    masterControls.setStyle("-fx-background-color: #eee; -fx-padding: 8; -fx-border-color: #ccc;");
    masterControls.setMinHeight(0);

    // ── Right panel with SplitPane for height adjustment ──
    SplitPane rightSplit = new SplitPane();
    rightSplit.setOrientation(Orientation.VERTICAL);

    VBox shredBox = new VBox(5, new Label("Active Shreds"), shredListView);
    VBox.setVgrow(shredListView, Priority.ALWAYS);
    shredBox.setPadding(new Insets(5));
    shredBox.setMinHeight(0);

    rightSplit.getItems().addAll(shredBox, visBox, masterControls);
    rightSplit.setDividerPositions(0.3, 0.75);

    // Fix resize bug: allow components to shrink
    shredListView.setMinWidth(0);
    shredListView.setMinHeight(0);
    visBox.setMinWidth(0);
    visBox.setMinHeight(0);
    masterControls.setMinWidth(0);
    rightSplit.setMinWidth(0);
    rightSplit.setMinHeight(0);

    VBox rightPanel = new VBox(rightSplit);
    VBox.setVgrow(rightSplit, Priority.ALWAYS);
    rightPanel.setPrefWidth(250);
    rightPanel.setMinWidth(0);
    rightPanel.setMinHeight(0);

    startVisualizer();

    // ── Output console ──
    outputArea = new TextArea();
    outputArea.setEditable(false);
    outputArea.setPrefHeight(110);
    outputArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");

    Button clearConsoleBtn = new Button("Clear");
    clearConsoleBtn.setOnAction(e -> clearConsole());

    statusLabel = new Label("  Ready");
    HBox statusBar = new HBox(5, clearConsoleBtn, new Separator(Orientation.VERTICAL), statusLabel);
    statusBar.setStyle("-fx-background-color: #ddd; -fx-padding: 2;");
    VBox bottomPanel = new VBox(outputArea, statusBar);

    // ── Layout ──
    SplitPane hSplit = new SplitPane(leftPanel, tabPane, rightPanel);
    hSplit.setDividerPositions(0.18, 0.82);
    SplitPane vSplit = new SplitPane(hSplit, bottomPanel);
    vSplit.setOrientation(Orientation.VERTICAL);
    vSplit.setDividerPositions(0.76);

    BorderPane root = new BorderPane();
    root.setTop(new VBox(menuBar, toolBar));
    root.setCenter(vSplit);

    Scene scene = new Scene(root, 1250, 820);
    scene
        .getStylesheets()
        .add(
            getClass().getResource("/chuck-ide.css") != null
                ? getClass().getResource("/chuck-ide.css").toExternalForm()
                : "");
    applyInlineStyles(scene);

    setupHidFilters(scene);

    primaryStage.setScene(scene);
    primaryStage.show();

    print("ChucK-Java Engine Online — JDK 25 | Virtual Threads | Vector API");
  }

  private void setupHidFilters(Scene scene) {
    // Keyboard Filters
    scene.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          HidMsg msg = new HidMsg();
          msg.type = 1; // BUTTON_DOWN
          msg.which = e.getCode().getCode();
          msg.key = e.getCode().getCode();
          if (e.getText().length() > 0) msg.ascii = e.getText().charAt(0);
          vm.dispatchHidMsg(msg);
        });
    scene.addEventFilter(
        KeyEvent.KEY_RELEASED,
        e -> {
          HidMsg msg = new HidMsg();
          msg.type = 2; // BUTTON_UP
          msg.which = e.getCode().getCode();
          msg.key = e.getCode().getCode();
          vm.dispatchHidMsg(msg);
        });

    // Mouse Filters
    scene.addEventFilter(
        MouseEvent.MOUSE_MOVED,
        e -> {
          HidMsg msg = new HidMsg();
          msg.type = 3; // MOUSE_MOTION
          msg.x = (float) e.getSceneX();
          msg.y = (float) e.getSceneY();
          vm.dispatchHidMsg(msg);
        });
    scene.addEventFilter(
        MouseEvent.MOUSE_PRESSED,
        e -> {
          HidMsg msg = new HidMsg();
          msg.type = 1; // BUTTON_DOWN
          msg.which = e.getButton().ordinal();
          vm.dispatchHidMsg(msg);
        });
    scene.addEventFilter(
        MouseEvent.MOUSE_RELEASED,
        e -> {
          HidMsg msg = new HidMsg();
          msg.type = 2; // BUTTON_UP
          msg.which = e.getButton().ordinal();
          vm.dispatchHidMsg(msg);
        });
  }

  // ── Syntax highlighting ────────────────────────────────────────────────────

  private static StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher m = HIGHLIGHT_PATTERN.matcher(text);
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    int lastEnd = 0;
    while (m.find()) {
      String styleClass =
          m.group("COMMENT") != null
              ? "ck-comment"
              : m.group("ANNOTATION") != null
                  ? "ck-annotation"
                  : m.group("KEYWORD") != null
                      ? "ck-keyword"
                      : m.group("TYPE") != null
                          ? "ck-type"
                          : m.group("BUILTIN") != null
                              ? "ck-builtin"
                              : m.group("BOOLEAN") != null
                                  ? "ck-boolean"
                                  : m.group("STRING") != null
                                      ? "ck-string"
                                      : m.group("NUMBER") != null
                                          ? "ck-number"
                                          : m.group("CHUCKOP") != null ? "ck-chuckop" : null;
      spansBuilder.add(Collections.emptyList(), m.start() - lastEnd);
      spansBuilder.add(
          styleClass != null ? Collections.singleton(styleClass) : Collections.emptyList(),
          m.end() - m.start());
      lastEnd = m.end();
    }
    spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
    return spansBuilder.create();
  }

  /** Apply highlight colours as inline JavaFX CSS (avoids needing an external file). */
  private void applyInlineStyles(Scene scene) {
    String css =
        ".ck-comment    { -rtfx-background-color: transparent; -fx-fill: #6a9955; }"
            + ".ck-annotation { -fx-fill: #c792ea; -fx-font-style: italic; }"
            + ".ck-keyword    { -fx-fill: #c586c0; -fx-font-weight: bold; }"
            + ".ck-type       { -fx-fill: #4ec9b0; -fx-font-weight: bold; }"
            + ".ck-builtin    { -fx-fill: #9cdcfe; }"
            + ".ck-boolean    { -fx-fill: #569cd6; -fx-font-weight: bold; }"
            + ".ck-string     { -fx-fill: #ce9178; }"
            + ".ck-number     { -fx-fill: #b5cea8; }"
            + ".ck-chuckop    { -fx-fill: #d4d4d4; -fx-font-weight: bold; }";
    scene.getRoot().setStyle(css);
    // Inject into scene stylesheets so RichTextFX picks them up
    scene
        .getStylesheets()
        .add(
            "data:text/css,"
                + java.net.URLEncoder.encode(css, java.nio.charset.StandardCharsets.UTF_8));
  }

  private void addNewTab(String title, String content) {
    CodeArea editor = new CodeArea();
    editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
    editor.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + prefFontSize + ";");
    editor.replaceText(0, 0, content);

    editor
        .multiPlainChanges()
        .successionEnds(Duration.ofMillis(50))
        .subscribe(ignore -> editor.setStyleSpans(0, computeHighlighting(editor.getText())));
    editor.setStyleSpans(0, computeHighlighting(editor.getText()));

    // Code Completion Trigger — manual (Ctrl+Space) and auto (. and ::)
    editor.addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.SPACE && e.isControlDown()) {
            e.consume();
            showCompletionPopup(editor);
          }
        });

    editor.addEventHandler(
        KeyEvent.KEY_TYPED,
        e -> {
          String ch = e.getCharacter();
          if (".".equals(ch)) {
            Platform.runLater(() -> showCompletionPopup(editor));
          } else if (":".equals(ch)) {
            int pos = editor.getCaretPosition();
            if (pos >= 2 && "::".equals(editor.getText(pos - 2, pos))) {
              Platform.runLater(() -> showCompletionPopup(editor));
            }
          } else if ("(".equals(ch)) {
            Platform.runLater(() -> showParamHint(editor));
          }
        });

    // Hover doc — show after 600 ms over a word
    editor.setMouseOverTextDelay(java.time.Duration.ofMillis(600));
    editor.addEventHandler(
        MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN,
        e -> {
          int charIdx = e.getCharacterIndex();
          String text = editor.getText();
          String word = extractWordAt(text, charIdx);
          if (word.isEmpty()) return;
          String docText = lookupDoc(word, null);
          if (docText == null && charIdx > 0) {
            // Try member lookup: find dot before the hovered word
            String fullBefore = text.substring(0, charIdx);
            int lastDot = fullBefore.lastIndexOf('.');
            if (lastDot > 0) {
              String beforeDot = fullBefore.substring(0, lastDot);
              String varName = extractWordAt(beforeDot, beforeDot.length());
              if (!varName.isEmpty()) {
                String type = resolveVariableType(varName, fullBefore);
                docText = lookupDoc(type, word);
              }
            }
          }
          if (docText != null) showDocHoverPopup(docText, e.getScreenPosition());
        });
    editor.addEventHandler(MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> hideDocHoverPopup());

    editor.setOnMouseClicked(
        e -> {
          if (currentPopup != null) currentPopup.hide();
          hideDocHoverPopup();
        });

    // Smart indentation (mirrors miniAudicle's enable_smart_indentation / magic_words logic)
    editor.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.ENTER && !e.isControlDown() && !e.isAltDown()) {
            if (prefSmartIndent) {
              applySmartIndent(editor);
              e.consume();
            }
          } else if (e.getCode() == KeyCode.TAB && !e.isControlDown()) {
            e.consume();
            String ins = prefUseSpaces ? " ".repeat(prefTabWidth) : "\t";
            editor.insertText(editor.getCaretPosition(), ins);
          }
        });

    // Wrap editor in a StackPane so we can overlay a flash rectangle for OTF visual feedback
    javafx.scene.shape.Rectangle flashRect = new javafx.scene.shape.Rectangle();
    flashRect.setMouseTransparent(true);
    flashRect.setOpacity(0);
    flashRect.widthProperty().bind(editor.widthProperty());
    flashRect.heightProperty().bind(editor.heightProperty());

    StackPane editorWrapper = new StackPane(editor, flashRect);
    editorWrapper.getProperties().put("flashRect", flashRect);

    // ── Args bar (miniAudicle's argument_view) ─────────────────────────────
    javafx.scene.control.TextField argsField = new javafx.scene.control.TextField();
    argsField.setPromptText("script arguments  (space-separated, accessible via me.arg(0)…)");
    argsField.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
    Label argsLabel = new Label(" Args:");
    argsLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
    HBox argsBar = new HBox(4, argsLabel, argsField);
    argsBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    argsBar.setPadding(new Insets(2, 6, 2, 4));
    argsBar.setStyle(
        "-fx-background-color: #f5f5f5; -fx-border-color: #ccc; -fx-border-width: 1 0 0 0;");
    HBox.setHgrow(argsField, Priority.ALWAYS);

    VBox tabContent = new VBox(editorWrapper, argsBar);
    VBox.setVgrow(editorWrapper, Priority.ALWAYS);

    Tab tab = new Tab(title, tabContent);
    tabToWrapper.put(tab, editorWrapper);
    tabToArgs.put(tab, argsField);
    tabSavedText.put(
        tab, content); // baseline = initial content (empty for new, file text for loaded)

    // Mark tab dirty when content changes
    editor
        .plainTextChanges()
        .subscribe(
            ch -> {
              String saved = tabSavedText.get(tab);
              boolean dirty = saved == null || !editor.getText().equals(saved);
              String base = tab.getText().replaceFirst("^\\* ?", "");
              String wanted = dirty ? "* " + base : base;
              if (!tab.getText().equals(wanted)) tab.setText(wanted);
            });

    // Intercept tab close — prompt if dirty
    tab.setOnCloseRequest(
        e -> {
          if (isTabDirty(tab) && !confirmClose(tab)) {
            e.consume(); // cancel close
          } else {
            tabToFile.remove(tab);
            tabToWrapper.remove(tab);
            tabToArgs.remove(tab);
            tabToShredId.remove(tab);
            tabSavedText.remove(tab);
          }
        });

    tabPane.getTabs().add(tab);
    tabPane.getSelectionModel().select(tab);
  }

  private boolean isTabDirty(Tab tab) {
    String saved = tabSavedText.get(tab);
    CodeArea ed = editorFromTab(tab);
    if (ed == null) return false;
    return saved == null || !ed.getText().equals(saved);
  }

  /** Returns true if the user chose to proceed (discard or saved), false to cancel. */
  private boolean confirmClose(Tab tab) {
    String name = tab.getText().replaceFirst("^\\* ?", "");
    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
    alert.setTitle("Unsaved Changes");
    alert.setHeaderText("\"" + name + "\" has unsaved changes.");
    alert.setContentText("Save before closing?");
    ButtonType save = new ButtonType("Save");
    ButtonType discard = new ButtonType("Discard");
    ButtonType cancel = ButtonType.CANCEL;
    alert.getButtonTypes().setAll(save, discard, cancel);
    var result = alert.showAndWait().orElse(cancel);
    if (result == save) {
      saveFile(stage);
      return true;
    }
    return result == discard;
  }

  // ── Kind badge colours (used in cell factory) ─────────────────────────────
  private static final Map<String, String> KIND_COLORS =
      Map.ofEntries(
          Map.entry("osc", "#4ec9b0"),
          Map.entry("fx", "#9cdcfe"),
          Map.entry("filter", "#4fc1ff"),
          Map.entry("stk", "#c3e88d"),
          Map.entry("env", "#ffcb6b"),
          Map.entry("ana", "#f78c6c"),
          Map.entry("io", "#82aaff"),
          Map.entry("ai", "#c792ea"),
          Map.entry("kw", "#c586c0"),
          Map.entry("type", "#4ec9b0"),
          Map.entry("builtin", "#9cdcfe"),
          Map.entry("dur", "#ffcb6b"),
          Map.entry("lit", "#569cd6"),
          Map.entry("var", "#dcdcaa"),
          Map.entry("fun", "#dcdcaa"),
          Map.entry("method", "#dcdcaa"));

  /** Assign a short kind label to a global-scope candidate. */
  private static String kindOf(String name) {
    return switch (name) {
      case "SinOsc",
          "SawOsc",
          "TriOsc",
          "SqrOsc",
          "PulseOsc",
          "Phasor",
          "Noise",
          "Impulse",
          "Step",
          "BlitSaw",
          "BlitSquare",
          "Blit" ->
          "osc";
      case "LPF",
          "HPF",
          "BPF",
          "BRF",
          "ResonZ",
          "BiQuad",
          "OnePole",
          "OneZero",
          "TwoPole",
          "TwoZero",
          "PoleZero" ->
          "filter";
      case "Echo",
          "Delay",
          "DelayL",
          "DelayA",
          "DelayP",
          "Chorus",
          "JCRev",
          "AllPass",
          "Pan2",
          "Gain" ->
          "fx";
      case "ADSR", "Adsr", "Envelope" -> "env";
      case "Mandolin",
          "Clarinet",
          "Plucked",
          "Rhodey",
          "Wurley",
          "BeeThree",
          "HevyMetl",
          "PercFlut",
          "TubeBell",
          "FMVoices",
          "Bowed",
          "StifKarp",
          "Moog",
          "Flute",
          "Sitar",
          "Brass",
          "Saxofony",
          "Shakers",
          "ModalBar",
          "VoicForm" ->
          "stk";
      case "FFT",
          "IFFT",
          "DCT",
          "IDCT",
          "ZCR",
          "RMS",
          "Centroid",
          "MFCC",
          "SFM",
          "Kurtosis",
          "AutoCorr",
          "XCorr",
          "Chroma",
          "FeatureCollector" ->
          "ana";
      case "SndBuf",
          "WvOut",
          "LiSa",
          "MidiIn",
          "MidiMsg",
          "MidiFileIn",
          "OscIn",
          "OscOut",
          "OscMsg",
          "OscEvent",
          "Hid",
          "HidMsg",
          "HidOut",
          "IO",
          "FileIO",
          "StringTokenizer",
          "ConsoleInput",
          "KBHit",
          "SerialIO" ->
          "io";
      case "KNN", "KNN2", "SVM", "MLP", "HMM", "PCA" -> "ai";
      case "second", "ms", "samp", "minute", "hour", "day", "week" -> "dur";
      case "true", "false", "null", "maybe", "pi", "e", "sqrt2" -> "lit";
      case "dac",
          "adc",
          "blackhole",
          "now",
          "Std",
          "Math",
          "Machine",
          "me",
          "chout",
          "cherr",
          "newline" ->
          "builtin";
      case "if",
          "else",
          "while",
          "for",
          "repeat",
          "return",
          "break",
          "continue",
          "new",
          "spork",
          "fun",
          "class",
          "extends",
          "public",
          "private",
          "static",
          "void",
          "abstract",
          "interface",
          "loop",
          "do",
          "until",
          "auto",
          "switch",
          "case",
          "default" ->
          "kw";
      case "int",
          "float",
          "dur",
          "time",
          "string",
          "complex",
          "polar",
          "vec2",
          "vec3",
          "vec4",
          "Event",
          "Object",
          "UGen",
          "UAna" ->
          "type";
      default -> "";
    };
  }

  private void showCompletionPopup(CodeArea editor) {
    if (currentPopup != null) {
      currentPopup.hide();
      currentPopup = null;
    }

    int caretPos = editor.getCaretPosition();
    String textBefore = editor.getText(0, caretPos);

    // Find the start of the current token (letters/digits/underscore/dots/colons)
    int i = textBefore.length() - 1;
    while (i >= 0
        && (Character.isLetterOrDigit(textBefore.charAt(i))
            || textBefore.charAt(i) == '_'
            || textBefore.charAt(i) == '.'
            || textBefore.charAt(i) == ':')) {
      i--;
    }
    String prefix = textBefore.substring(i + 1).trim();

    List<CompItem> candidates;
    String filter;
    String resolvedType = null;

    if (prefix.contains("::")) {
      // Duration completion: 1::[second]
      int colIdx = prefix.lastIndexOf("::");
      filter = prefix.substring(colIdx + 2);
      candidates =
          DURATION_CANDIDATES.stream()
              .map(d -> new CompItem(d, d, "dur"))
              .collect(Collectors.toList());
    } else if (prefix.contains(".")) {
      // Member completion: s.[gain]
      int dotIdx = prefix.lastIndexOf('.');
      String varName = prefix.substring(0, dotIdx);
      filter = prefix.substring(dotIdx + 1);
      resolvedType = resolveVariableType(varName, textBefore);
      candidates = getDynamicMemberCompItems(resolvedType);
    } else {
      // Global context
      candidates = new java.util.ArrayList<>();
      for (String name : TYPE_CANDIDATES) {
        candidates.add(new CompItem(name, name, kindOf(name)));
      }
      // User-declared symbols
      for (UserSymbol sym : scanUserSymbols(editor.getText())) {
        String kind = sym.signature().startsWith("fun ") ? "fun" : "var";
        if (candidates.stream().noneMatch(c -> c.insertText().equals(sym.name()))) {
          candidates.add(new CompItem(sym.name(), sym.name(), kind));
        }
      }
      filter = prefix;
    }

    final String finalFilter = filter.toLowerCase();
    final String finalResolvedType = resolvedType;

    List<CompItem> matches =
        candidates.stream()
            .filter(c -> c.insertText().toLowerCase().startsWith(finalFilter))
            .distinct()
            .sorted(java.util.Comparator.comparing(CompItem::insertText))
            .collect(Collectors.toList());

    if (matches.isEmpty()) return;

    // ── Completion list with kind badges ──────────────────────────────────
    ListView<CompItem> listView = new ListView<>();
    listView.getItems().addAll(matches);
    listView.setPrefWidth(210);
    listView.setPrefHeight(Math.min(matches.size() * 24 + 4, 220));
    listView.setCellFactory(
        lv ->
            new ListCell<>() {
              @Override
              protected void updateItem(CompItem item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setGraphic(null);
                  setText(null);
                  return;
                }
                Label name = new Label(item.insertText());
                name.setStyle("-fx-font-family: monospace; -fx-font-size: 12;");
                HBox cell = new HBox(4, name);
                cell.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                if (!item.kind().isEmpty()) {
                  String color = KIND_COLORS.getOrDefault(item.kind(), "#888");
                  Label badge = new Label(item.kind());
                  badge.setStyle(
                      "-fx-font-size: 9; -fx-text-fill: "
                          + color
                          + ";"
                          + " -fx-padding: 0 3 0 3; -fx-background-radius: 3;"
                          + " -fx-border-color: "
                          + color
                          + "; -fx-border-radius: 3;"
                          + " -fx-border-width: 1;");
                  Region spacer = new Region();
                  HBox.setHgrow(spacer, Priority.ALWAYS);
                  cell.getChildren().addAll(spacer, badge);
                }
                setGraphic(cell);
              }
            });

    // ── Doc panel ──────────────────────────────────────────────────────────
    Label docTitle = new Label();
    docTitle.setWrapText(true);
    docTitle.setStyle(
        "-fx-font-family: monospace; -fx-font-size: 11; -fx-font-weight: bold;"
            + " -fx-text-fill: #4ec9b0;");

    Label docSig = new Label();
    docSig.setWrapText(true);
    docSig.setStyle("-fx-font-family: monospace; -fx-font-size: 10; -fx-text-fill: #9cdcfe;");

    Label docDesc = new Label("Select an item for documentation");
    docDesc.setWrapText(true);
    docDesc.setStyle("-fx-font-size: 11; -fx-text-fill: #d4d4d4;");

    VBox docPanel = new VBox(4, docTitle, docSig, docDesc);
    docPanel.setPrefWidth(260);
    docPanel.setMaxHeight(220);
    docPanel.setStyle(
        "-fx-background-color: #252526; -fx-padding: 8;"
            + " -fx-border-color: #555; -fx-border-width: 0 0 0 1;");

    listView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, sel) -> {
              if (sel == null) return;
              String rawDoc =
                  finalResolvedType != null
                      ? lookupDoc(finalResolvedType, sel.insertText())
                      : lookupDoc(sel.insertText(), null);
              if (rawDoc != null) {
                // Format: first line = title/sig, remaining = description
                String[] lines = rawDoc.split("\n", 2);
                docTitle.setText(lines[0]);
                if (lines.length > 1) {
                  // Check if second part starts with a description or more sig lines
                  String rest = lines[1].trim();
                  int nlIdx = rest.indexOf('\n');
                  if (nlIdx >= 0) {
                    docSig.setText(rest.substring(0, nlIdx).trim());
                    docDesc.setText(rest.substring(nlIdx + 1).trim());
                  } else {
                    docSig.setText("");
                    docDesc.setText(rest);
                  }
                } else {
                  docSig.setText("");
                  docDesc.setText(sel.kind().isEmpty() ? "" : "[" + sel.kind() + "]");
                }
              } else {
                docTitle.setText(sel.insertText());
                docSig.setText(sel.kind().isEmpty() ? "" : "[" + sel.kind() + "]");
                docDesc.setText("");
              }
            });

    HBox popupContent = new HBox(listView, docPanel);
    popupContent.setStyle(
        "-fx-background-color: #1e1e1e; -fx-border-color: #555; -fx-border-width: 1;"
            + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.5), 8, 0, 0, 3);");

    Popup popup = new Popup();
    popup.getContent().add(popupContent);
    popup.setAutoHide(true);
    this.currentPopup = popup;

    listView.setOnKeyPressed(
        ke -> {
          if (ke.getCode() == KeyCode.ENTER || ke.getCode() == KeyCode.TAB) {
            ke.consume();
            CompItem sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
              complete(editor, sel.insertText(), finalFilter);
              popup.hide();
              currentPopup = null;
            }
          } else if (ke.getCode() == KeyCode.ESCAPE) {
            popup.hide();
            currentPopup = null;
          }
        });

    listView.setOnMouseClicked(
        me -> {
          if (me.getClickCount() == 2) {
            CompItem sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
              complete(editor, sel.insertText(), finalFilter);
              popup.hide();
              currentPopup = null;
            }
          }
        });

    popup.setOnHidden(e -> currentPopup = null);

    Optional<Bounds> caretBounds = editor.getCaretBounds();
    caretBounds.ifPresent(b -> popup.show(editor, b.getMaxX(), b.getMaxY()));
    listView.requestFocus();
    listView.getSelectionModel().select(0);
  }

  private void complete(CodeArea editor, String completion, String filter) {
    if (completion == null) return;
    int caret = editor.getCaretPosition();
    editor.replaceText(caret - filter.length(), caret, completion);
  }

  /** Returns CompItems for member completion of a given type, with kind="method". */
  private List<CompItem> getDynamicMemberCompItems(String type) {
    List<String> names = getDynamicMemberCandidates(type);
    Class<?> cls = resolveChuckClass(type);
    return names.stream()
        .map(
            name -> {
              String sig = "";
              if (cls != null) {
                for (java.lang.reflect.Method m : cls.getMethods()) {
                  if (m.getName().equals(name)
                      || m.getName().equals("set" + capitalize(name))
                      || m.getName().equals("get" + capitalize(name))) {
                    sig = formatMethodSig(m, name);
                    break;
                  }
                }
              }
              return new CompItem(name, sig.isEmpty() ? name : sig, "method");
            })
        .collect(Collectors.toList());
  }

  private String resolveVariableType(String varName, String textBefore) {
    // Simple regex lookup for: [Type] varName
    // e.g. SinOsc s => dac;  -> finds SinOsc
    Pattern p = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\s+" + Pattern.quote(varName) + "\\b");
    Matcher m = p.matcher(textBefore);
    String lastFound = "UGen"; // Default
    while (m.find()) {
      lastFound = m.group(1);
    }
    return lastFound;
  }

  private List<String> getDynamicMemberCandidates(String type) {
    Class<?> cls = resolveChuckClass(type);
    if (cls == null) return MEMBER_CANDIDATES;

    Set<String> objectMethods =
        Set.of(
            "hashCode",
            "equals",
            "toString",
            "getClass",
            "notify",
            "notifyAll",
            "wait",
            "clone",
            "finalize");

    java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
    Set<String> setterProps = new java.util.HashSet<>();

    // First pass: setter → property name (setFreq → freq)
    for (java.lang.reflect.Method m : cls.getMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
      String n = m.getName();
      if (n.startsWith("set") && n.length() > 3) {
        String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
        setterProps.add(prop);
        seen.add(prop);
      }
    }

    // Second pass: other public methods (skip Object noise, skip getters covered by setter)
    for (java.lang.reflect.Method m : cls.getMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
      String n = m.getName();
      if (objectMethods.contains(n) || n.startsWith("set")) continue;
      if (n.startsWith("get") && n.length() > 3) {
        String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
        if (setterProps.contains(prop)) continue; // already shown as property
      }
      seen.add(n);
    }

    return new java.util.ArrayList<>(seen);
  }

  /** Resolve a ChucK type name to a Java class, searching known packages. */
  private Class<?> resolveChuckClass(String type) {
    if (type == null || type.isEmpty()) return null;
    String[][] mappings = {
      {type, "org.chuck.audio." + type},
      {type, "org.chuck.audio.osc." + type},
      {type, "org.chuck.audio.fx." + type},
      {type, "org.chuck.audio.filter." + type},
      {type, "org.chuck.audio.stk." + type},
      {type, "org.chuck.audio.util." + type},
      {type, "org.chuck.audio.analysis." + type},
      {type, "org.chuck.core." + type},
      {type, "org.chuck.core.ai." + type},
    };
    // Special renames
    String overrideClass =
        switch (type) {
          case "IO" -> "org.chuck.core.ChuckIO";
          case "Std" -> "org.chuck.core.Std";
          case "Machine" -> "org.chuck.core.Machine";
          case "me" -> "org.chuck.core.ChuckShred";
          default -> null;
        };
    if (overrideClass != null) {
      try {
        return Class.forName(overrideClass);
      } catch (ClassNotFoundException ignored) {
      }
    }
    for (String[] row : mappings) {
      try {
        return Class.forName(row[1]);
      } catch (ClassNotFoundException ignored) {
      }
    }
    return null;
  }

  private CodeArea getCurrentEditor() {
    return editorFromTab(tabPane.getSelectionModel().getSelectedItem());
  }

  private CodeArea editorFromTab(Tab tab) {
    if (tab == null) return null;
    // VBox(editorWrapper StackPane, argsBar HBox) → StackPane → CodeArea
    if (tab.getContent() instanceof VBox vb && !vb.getChildren().isEmpty()) {
      javafx.scene.Node first = vb.getChildren().get(0);
      if (first instanceof StackPane sp
          && !sp.getChildren().isEmpty()
          && sp.getChildren().get(0) instanceof CodeArea area) {
        return area;
      }
    }
    // Fallback: direct StackPane or bare CodeArea (legacy)
    if (tab.getContent() instanceof StackPane sp
        && !sp.getChildren().isEmpty()
        && sp.getChildren().get(0) instanceof CodeArea area) {
      return area;
    }
    if (tab.getContent() instanceof CodeArea area) return area;
    return null;
  }

  private File getCurrentFile() {
    Tab tab = tabPane.getSelectionModel().getSelectedItem();
    return tabToFile.get(tab);
  }

  // ── Menu bar ───────────────────────────────────────────────────────────────

  private MenuBar createMenuBar(Stage stage) {
    MenuBar mb = new MenuBar();

    // File
    Menu fileMenu = new Menu("_File");
    MenuItem newItem = new MenuItem("New");
    newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
    newItem.setOnAction(e -> addNewTab("Untitled.ck", ""));

    MenuItem openItem = new MenuItem("Open…");
    openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
    openItem.setOnAction(e -> openFile(stage));

    MenuItem openProj = new MenuItem("Open Project...");
    openProj.setOnAction(
        e -> {
          javafx.stage.DirectoryChooser dc = new javafx.stage.DirectoryChooser();
          dc.setTitle("Select Project Directory");
          File dir = dc.showDialog(stage);
          if (dir != null) {
            fileBrowser.setRoot(buildTreeItem(dir));
          }
        });

    MenuItem saveItem = new MenuItem("Save");
    saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
    saveItem.setOnAction(e -> saveFile(stage));

    MenuItem saveAsItem = new MenuItem("Save As…");
    saveAsItem.setAccelerator(
        new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
    saveAsItem.setOnAction(e -> saveFileAs(stage));

    MenuItem closeTabItem = new MenuItem("Close Tab");
    closeTabItem.setAccelerator(new KeyCodeCombination(KeyCode.W, KeyCombination.CONTROL_DOWN));
    closeTabItem.setOnAction(
        e -> {
          Tab sel = tabPane.getSelectionModel().getSelectedItem();
          if (sel != null && (!isTabDirty(sel) || confirmClose(sel))) {
            tabPane.getTabs().remove(sel);
            tabToFile.remove(sel);
            tabToWrapper.remove(sel);
            tabToArgs.remove(sel);
            tabToShredId.remove(sel);
            tabSavedText.remove(sel);
          }
        });

    MenuItem exitItem = new MenuItem("Exit");
    exitItem.setOnAction(e -> Platform.exit());

    recentFilesMenu = new Menu("Open _Recent");
    rebuildRecentMenu();

    fileMenu
        .getItems()
        .addAll(
            newItem,
            openItem,
            openProj,
            recentFilesMenu,
            saveItem,
            saveAsItem,
            closeTabItem,
            new SeparatorMenuItem(),
            exitItem);

    // Edit
    Menu editMenu = new Menu("_Edit");
    MenuItem undoItem = new MenuItem("Undo");
    undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
    undoItem.setOnAction(
        e -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) ed.undo();
        });

    MenuItem redoItem = new MenuItem("Redo");
    redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
    redoItem.setOnAction(
        e -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) ed.redo();
        });

    MenuItem cutItem = new MenuItem("Cut");
    cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
    cutItem.setOnAction(
        e -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) ed.cut();
        });

    MenuItem copyItem = new MenuItem("Copy");
    copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
    copyItem.setOnAction(
        e -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) ed.copy();
        });

    MenuItem pasteItem = new MenuItem("Paste");
    pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
    pasteItem.setOnAction(
        e -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) ed.paste();
        });

    MenuItem selAllItem = new MenuItem("Select All");
    selAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
    selAllItem.setOnAction(
        e -> {
          CodeArea ed = getCurrentEditor();
          if (ed != null) ed.selectAll();
        });

    editMenu
        .getItems()
        .addAll(
            undoItem,
            redoItem,
            new SeparatorMenuItem(),
            cutItem,
            copyItem,
            pasteItem,
            new SeparatorMenuItem(),
            selAllItem);

    // Options
    Menu optionsMenu = new Menu("_Options");
    MenuItem prefsItem = new MenuItem("Preferences…");
    prefsItem.setAccelerator(new KeyCodeCombination(KeyCode.COMMA, KeyCombination.CONTROL_DOWN));
    prefsItem.setOnAction(e -> showPreferencesDialog());
    optionsMenu.getItems().add(prefsItem);

    // Examples
    Menu examplesMenu = new Menu("_Examples");
    loadExamples(examplesMenu);

    // Help
    Menu helpMenu = new Menu("_Help");
    MenuItem aboutItem = new MenuItem("About ChucK-Java");
    aboutItem.setOnAction(
        e -> {
          Alert a = new Alert(Alert.AlertType.INFORMATION);
          a.setTitle("About ChucK-Java");
          a.setHeaderText("ChucK-Java — JDK 25 port");
          a.setContentText(
              """
                             A modern port of the ChucK strongly-timed music language to Java 25.

                             Features: Virtual Threads, Vector API, JavaFX IDE with syntax highlighting.

                             Original ChucK: https://chuck.stanford.edu/""");
          a.showAndWait();
        });
    helpMenu.getItems().add(aboutItem);

    Menu vizMenu = new Menu("Visualizer");
    MenuItem fftSize = new MenuItem("FFT Size...");
    fftSize.setOnAction(
        e -> {
          List<Integer> choices = List.of(256, 512, 1024, 2048, 4096);
          ChoiceDialog<Integer> dialog = new ChoiceDialog<>(analyzer.getSize(), choices);
          dialog.setTitle("FFT Size");
          dialog.setHeaderText("Select FFT analysis size");
          dialog.showAndWait().ifPresent(size -> analyzer.setSize(size));
        });
    MenuItem scopeSize = new MenuItem("Oscilloscope Window...");
    scopeSize.setOnAction(
        e -> {
          TextInputDialog dialog = new TextInputDialog(String.valueOf(scope.getWindowSize()));
          dialog.setTitle("Scope Window");
          dialog.setHeaderText("Enter window size in samples");
          dialog
              .showAndWait()
              .ifPresent(
                  s -> {
                    try {
                      scope.setWindowSize(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {
                    }
                  });
        });
    vizMenu.getItems().addAll(fftSize, scopeSize);

    mb.getMenus().addAll(fileMenu, editMenu, optionsMenu, vizMenu, examplesMenu, helpMenu);
    return mb;
  }

  // ── Toolbar ────────────────────────────────────────────────────────────────

  private ToolBar createToolBar() {
    Button addShredBtn = new Button("Add Shred  [Ctrl+Enter]");
    addShredBtn.setStyle("-fx-background-color: #b8f0b8; -fx-font-weight: bold;");
    addShredBtn.setOnAction(e -> addShred());
    this.addShredBtnRef = addShredBtn;

    Button replaceBtn = new Button("Replace  [Ctrl+Shift+Enter]");
    replaceBtn.setOnAction(e -> replaceLastShred());
    this.replaceBtnRef = replaceBtn;

    Button removeLastBtn = new Button("Remove Last  [Ctrl+.]");
    removeLastBtn.setOnAction(e -> removeLastShred());

    Button stopAllBtn = new Button("Stop All  [Ctrl+/]");
    stopAllBtn.setStyle("-fx-background-color: #f0b8b8; -fx-font-weight: bold;");
    stopAllBtn.setOnAction(e -> clearVM());

    Button recordBtn = new Button("Record");
    recordBtn.setOnAction(e -> toggleRecord(recordBtn));

    stallWarningLabel = new Label("⚠ VM Stalled");
    stallWarningLabel.setStyle(
        "-fx-text-fill: white; -fx-background-color: #cc2200;"
            + " -fx-padding: 2 8 2 8; -fx-background-radius: 3; -fx-font-weight: bold;");
    stallWarningLabel.setVisible(false);

    ToolBar tb =
        new ToolBar(
            addShredBtn,
            replaceBtn,
            removeLastBtn,
            stopAllBtn,
            new Separator(),
            recordBtn,
            new Separator(),
            stallWarningLabel);

    // Keyboard shortcuts on the scene — wired after scene is set
    Platform.runLater(
        () -> {
          if (stage.getScene() == null) return;
          stage
              .getScene()
              .getAccelerators()
              .put(
                  new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN),
                  this::addShred);
          stage
              .getScene()
              .getAccelerators()
              .put(
                  new KeyCodeCombination(
                      KeyCode.ENTER, KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                  this::replaceLastShred);
          stage
              .getScene()
              .getAccelerators()
              .put(
                  new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.CONTROL_DOWN),
                  this::removeLastShred);
          stage
              .getScene()
              .getAccelerators()
              .put(
                  new KeyCodeCombination(KeyCode.SLASH, KeyCombination.CONTROL_DOWN),
                  this::clearVM);
        });

    return tb;
  }

  // ── File browser ───────────────────────────────────────────────────────────

  private TreeView<File> createFileBrowser() {
    File rootDir = new File(".");
    TreeItem<File> rootItem = buildTreeItem(rootDir);
    fileBrowser = new TreeView<>(rootItem);
    fileBrowser.setShowRoot(false);
    fileBrowser.setCellFactory(
        tv ->
            new TreeCell<>() {
              @Override
              protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                  setText(null);
                  setContextMenu(null);
                } else {
                  setText(item.getName());
                  ContextMenu cm = new ContextMenu();
                  MenuItem refresh = new MenuItem("Refresh");
                  refresh.setOnAction(e -> refreshFileBrowser());
                  MenuItem delete = new MenuItem("Delete");
                  delete.setOnAction(e -> deleteFile(item));
                  MenuItem newFile = new MenuItem("New File...");
                  newFile.setOnAction(
                      e -> createNewFile(item.isDirectory() ? item : item.getParentFile()));
                  cm.getItems().addAll(refresh, newFile, new SeparatorMenuItem(), delete);
                  setContextMenu(cm);
                }
              }
            });
    fileBrowser.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2) {
            TreeItem<File> sel = fileBrowser.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue().isFile()) loadFileIntoEditor(sel.getValue());
          }
        });
    return fileBrowser;
  }

  private void refreshFileBrowser() {
    TreeItem<File> root = fileBrowser.getRoot();
    if (root != null) {
      File rootDir = root.getValue();
      fileBrowser.setRoot(buildTreeItem(rootDir));
    }
  }

  private void deleteFile(File f) {
    Alert alert =
        new Alert(
            Alert.AlertType.CONFIRMATION,
            "Delete " + f.getName() + "?",
            ButtonType.YES,
            ButtonType.NO);
    alert
        .showAndWait()
        .ifPresent(
            response -> {
              if (response == ButtonType.YES) {
                if (f.delete()) refreshFileBrowser();
              }
            });
  }

  private void createNewFile(File dir) {
    TextInputDialog dialog = new TextInputDialog("untitled.ck");
    dialog.setHeaderText("Create New File in " + dir.getName());
    dialog
        .showAndWait()
        .ifPresent(
            name -> {
              try {
                File f = new File(dir, name);
                if (f.createNewFile()) refreshFileBrowser();
              } catch (IOException e) {
                print("Error creating file: " + e.getMessage());
              }
            });
  }

  private TreeItem<File> buildTreeItem(File file) {
    TreeItem<File> item = new TreeItem<>(file);
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        for (File child : children) {
          if (child.getName().startsWith(".") || child.getName().equals("target")) continue;
          item.getChildren().add(buildTreeItem(child));
        }
      }
    }
    return item;
  }

  private void loadExamples(Menu menu) {
    // Try local filesystem first
    File dir = new File("../examples");
    if (!dir.exists()) dir = new File("examples");

    if (dir.exists() && dir.isDirectory()) {
      addExampleSubmenu(menu, dir);
    } else {
      // Fallback to bundled examples in JAR
      loadExamplesFromClasspath(menu);
    }
  }

  private void loadExamplesFromClasspath(Menu menu) {
    // Since we can't easily list resources in a JAR without extra libraries,
    // we'll provide a hardcoded list of common categories if running from JAR
    // and try to load them via getResource
    String[] categories = {"basic", "analysis", "effects", "stk", "osc", "midi"};
    for (String cat : categories) {
      Menu sub = new Menu(cat);
      // This is a simplified fallback for the bundled case
      // In a real app, we'd use a ClassGraph or similar to scan the JAR
      menu.getItems().add(sub);
    }
    if (menu.getItems().isEmpty()) {
      menu.getItems().add(new MenuItem("(no examples found)"));
    }
  }

  private void addExampleSubmenu(Menu menu, File dir) {
    File[] files = dir.listFiles();
    if (files == null) return;

    java.util.Arrays.sort(files);

    List<MenuItem> allItems = new java.util.ArrayList<>();

    for (File f : files) {
      if (f.isDirectory()) {
        Menu sub = new Menu(f.getName());
        addExampleSubmenu(sub, f);
        allItems.add(sub);
      } else if (f.getName().endsWith(".ck")) {
        MenuItem mi = new MenuItem(f.getName());
        mi.setOnAction(e -> loadFileIntoEditor(f));
        allItems.add(mi);
      }
    }

    if (allItems.size() <= 25) {
      menu.getItems().addAll(allItems);
    } else {
      for (int i = 0; i < allItems.size(); i += 20) {
        int end = Math.min(i + 20, allItems.size());
        String label =
            allItems.get(i).getText().substring(0, Math.min(5, allItems.get(i).getText().length()))
                + "...";
        Menu chunkMenu = new Menu("Group " + (i / 20 + 1) + " (" + label + ")");
        chunkMenu.getItems().addAll(allItems.subList(i, end));
        menu.getItems().add(chunkMenu);
      }
    }
  }

  // ── Shred management ───────────────────────────────────────────────────────

  private void addShred() {
    if (lockdownMode) {
      print("[IDE] VM stalled — lockdown active. Stop all shreds first.");
      return;
    }
    CodeArea editor = getCurrentEditor();
    if (editor == null) return;

    Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
    File currentFile = getCurrentFile();
    String path = currentFile != null ? currentFile.getName() : "Untitled.ck";
    statusLabel.setText("  Compiling…");

    // Parse args field for this tab
    String[] scriptArgs = new String[0];
    javafx.scene.control.TextField argsField = tabToArgs.get(currentTab);
    if (argsField != null && !argsField.getText().isBlank()) {
      scriptArgs = argsField.getText().trim().split("\\s+");
    }
    final String[] finalArgs = scriptArgs;

    try {
      if (path.endsWith(".java")) {
        if (currentFile == null) {
          throw new RuntimeException("Please save the .java file before sporking.");
        }
        // Save first to ensure the compiler sees the latest changes
        Files.writeString(currentFile.toPath(), editor.getText());

        Runnable task = ChuckDSL.load(currentFile.toPath());
        int id = vm.spork(task);

        ChuckShred shredObj = vm.getShred(id);
        if (shredObj != null) {
          shredObj.setName(currentFile.getName());
          shredObj.setArgs(finalArgs);
        }
        ShredInfo info = new ShredInfo(id, currentFile.getName(), shredObj);
        shredListView.getItems().add(info);
        tabToShredId.put(currentTab, id);
        flashEditor(currentTab, javafx.scene.paint.Color.LIMEGREEN);
        print("Sporked Java Shred " + id + " (" + currentFile.getName() + ")");
        updateStatus();
        return;
      }

      // --- Existing ChucK compilation logic ---
      String source = editor.getText();
      org.antlr.v4.runtime.CharStream input = org.antlr.v4.runtime.CharStreams.fromString(source);
      org.chuck.compiler.ChuckANTLRLexer lexer = new org.chuck.compiler.ChuckANTLRLexer(input);
      org.antlr.v4.runtime.CommonTokenStream tokens =
          new org.antlr.v4.runtime.CommonTokenStream(lexer);
      org.chuck.compiler.ChuckANTLRParser parser = new org.chuck.compiler.ChuckANTLRParser(tokens);
      org.chuck.compiler.ChuckASTVisitor visitor = new org.chuck.compiler.ChuckASTVisitor();
      @SuppressWarnings("unchecked")
      List<org.chuck.compiler.ChuckAST.Stmt> ast =
          (List<org.chuck.compiler.ChuckAST.Stmt>) visitor.visit(parser.program());

      org.chuck.compiler.ChuckEmitter emitter =
          new org.chuck.compiler.ChuckEmitter(vm.getUserClassRegistry());
      ChuckCode code = emitter.emit(ast, path);

      ChuckShred shred = new ChuckShred(code);
      shred.setArgs(finalArgs);
      vm.spork(shred);

      String name = currentFile != null ? currentFile.getName() : "User";
      ShredInfo info = new ShredInfo(shred.getId(), name, shred);
      shredListView.getItems().add(info);
      tabToShredId.put(currentTab, shred.getId());
      flashEditor(currentTab, javafx.scene.paint.Color.LIMEGREEN);
      print("Sporked Shred " + shred.getId() + " (" + name + ")");
      updateStatus();

    } catch (org.chuck.core.ChuckCompilerException ex) {
      print("Compilation Error: " + ex.getMessage());
      highlightErrorLine(ex);
      flashEditor(currentTab, javafx.scene.paint.Color.RED);
      statusLabel.setText("  Compilation failed");
    } catch (Exception ex) {
      String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
      print("Compilation Error: " + msg);
      highlightErrorLine(msg);
      flashEditor(currentTab, javafx.scene.paint.Color.RED);
      statusLabel.setText("  Compilation failed");
    }
  }

  private void replaceLastShred() {
    if (lockdownMode) {
      print("[IDE] VM stalled — lockdown active. Stop all shreds first.");
      return;
    }
    Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
    // Prefer the shred that was sporked from this tab; fall back to the last in the list
    Integer tabShredId = tabToShredId.get(currentTab);
    ShredInfo toRemove = null;
    if (tabShredId != null) {
      for (ShredInfo si : shredListView.getItems()) {
        if (si.id == tabShredId) {
          toRemove = si;
          break;
        }
      }
    }
    if (toRemove == null && !shredListView.getItems().isEmpty()) {
      toRemove = shredListView.getItems().get(shredListView.getItems().size() - 1);
    }
    if (toRemove != null) {
      vm.removeShred(toRemove.id);
      shredListView.getItems().remove(toRemove);
      tabToShredId.remove(currentTab);
      print("Replacing Shred " + toRemove.id);
    }
    // addShred will flash green; pre-flash orange to indicate replace
    flashEditor(currentTab, javafx.scene.paint.Color.ORANGE);
    addShred();
  }

  private void removeLastShred() {
    Tab currentTab = tabPane.getSelectionModel().getSelectedItem();
    // Prefer the shred from this tab
    Integer tabShredId = tabToShredId.get(currentTab);
    ShredInfo toRemove = null;
    if (tabShredId != null) {
      for (ShredInfo si : shredListView.getItems()) {
        if (si.id == tabShredId) {
          toRemove = si;
          break;
        }
      }
    }
    if (toRemove == null && !shredListView.getItems().isEmpty()) {
      toRemove = shredListView.getItems().get(shredListView.getItems().size() - 1);
    }
    if (toRemove != null) {
      vm.removeShred(toRemove.id);
      shredListView.getItems().remove(toRemove);
      tabToShredId.remove(currentTab);
      flashEditor(currentTab, javafx.scene.paint.Color.GRAY);
      print("Removed Shred " + toRemove.id);
      updateStatus();
    }
  }

  private void removeSelectedShred() {
    ShredInfo sel = shredListView.getSelectionModel().getSelectedItem();
    if (sel != null) {
      vm.removeShred(sel.id);
      shredListView.getItems().remove(sel);
      print("Stopped Shred " + sel.id);
      updateStatus();
    }
  }

  private void clearVM() {
    print("Stopping all shreds…");
    vm.clear();
    shredListView.getItems().clear();
    tabToShredId.clear();
    exitLockdown();
    statusLabel.setText("  VM cleared");
  }

  private void updateStatus() {
    int n = shredListView.getItems().size();
    statusLabel.setText("  " + (n == 0 ? "Ready" : "Running " + n + " shred" + (n > 1 ? "s" : "")));
  }

  private void startVisualizer() {
    visTimer =
        new javafx.animation.AnimationTimer() {
          @Override
          public void handle(long now) {
            renderSpectrum();
            renderScope();
            updateVMTime();
            updateShredList();
          }
        };
    visTimer.start();
  }

  private void updateShredList() {
    List<ShredInfo> toRemove = new java.util.ArrayList<>();
    for (ShredInfo si : shredListView.getItems()) {
      if (si.shred == null || si.shred.isDone()) {
        toRemove.add(si);
      } else {
        si.updateDuration();
      }
    }
    if (!toRemove.isEmpty()) {
      shredListView.getItems().removeAll(toRemove);
      updateStatus();
    }
    // Removed shredListView.refresh() to keep buttons responsive
  }

  // ── Stall / lockdown ───────────────────────────────────────────────────────

  private void enterLockdown() {
    if (lockdownMode) return;
    lockdownMode = true;
    if (addShredBtnRef != null) addShredBtnRef.setDisable(true);
    if (replaceBtnRef != null) replaceBtnRef.setDisable(true);
    if (stallWarningLabel != null) {
      stallWarningLabel.setText("⚠ VM Stalled");
      stallWarningLabel.setVisible(true);
    }
    statusLabel.setText("  ⚠ VM stalled — lockdown");
  }

  private void exitLockdown() {
    lockdownMode = false;
    stallFrameCount = 0;
    lastVmTimeSample = -1;
    if (addShredBtnRef != null) addShredBtnRef.setDisable(false);
    if (replaceBtnRef != null) replaceBtnRef.setDisable(false);
    if (stallWarningLabel != null) stallWarningLabel.setVisible(false);
  }

  private void updateVMTime() {
    long nowSample = vm.getCurrentTime();
    double seconds = nowSample / (double) vm.getSampleRate();
    vmTimeLabel.setText(String.format("Time: %.3fs", seconds));

    // Stall detection: if the VM hasn't advanced since the last frame, increment stall counter.
    // The animation timer fires at ~60 fps; we sample every ~3 frames → ~20 Hz, matching
    // miniAudicle's VMMONITOR_REFRESH_RATE. A 2-second stall triggers lockdown.
    boolean shredsRunning = !shredListView.getItems().isEmpty();
    if (shredsRunning) {
      if (nowSample == lastVmTimeSample) {
        if (++stallFrameCount >= STALL_FRAMES_LOCKDOWN) {
          enterLockdown();
        }
      } else {
        stallFrameCount = 0;
        if (lockdownMode) exitLockdown();
      }
    } else {
      stallFrameCount = 0;
      if (lockdownMode) exitLockdown();
    }
    lastVmTimeSample = nowSample;

    // VU meters: read peak-with-decay from audio engine
    if (vuLeft != null && vuRight != null && audio != null) {
      double peak0 = Math.min(1.0, audio.getPeakOut(0));
      double peak1 = Math.min(1.0, audio.getPeakOut(1));
      vuLeft.setProgress(peak0);
      vuRight.setProgress(peak1);
      // Colour: green < -6 dBFS, yellow < -3 dBFS, red ≥ -3 dBFS
      String leftStyle =
          peak0 > 0.707
              ? "-fx-accent: red;"
              : peak0 > 0.5 ? "-fx-accent: gold;" : "-fx-accent: limegreen;";
      String rightStyle =
          peak1 > 0.707
              ? "-fx-accent: red;"
              : peak1 > 0.5 ? "-fx-accent: gold;" : "-fx-accent: limegreen;";
      if (!leftStyle.equals(vuLeft.getStyle())) vuLeft.setStyle(leftStyle);
      if (!rightStyle.equals(vuRight.getStyle())) vuRight.setStyle(rightStyle);
    }
  }

  private void renderSpectrum() {
    GraphicsContext gc = visualizerCanvas.getGraphicsContext2D();
    double w = visualizerCanvas.getWidth();
    double h = visualizerCanvas.getHeight();
    if (w <= 0 || h <= 0) return;

    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, w, h);

    UAnaBlob blob = analyzer.upchuck();
    float[] mags = blob.getFvals();
    if (mags == null || mags.length == 0) return;

    gc.setStroke(Color.LIME);
    gc.setLineWidth(1.5);
    double binW = w / mags.length;

    // Normalize by FFT size then map to dB: 0 dB at top, -80 dB at bottom
    double norm = 2.0 / mags.length;
    gc.beginPath();
    for (int i = 0; i < mags.length; i++) {
      double x = i * binW;
      double linear = mags[i] * norm;
      double db = linear > 1e-9 ? 20.0 * Math.log10(linear) : -80.0;
      double val = Math.max(0.0, Math.min(1.0, (db + 80.0) / 80.0));
      double y = h - (val * h);
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.stroke();
  }

  private void renderScope() {
    GraphicsContext gc = scopeCanvas.getGraphicsContext2D();
    double w = scopeCanvas.getWidth();
    double h = scopeCanvas.getHeight();
    if (w <= 0 || h <= 0) return;

    gc.setFill(Color.BLACK);
    gc.fillRect(0, 0, w, h);

    UAnaBlob blob = scope.upchuck();
    if (blob == null) return;
    float[] samples = blob.getFvals();
    if (samples == null || samples.length == 0) return;

    gc.setStroke(Color.CYAN);
    gc.setLineWidth(1.5);
    double step = w / samples.length;
    double midY = h / 2.0;

    gc.beginPath();
    for (int i = 0; i < samples.length; i++) {
      double x = i * step;
      double y = midY - (samples[i] * midY * 0.9);
      if (i == 0) gc.moveTo(x, y);
      else gc.lineTo(x, y);
    }
    gc.stroke();
  }

  private void toggleRecord(Button btn) {
    try {
      if (audio.isRecording()) {
        audio.stopRecording();
        btn.setText("Record");
        btn.setStyle("");
        print("Recording saved to session.wav");
      } else {
        audio.startRecording("session.wav");
        btn.setText("Stop Recording");
        btn.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
        print("Recording started → session.wav");
      }
    } catch (IOException ex) {
      print("Recording error: " + ex.getMessage());
    }
  }

  // ── Smart indentation (mirrors miniAudicle's enable_smart_indentation logic) ──

  /**
   * Called on Enter: copies the indentation of the current line and adds one extra level if the
   * line ends with '{'. On a line that is only whitespace followed by '}', reduces by one level.
   */
  private void applySmartIndent(CodeArea editor) {
    int caretPos = editor.getCaretPosition();
    int paraIdx = editor.getCurrentParagraph();
    String currentLine = editor.getParagraph(paraIdx).getText();

    // Leading whitespace of the current line
    int wsEnd = 0;
    while (wsEnd < currentLine.length()
        && (currentLine.charAt(wsEnd) == ' ' || currentLine.charAt(wsEnd) == '\t')) {
      wsEnd++;
    }
    String indent = currentLine.substring(0, wsEnd);

    // If the trimmed text before the caret ends with '{', add one indent level
    String trimmedBefore =
        currentLine.substring(0, caretPos - editor.position(paraIdx, 0).toOffset()).stripTrailing();
    String oneLevel = prefUseSpaces ? " ".repeat(prefTabWidth) : "\t";
    if (!trimmedBefore.isEmpty() && trimmedBefore.charAt(trimmedBefore.length() - 1) == '{') {
      indent += oneLevel;
    }

    editor.insertText(caretPos, "\n" + indent);
  }

  // ── OTF Visual feedback (flash) ────────────────────────────────────────────

  /**
   * Flashes the editor background with {@code color} for ~400 ms, fading out — matching
   * miniAudicle's animateAdd/animateError visual cues.
   */
  private void flashEditor(Tab tab, javafx.scene.paint.Color color) {
    StackPane wrapper = tabToWrapper.get(tab);
    if (wrapper == null) return;
    Object obj = wrapper.getProperties().get("flashRect");
    if (!(obj instanceof javafx.scene.shape.Rectangle rect)) return;
    rect.setFill(color);
    javafx.animation.FadeTransition ft =
        new javafx.animation.FadeTransition(javafx.util.Duration.millis(400), rect);
    ft.setFromValue(0.35);
    ft.setToValue(0.0);
    ft.play();
  }

  // ── Error line highlighting ─────────────────────────────────────────────────

  private void highlightErrorLine(org.chuck.core.ChuckCompilerException e) {
    CodeArea editor = getCurrentEditor();
    if (editor == null) return;
    int lineNum = e.getLine() - 1;
    int col = e.getColumn();
    if (lineNum < 0 || lineNum >= editor.getParagraphs().size()) return;
    editor.showParagraphAtTop(Math.max(0, lineNum - 3));
    int lineStart = editor.position(lineNum, 0).toOffset();
    int errorOffset =
        editor.position(lineNum, Math.min(col, editor.getParagraph(lineNum).length())).toOffset();

    // Select the whole line or just the point? ChucK usually selects the line.
    int lineEnd = editor.position(lineNum, editor.getParagraph(lineNum).length()).toOffset();
    editor.selectRange(lineStart, lineEnd);
    editor.requestFocus();
  }

  private void highlightErrorLine(String msg) {
    CodeArea editor = getCurrentEditor();
    if (editor == null) return;
    java.util.regex.Matcher m =
        java.util.regex.Pattern.compile(
                "(?:at|line)\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(msg);
    if (!m.find()) return;
    int lineNum = Integer.parseInt(m.group(1)) - 1;
    if (lineNum < 0 || lineNum >= editor.getParagraphs().size()) return;
    editor.showParagraphAtTop(Math.max(0, lineNum - 3));
    int lineStart = editor.position(lineNum, 0).toOffset();
    int lineEnd = editor.position(lineNum, editor.getParagraph(lineNum).length()).toOffset();
    editor.selectRange(lineStart, lineEnd);
  }

  // ── File I/O ───────────────────────────────────────────────────────────────

  private void loadFileIntoEditor(File file) {
    try {
      String text = Files.readString(file.toPath());
      addNewTab(file.getName(), text);
      Tab tab = tabPane.getSelectionModel().getSelectedItem();
      tabToFile.put(tab, file);
      tabSavedText.put(tab, text); // mark clean immediately after load
      print("Loaded: " + file.getAbsolutePath());
      addToRecentFiles(file);
    } catch (IOException ex) {
      print("Error loading file: " + ex.getMessage());
    }
  }

  private void openFile(Stage stage) {
    FileChooser fc = new FileChooser();
    fc.getExtensionFilters()
        .addAll(
            new FileChooser.ExtensionFilter("ChucK and Java Files", "*.ck", "*.java"),
            new FileChooser.ExtensionFilter("ChucK Files (*.ck)", "*.ck"),
            new FileChooser.ExtensionFilter("Java Files (*.java)", "*.java"));
    File file = fc.showOpenDialog(stage);
    if (file != null) loadFileIntoEditor(file);
  }

  private void saveFile(Stage stage) {
    Tab tab = tabPane.getSelectionModel().getSelectedItem();
    File file = tabToFile.get(tab);
    if (file == null) {
      saveFileAs(stage);
    } else {
      try {
        String text = getCurrentEditor().getText();
        Files.writeString(file.toPath(), text);
        tabSavedText.put(tab, text);
        tab.setText(file.getName()); // remove dirty asterisk
        print("Saved: " + file.getAbsolutePath());
      } catch (IOException ex) {
        print("Error saving: " + ex.getMessage());
      }
    }
  }

  private void saveFileAs(Stage stage) {
    FileChooser fc = new FileChooser();
    fc.getExtensionFilters()
        .addAll(
            new FileChooser.ExtensionFilter("ChucK and Java Files", "*.ck", "*.java"),
            new FileChooser.ExtensionFilter("ChucK Files (*.ck)", "*.ck"),
            new FileChooser.ExtensionFilter("Java Files (*.java)", "*.java"));
    File file = fc.showSaveDialog(stage);
    if (file != null) {
      try {
        String text = getCurrentEditor().getText();
        Files.writeString(file.toPath(), text);
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        tab.setText(file.getName());
        tabToFile.put(tab, file);
        tabSavedText.put(tab, text);
        print("Saved: " + file.getAbsolutePath());
        addToRecentFiles(file);
      } catch (IOException ex) {
        print("Error saving: " + ex.getMessage());
      }
    }
  }

  // ── Helpers ────────────────────────────────────────────────────────────────

  private void print(String text) {
    Platform.runLater(() -> outputArea.appendText(text + "\n"));
  }

  private void clearConsole() {
    outputArea.clear();
  }

  // ── Preferences dialog (mirrors miniAudicle's preferences panel) ───────────

  private void showPreferencesDialog() {
    javafx.scene.control.Dialog<ButtonType> dialog = new javafx.scene.control.Dialog<>();
    dialog.setTitle("Preferences");
    dialog.setHeaderText(null);
    dialog.setResultConverter(bt -> bt);
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

    // ── Editor tab ─────────────────────────────────────────────────────────
    javafx.scene.control.Spinner<Integer> fontSizeSpinner =
        new javafx.scene.control.Spinner<>(8, 32, prefFontSize);
    fontSizeSpinner.setEditable(true);
    fontSizeSpinner.setPrefWidth(80);

    javafx.scene.control.Spinner<Integer> tabWidthSpinner =
        new javafx.scene.control.Spinner<>(1, 16, prefTabWidth);
    tabWidthSpinner.setEditable(true);
    tabWidthSpinner.setPrefWidth(80);

    javafx.scene.control.CheckBox useSpacesCb =
        new javafx.scene.control.CheckBox("Use spaces (uncheck for tabs)");
    useSpacesCb.setSelected(prefUseSpaces);

    javafx.scene.control.CheckBox smartIndentCb =
        new javafx.scene.control.CheckBox("Enable smart indentation");
    smartIndentCb.setSelected(prefSmartIndent);

    javafx.scene.layout.GridPane editorGrid = new javafx.scene.layout.GridPane();
    editorGrid.setHgap(12);
    editorGrid.setVgap(10);
    editorGrid.setPadding(new Insets(12));
    editorGrid.add(new Label("Font size:"), 0, 0);
    editorGrid.add(fontSizeSpinner, 1, 0);
    editorGrid.add(new Label("Indent width:"), 0, 1);
    editorGrid.add(tabWidthSpinner, 1, 1);
    editorGrid.add(useSpacesCb, 0, 2, 2, 1);
    editorGrid.add(smartIndentCb, 0, 3, 2, 1);

    Tab editorTab = new Tab("Editor", editorGrid);
    editorTab.setClosable(false);

    // ── Audio tab ──────────────────────────────────────────────────────────
    javafx.scene.control.ChoiceBox<Integer> srBox =
        new javafx.scene.control.ChoiceBox<>(
            javafx.collections.FXCollections.observableArrayList(
                22050, 44100, 48000, 88200, 96000));
    srBox.setValue(prefSampleRate);

    javafx.scene.control.ChoiceBox<Integer> bufBox =
        new javafx.scene.control.ChoiceBox<>(
            javafx.collections.FXCollections.observableArrayList(128, 256, 512, 1024, 2048));
    bufBox.setValue(prefBufferSize);

    // Device lists (probed at dialog open time)
    java.util.List<String> outDevices = ChuckAudio.getOutputDeviceNames();
    java.util.List<String> inDevices = ChuckAudio.getInputDeviceNames();

    javafx.scene.control.ChoiceBox<String> outDevBox =
        new javafx.scene.control.ChoiceBox<>(
            javafx.collections.FXCollections.observableArrayList(outDevices));
    outDevBox.setValue(outDevices.contains(prefOutputDevice) ? prefOutputDevice : "");

    javafx.scene.control.ChoiceBox<String> inDevBox =
        new javafx.scene.control.ChoiceBox<>(
            javafx.collections.FXCollections.observableArrayList(inDevices));
    inDevBox.setValue(inDevices.contains(prefInputDevice) ? prefInputDevice : "");

    Label restartNote = new Label("⚠ Changes take effect after restarting the IDE.");
    restartNote.setStyle("-fx-text-fill: #884400; -fx-font-style: italic;");

    javafx.scene.layout.GridPane audioGrid = new javafx.scene.layout.GridPane();
    audioGrid.setHgap(12);
    audioGrid.setVgap(10);
    audioGrid.setPadding(new Insets(12));
    audioGrid.add(new Label("Sample rate (Hz):"), 0, 0);
    audioGrid.add(srBox, 1, 0);
    audioGrid.add(new Label("Buffer size (samples):"), 0, 1);
    audioGrid.add(bufBox, 1, 1);
    audioGrid.add(new Label("Output device:"), 0, 2);
    audioGrid.add(outDevBox, 1, 2);
    audioGrid.add(new Label("Input device:"), 0, 3);
    audioGrid.add(inDevBox, 1, 3);
    audioGrid.add(restartNote, 0, 4, 2, 1);

    Tab audioTab = new Tab("Audio", audioGrid);
    audioTab.setClosable(false);

    // ── About tab ─────────────────────────────────────────────────────────
    Label aboutLbl =
        new Label(
            "ChucK-Java  —  JDK 25 port\n\nOriginal ChucK: https://chuck.stanford.edu/\nminiAudicle IDE: https://audicle.cs.princeton.edu/mini/");
    aboutLbl.setWrapText(true);
    VBox aboutBox = new VBox(aboutLbl);
    aboutBox.setPadding(new Insets(12));
    Tab aboutTab = new Tab("About", aboutBox);
    aboutTab.setClosable(false);

    TabPane prefTabs = new TabPane(editorTab, audioTab, aboutTab);
    prefTabs.setPrefWidth(400);
    prefTabs.setPrefHeight(220);
    dialog.getDialogPane().setContent(prefTabs);

    dialog
        .showAndWait()
        .filter(bt -> bt == ButtonType.OK)
        .ifPresent(
            result -> {
              // Apply and persist
              prefFontSize = fontSizeSpinner.getValue();
              prefTabWidth = tabWidthSpinner.getValue();
              prefUseSpaces = useSpacesCb.isSelected();
              prefSmartIndent = smartIndentCb.isSelected();

              prefs.putInt("editor.fontSize", prefFontSize);
              prefs.putInt("editor.tabWidth", prefTabWidth);
              prefs.putBoolean("editor.useSpaces", prefUseSpaces);
              prefs.putBoolean("editor.smartIndent", prefSmartIndent);

              // Audio — persist for next launch
              prefSampleRate = srBox.getValue();
              prefBufferSize = bufBox.getValue();
              prefOutputDevice = outDevBox.getValue() != null ? outDevBox.getValue() : "";
              prefInputDevice = inDevBox.getValue() != null ? inDevBox.getValue() : "";
              prefs.putInt("audio.sampleRate", prefSampleRate);
              prefs.putInt("audio.bufferSize", prefBufferSize);
              prefs.put("audio.outputDevice", prefOutputDevice);
              prefs.put("audio.inputDevice", prefInputDevice);

              // Apply font size to all open editors immediately
              String fontStyle =
                  "-fx-font-family: 'Monospaced'; -fx-font-size: " + prefFontSize + ";";
              for (Tab t : tabPane.getTabs()) {
                CodeArea ed = editorFromTab(t);
                if (ed != null) ed.setStyle(fontStyle);
              }
            });
  }

  @Override
  public void stop() {
    if (audio != null) audio.stop();
  }

  public static void main(String[] args) {
    launch(args);
  }

  // ── Recent files ───────────────────────────────────────────────────────────

  private void addToRecentFiles(File f) {
    String path = f.getAbsolutePath();
    List<String> recent = new java.util.ArrayList<>(getRecentFilePaths());
    recent.remove(path);
    recent.add(0, path);
    if (recent.size() > MAX_RECENT) recent = recent.subList(0, MAX_RECENT);
    for (int j = 0; j < MAX_RECENT; j++) {
      if (j < recent.size()) prefs.put("recent." + j, recent.get(j));
      else prefs.remove("recent." + j);
    }
    rebuildRecentMenu();
  }

  private List<String> getRecentFilePaths() {
    List<String> paths = new java.util.ArrayList<>();
    for (int j = 0; j < MAX_RECENT; j++) {
      String p = prefs.get("recent." + j, null);
      if (p != null) paths.add(p);
    }
    return paths;
  }

  private void rebuildRecentMenu() {
    if (recentFilesMenu == null) return;
    recentFilesMenu.getItems().clear();
    List<String> paths = getRecentFilePaths();
    if (paths.isEmpty()) {
      MenuItem empty = new MenuItem("(no recent files)");
      empty.setDisable(true);
      recentFilesMenu.getItems().add(empty);
      return;
    }
    for (String p : paths) {
      File f = new File(p);
      String label = f.getName() + "   \u2014   " + p; // em dash separator
      MenuItem mi = new MenuItem(label);
      mi.setDisable(!f.exists());
      mi.setOnAction(
          e -> {
            if (f.exists()) loadFileIntoEditor(f);
          });
      recentFilesMenu.getItems().add(mi);
    }
    recentFilesMenu.getItems().add(new SeparatorMenuItem());
    MenuItem clear = new MenuItem("Clear Recent");
    clear.setOnAction(
        e -> {
          for (int j = 0; j < MAX_RECENT; j++) prefs.remove("recent." + j);
          rebuildRecentMenu();
        });
    recentFilesMenu.getItems().add(clear);
  }

  // ── Doc lookup ─────────────────────────────────────────────────────────────

  /**
   * Look up documentation for a ChucK type and optional member. Returns null if not found. Falls
   * back to a formatted method signature when @doc is absent.
   */
  private String lookupDoc(String typeName, String memberName) {
    if (typeName == null || typeName.isEmpty()) return null;
    Class<?> cls = resolveChuckClass(typeName);
    if (cls == null) return null;

    if (memberName == null || memberName.isEmpty()) {
      doc classDoc = cls.getAnnotation(doc.class);
      if (classDoc != null) return typeName + "\n" + classDoc.value();
      String parent = cls.getSuperclass() != null ? cls.getSuperclass().getSimpleName() : "Object";
      return typeName + " (" + parent + ")";
    }

    // Find matching method: exact name or set/get prefix
    java.lang.reflect.Method best = null;
    for (java.lang.reflect.Method m : cls.getMethods()) {
      String n = m.getName();
      if (n.equals(memberName)
          || n.equals("set" + capitalize(memberName))
          || n.equals("get" + capitalize(memberName))) {
        if (best == null || n.equals(memberName)) best = m;
      }
    }
    if (best == null) return null;

    doc memberDoc = best.getAnnotation(doc.class);
    String sig = formatMethodSig(best, memberName);
    return memberDoc != null ? sig + "\n" + memberDoc.value() : sig;
  }

  private String capitalize(String s) {
    return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  private String formatMethodSig(java.lang.reflect.Method m, String alias) {
    StringBuilder sb = new StringBuilder(alias != null ? alias : m.getName());
    sb.append("(");
    Class<?>[] params = m.getParameterTypes();
    for (int j = 0; j < params.length; j++) {
      if (j > 0) sb.append(", ");
      sb.append(params[j].getSimpleName());
    }
    sb.append(")");
    String ret = m.getReturnType().getSimpleName();
    if (!"void".equals(ret)) sb.append(" \u2192 ").append(ret); // →
    return sb.toString();
  }

  // ── Hover doc popup ────────────────────────────────────────────────────────

  private void showDocHoverPopup(String text, javafx.geometry.Point2D pos) {
    hideDocHoverPopup();
    // Split into signature line and description
    String[] parts = text.split("\n", 2);
    VBox box = new VBox(3);
    box.setStyle(
        "-fx-background-color: #252526; -fx-border-color: #555;"
            + " -fx-border-radius: 4; -fx-background-radius: 4;"
            + " -fx-padding: 6 10 6 10;");
    box.setMaxWidth(360);
    Label title = new Label(parts[0]);
    title.setStyle(
        "-fx-font-family: monospace; -fx-font-size: 11; -fx-font-weight: bold;"
            + " -fx-text-fill: #4ec9b0; -fx-wrap-text: true;");
    title.setMaxWidth(340);
    box.getChildren().add(title);
    if (parts.length > 1 && !parts[1].isBlank()) {
      Label desc = new Label(parts[1].trim());
      desc.setStyle("-fx-font-size: 11; -fx-text-fill: #d4d4d4; -fx-wrap-text: true;");
      desc.setMaxWidth(340);
      box.getChildren().add(desc);
    }
    docHoverPopup = new Popup();
    docHoverPopup.getContent().add(box);
    docHoverPopup.setAutoHide(true);
    docHoverPopup.show(stage, pos.getX() + 12, pos.getY() + 14);
  }

  /**
   * Shows a small parameter-hint popup when '(' is typed after a method name, displaying the method
   * signature.
   */
  private void showParamHint(CodeArea editor) {
    int caret = editor.getCaretPosition();
    if (caret < 2) return;
    String textBefore = editor.getText(0, caret - 1); // exclude the '('
    String word = extractWordAt(textBefore, textBefore.length());
    if (word.isEmpty()) return;

    // Try member lookup: type before dot
    String sig = null;
    int dotIdx = textBefore.lastIndexOf('.', textBefore.length() - word.length() - 1);
    if (dotIdx > 0) {
      String beforeDot = textBefore.substring(0, dotIdx);
      String varName = extractWordAt(beforeDot, beforeDot.length());
      if (!varName.isEmpty()) {
        String type = resolveVariableType(varName, textBefore);
        sig = lookupDoc(type, word);
      }
    }
    if (sig == null) sig = lookupDoc(word, null);
    if (sig == null) return;

    // Show a small dark tooltip near the caret
    String finalSig = sig;
    hideDocHoverPopup();
    Label hint = new Label(finalSig.split("\n")[0] + "(…)");
    hint.setStyle(
        "-fx-background-color: #252526; -fx-border-color: #555; -fx-border-radius: 3;"
            + " -fx-padding: 2 6 2 6; -fx-font-family: monospace; -fx-font-size: 11;"
            + " -fx-text-fill: #9cdcfe;");
    docHoverPopup = new Popup();
    docHoverPopup.getContent().add(hint);
    docHoverPopup.setAutoHide(true);
    editor
        .getCaretBounds()
        .ifPresent(b -> docHoverPopup.show(editor, b.getMaxX(), b.getMaxY() + 2));
  }

  private void hideDocHoverPopup() {
    if (docHoverPopup != null) {
      docHoverPopup.hide();
      docHoverPopup = null;
    }
  }

  // ── User-symbol scanner ────────────────────────────────────────────────────

  /**
   * Scans the editor text for user-declared variables (Type varName) and functions (fun retType
   * name(...)) to include them in global completion.
   */
  private List<UserSymbol> scanUserSymbols(String code) {
    List<UserSymbol> symbols = new java.util.ArrayList<>();
    Set<String> builtinKeywords =
        Set.of("if", "else", "while", "for", "return", "new", "fun", "class", "spork", "repeat");

    // Variable declarations: Type varName (starts with uppercase type)
    Matcher m = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\s+([a-z_][a-zA-Z0-9_]*)\\b").matcher(code);
    while (m.find()) {
      String type = m.group(1), name = m.group(2);
      if (!builtinKeywords.contains(name)) {
        symbols.add(new UserSymbol(name, type, type + " " + name));
      }
    }

    // Function declarations: fun retType name(...)
    m =
        Pattern.compile(
                "\\bfun\\s+([a-zA-Z][a-zA-Z0-9]*)\\s+([a-zA-Z][a-zA-Z0-9_]*)\\s*\\(([^)]*)\\)")
            .matcher(code);
    while (m.find()) {
      String retType = m.group(1), name = m.group(2), args = m.group(3);
      symbols.add(new UserSymbol(name, retType, "fun " + retType + " " + name + "(" + args + ")"));
    }

    return symbols;
  }

  // ── Word extraction helper ─────────────────────────────────────────────────

  private boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

  /**
   * Extracts the word that spans position {@code pos} in {@code text}. If {@code pos} is at a
   * non-word character, returns "".
   */
  private String extractWordAt(String text, int pos) {
    if (text == null || pos < 0 || pos > text.length()) return "";
    int end = pos;
    while (end < text.length() && isWordChar(text.charAt(end))) end++;
    int start = pos;
    while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
    return text.substring(start, end);
  }
}
