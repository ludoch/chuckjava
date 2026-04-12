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

  private static final List<String> TYPE_CANDIDATES =
      List.of(
          "int",
          "float",
          "dur",
          "time",
          "string",
          "void",
          "Event",
          "if",
          "else",
          "while",
          "for",
          "repeat",
          "return",
          "new",
          "spork",
          "fun",
          "class",
          "SinOsc",
          "SawOsc",
          "TriOsc",
          "SqrOsc",
          "PulseOsc",
          "Phasor",
          "Noise",
          "Impulse",
          "Step",
          "Mandolin",
          "Clarinet",
          "Plucked",
          "Rhodey",
          "Bowed",
          "StifKarp",
          "Moog",
          "Flute",
          "Sitar",
          "Brass",
          "Saxofony",
          "Shakers",
          "ADSR",
          "Adsr",
          "Envelope",
          "Gain",
          "Pan2",
          "FFT",
          "IFFT",
          "LiSa",
          "Gen5",
          "Gen7",
          "Gen10",
          "RMS",
          "Centroid",
          "Echo",
          "Delay",
          "DelayL",
          "JCRev",
          "Chorus",
          "ResonZ",
          "Lpf",
          "OnePole",
          "OneZero",
          "MidiIn",
          "SndBuf",
          "WvOut",
          "IO",
          "OscIn",
          "OscOut",
          "OscMsg",
          "Hid",
          "HidMsg",
          "dac",
          "adc",
          "blackhole",
          "now",
          "second",
          "ms",
          "samp",
          "Std",
          "Math",
          "Machine",
          "me",
          "chout",
          "cherr",
          "newline");

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
      "\\b(if|else|while|for|repeat|return|new|spork|fun|class|extends|public|private|static|void)\\b";
  private static final String TYPE_PATTERN =
      "\\b(int|float|dur|time|string|"
          + "SinOsc|SawOsc|TriOsc|SqrOsc|PulseOsc|Phasor|Noise|Impulse|Step|"
          + "Mandolin|Clarinet|Plucked|Rhodey|Bowed|StifKarp|Moog|Flute|Sitar|Brass|Saxofony|Shakers|"
          + "ADSR|Adsr|Gain|Pan2|FFT|IFFT|LiSa|Gen5|Gen7|Gen10|Echo|Delay|DelayL|JCRev|Chorus|ResonZ|Lpf|OnePole|OneZero|"
          + "MidiIn|SndBuf|WvOut|OscIn|OscOut|OscMsg|Hid|HidMsg)\\b";
  private static final String BUILTIN_PATTERN =
      "\\b(dac|adc|blackhole|now|second|ms|samp|Std|Math|Machine|me|chout|cherr|newline)\\b";
  private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";
  private static final String STRING_PATTERN = "\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"";
  private static final String COMMENT_PATTERN = "//[^\n]*|/\\*.*?\\*/";
  private static final String CHUCK_OP_PATTERN = "=>|@=>|::|<=>|!=>";

  private static final Pattern HIGHLIGHT_PATTERN =
      Pattern.compile(
          "(?<COMMENT>"
              + COMMENT_PATTERN
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

    int sampleRate = 44100;
    vm = new ChuckVM(sampleRate);
    vm.addPrintListener(this::print);

    // Master gain is a scalar multiplier applied in ChuckAudio (not a UGen in the signal path).
    // Wiring the Gain UGen into the DAC pull-graph would leave it un-ticked → silence.
    masterGain = new Gain();

    audio = new ChuckAudio(vm, 512, 2, sampleRate);

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

    // ── File browser ──
    fileBrowser = createFileBrowser();
    VBox leftPanel = new VBox(new Label("  Project"), fileBrowser);
    VBox.setVgrow(fileBrowser, Priority.ALWAYS);
    leftPanel.setPrefWidth(210);

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

    VBox masterControls = new VBox(5, new Label("Master Gain"), masterGainSlider, vmTimeLabel);
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
              : m.group("KEYWORD") != null
                  ? "ck-keyword"
                  : m.group("TYPE") != null
                      ? "ck-type"
                      : m.group("BUILTIN") != null
                          ? "ck-builtin"
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
    scene
        .getRoot()
        .setStyle(
            ".ck-comment  { -rtfx-background-color: transparent; -fx-fill: #5c7a5c; }"
                + ".ck-keyword  { -fx-fill: #cc7722; -fx-font-weight: bold; }"
                + ".ck-type     { -fx-fill: #2255aa; -fx-font-weight: bold; }"
                + ".ck-builtin  { -fx-fill: #8844aa; }"
                + ".ck-string   { -fx-fill: #b5491c; }"
                + ".ck-number   { -fx-fill: #1c7c1c; }"
                + ".ck-chuckop  { -fx-fill: #aa3322; -fx-font-weight: bold; }");
    // Inject into scene stylesheets so RichTextFX picks them up
    scene
        .getStylesheets()
        .add(
            "data:text/css,"
                + java.net.URLEncoder.encode(
                    ".ck-comment  { -rtfx-background-color: transparent; -fx-fill: #5c7a5c; }"
                        + ".ck-keyword  { -fx-fill: #cc7722; -fx-font-weight: bold; }"
                        + ".ck-type     { -fx-fill: #2255aa; -fx-font-weight: bold; }"
                        + ".ck-builtin  { -fx-fill: #8844aa; }"
                        + ".ck-string   { -fx-fill: #b5491c; }"
                        + ".ck-number   { -fx-fill: #1c7c1c; }"
                        + ".ck-chuckop  { -fx-fill: #aa3322; -fx-font-weight: bold; }",
                    java.nio.charset.StandardCharsets.UTF_8));
  }

  private void addNewTab(String title, String content) {
    CodeArea editor = new CodeArea();
    editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
    editor.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 13;");
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

    Tab tab = new Tab(title, editor);
    tabPane.getTabs().add(tab);
    tabPane.getSelectionModel().select(tab);
  }

  private void showCompletionPopup(CodeArea editor) {
    if (currentPopup != null) {
      currentPopup.hide();
      currentPopup = null;
    }

    int caretPos = editor.getCaretPosition();
    String textBefore = editor.getText(0, caretPos);

    // Find the start of the current "word" (including dots and colons)
    int i = textBefore.length() - 1;
    while (i >= 0
        && (Character.isLetterOrDigit(textBefore.charAt(i))
            || textBefore.charAt(i) == '_'
            || textBefore.charAt(i) == '.'
            || textBefore.charAt(i) == ':')) {
      i--;
    }
    String prefix = textBefore.substring(i + 1).trim();

    List<String> candidates;
    String filter;
    String resolvedType = null;

    if (prefix.contains("::")) {
      // Duration completion: 1::[second]
      int colIdx = prefix.lastIndexOf("::");
      candidates = new java.util.ArrayList<>(DURATION_CANDIDATES);
      filter = prefix.substring(colIdx + 2);
    } else if (prefix.contains(".")) {
      // Member completion: s.[gain]
      int dotIdx = prefix.lastIndexOf('.');
      String varName = prefix.substring(0, dotIdx);
      filter = prefix.substring(dotIdx + 1);
      resolvedType = resolveVariableType(varName, textBefore);
      candidates = new java.util.ArrayList<>(getDynamicMemberCandidates(resolvedType));
    } else {
      // Global context: types, keywords, and user-declared symbols
      List<String> base =
          TYPE_CANDIDATES.stream()
              .filter(
                  s ->
                      Character.isUpperCase(s.charAt(0))
                          || s.equals("int")
                          || s.equals("float")
                          || s.equals("dur")
                          || s.equals("time")
                          || s.equals("string")
                          || s.equals("void")
                          || List.of(
                                  "if", "else", "while", "for", "repeat", "return", "new", "spork",
                                  "fun", "class", "dac", "adc", "now", "me", "chout", "cherr")
                              .contains(s))
              .collect(Collectors.toList());
      candidates = new java.util.ArrayList<>(base);
      // Add user-declared variables and functions
      for (UserSymbol sym : scanUserSymbols(editor.getText())) {
        if (!candidates.contains(sym.name())) candidates.add(sym.name());
      }
      filter = prefix;
    }

    final String finalFilter = filter.toLowerCase();
    final String finalResolvedType = resolvedType;

    List<String> matches =
        candidates.stream()
            .filter(s -> s.toLowerCase().startsWith(finalFilter))
            .distinct()
            .sorted()
            .collect(Collectors.toList());

    if (matches.isEmpty()) return;

    // ── Completion list ──
    ListView<String> listView = new ListView<>();
    listView.getItems().addAll(matches);
    listView.setPrefWidth(175);
    listView.setPrefHeight(Math.min(matches.size() * 24 + 4, 200));

    // ── Doc panel ──
    Label docLabel = new Label("Select an item for documentation");
    docLabel.setWrapText(true);
    docLabel.setPrefWidth(235);
    docLabel.setMaxHeight(196);
    docLabel.setAlignment(javafx.geometry.Pos.TOP_LEFT);
    docLabel.setStyle(
        "-fx-background-color: #fefde8; -fx-padding: 8; -fx-font-size: 11;"
            + " -fx-border-color: #ddd; -fx-border-width: 0 0 0 1;");

    listView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, sel) -> {
              if (sel == null) return;
              String docText =
                  finalResolvedType != null
                      ? lookupDoc(finalResolvedType, sel)
                      : lookupDoc(sel, null);
              docLabel.setText(docText != null ? docText : sel);
            });

    HBox popupContent = new HBox(listView, docLabel);
    popupContent.setStyle(
        "-fx-background-color: white; -fx-border-color: #aaa; -fx-border-width: 1;"
            + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.25), 6, 0, 0, 2);");

    Popup popup = new Popup();
    popup.getContent().add(popupContent);
    popup.setAutoHide(true);
    this.currentPopup = popup;

    listView.setOnKeyPressed(
        ke -> {
          if (ke.getCode() == KeyCode.ENTER) {
            complete(editor, listView.getSelectionModel().getSelectedItem(), finalFilter);
            popup.hide();
            currentPopup = null;
          } else if (ke.getCode() == KeyCode.ESCAPE) {
            popup.hide();
            currentPopup = null;
          }
        });

    listView.setOnMouseClicked(
        me -> {
          if (me.getClickCount() == 2) {
            complete(editor, listView.getSelectionModel().getSelectedItem(), finalFilter);
            popup.hide();
            currentPopup = null;
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
    Tab tab = tabPane.getSelectionModel().getSelectedItem();
    if (tab != null && tab.getContent() instanceof CodeArea area) {
      return area;
    }
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
          if (sel != null) {
            tabPane.getTabs().remove(sel);
            tabToFile.remove(sel);
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

    Button replaceBtn = new Button("Replace Last  [Ctrl+Shift+Enter]");
    replaceBtn.setOnAction(e -> replaceLastShred());

    Button removeLastBtn = new Button("Remove Last  [Ctrl+.]");
    removeLastBtn.setOnAction(e -> removeLastShred());

    Button stopAllBtn = new Button("Stop All  [Ctrl+/]");
    stopAllBtn.setStyle("-fx-background-color: #f0b8b8; -fx-font-weight: bold;");
    stopAllBtn.setOnAction(e -> clearVM());

    Button recordBtn = new Button("Record");
    recordBtn.setOnAction(e -> toggleRecord(recordBtn));

    ToolBar tb =
        new ToolBar(addShredBtn, replaceBtn, removeLastBtn, stopAllBtn, new Separator(), recordBtn);

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
    CodeArea editor = getCurrentEditor();
    if (editor == null) return;

    File currentFile = getCurrentFile();
    String path = currentFile != null ? currentFile.getName() : "Untitled.ck";
    statusLabel.setText("  Compiling…");

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
        }
        ShredInfo info = new ShredInfo(id, currentFile.getName(), shredObj);
        shredListView.getItems().add(info);
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

      org.chuck.compiler.ChuckEmitter emitter = new org.chuck.compiler.ChuckEmitter();
      ChuckCode code = emitter.emit(ast, path);

      ChuckShred shred = new ChuckShred(code);
      vm.spork(shred);

      String name = currentFile != null ? currentFile.getName() : "User";
      ShredInfo info = new ShredInfo(shred.getId(), name, shred);
      shredListView.getItems().add(info);

      print("Sporked Shred " + shred.getId() + " (" + name + ")");
      updateStatus();

    } catch (Exception ex) {
      String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
      print("Compilation Error: " + msg);
      highlightErrorLine(msg);
      statusLabel.setText("  Compilation failed");
    }
  }

  private void replaceLastShred() {
    if (!shredListView.getItems().isEmpty()) {
      ShredInfo last = shredListView.getItems().get(shredListView.getItems().size() - 1);
      vm.removeShred(last.id);
      shredListView.getItems().remove(last);
      print("Replacing Shred " + last.id);
    }
    addShred();
  }

  private void removeLastShred() {
    if (!shredListView.getItems().isEmpty()) {
      ShredInfo last = shredListView.getItems().get(shredListView.getItems().size() - 1);
      vm.removeShred(last.id);
      shredListView.getItems().remove(last);
      print("Removed Shred " + last.id);
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

  private void updateVMTime() {
    double seconds = vm.getCurrentTime() / (double) vm.getSampleRate();
    vmTimeLabel.setText(String.format("Time: %.3fs", seconds));
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

    gc.beginPath();
    for (int i = 0; i < mags.length; i++) {
      double x = i * binW;
      double val = Math.min(1.0, mags[i] * 10.0);
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

  // ── Error line highlighting ─────────────────────────────────────────────────

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
      addNewTab(file.getName(), Files.readString(file.toPath()));
      tabToFile.put(tabPane.getSelectionModel().getSelectedItem(), file);
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
        Files.writeString(file.toPath(), getCurrentEditor().getText());
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
        Files.writeString(file.toPath(), getCurrentEditor().getText());
        Tab tab = tabPane.getSelectionModel().getSelectedItem();
        tab.setText(file.getName());
        tabToFile.put(tab, file);
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
    Label label = new Label(text);
    label.setWrapText(true);
    label.setMaxWidth(340);
    label.setStyle(
        "-fx-background-color: #fefde8; -fx-border-color: #c8c050;"
            + " -fx-border-radius: 3; -fx-background-radius: 3;"
            + " -fx-padding: 6 10 6 10; -fx-font-size: 11;");
    docHoverPopup = new Popup();
    docHoverPopup.getContent().add(label);
    docHoverPopup.setAutoHide(true);
    docHoverPopup.show(stage, pos.getX() + 12, pos.getY() + 14);
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
