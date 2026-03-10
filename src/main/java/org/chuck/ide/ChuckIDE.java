package org.chuck.ide;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Orientation;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.chuck.audio.ChuckAudio;
import org.chuck.compiler.ChuckEmitter;
import org.chuck.compiler.ChuckLexer;
import org.chuck.compiler.ChuckParser;
import org.chuck.core.*;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Professional desktop IDE for ChucK-Java (JDK 25).
 * Features syntax highlighting (RichTextFX), line numbers, keyboard shortcuts,
 * shred management, WAV recording, and a file browser.
 */
public class ChuckIDE extends Application {

    // ── Syntax highlighting patterns ───────────────────────────────────────────
    private static final String KEYWORD_PATTERN =
        "\\b(if|else|while|for|repeat|return|new|spork|fun|class|extends|public|private|static|void)\\b";
    private static final String TYPE_PATTERN =
        "\\b(int|float|dur|time|string|" +
        "SinOsc|SawOsc|TriOsc|SqrOsc|PulseOsc|Phasor|Noise|Impulse|Step|" +
        "Mandolin|Clarinet|Plucked|Rhodey|" +
        "ADSR|Adsr|Gain|Pan2|Echo|Delay|DelayL|JCRev|Chorus|ResonZ|Lpf|OnePole|OneZero|" +
        "MidiIn|SndBuf|WvOut)\\b";
    private static final String BUILTIN_PATTERN =
        "\\b(dac|adc|blackhole|now|second|ms|samp|Std|Math|me)\\b";
    private static final String NUMBER_PATTERN  = "\\b\\d+(\\.\\d+)?\\b";
    private static final String STRING_PATTERN  = "\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"";
    private static final String COMMENT_PATTERN = "//[^\n]*|/\\*.*?\\*/";
    private static final String CHUCK_OP_PATTERN = "=>|@=>|::";

    private static final Pattern HIGHLIGHT_PATTERN = Pattern.compile(
        "(?<COMMENT>"  + COMMENT_PATTERN  + ")" +
        "|(?<KEYWORD>" + KEYWORD_PATTERN  + ")" +
        "|(?<TYPE>"    + TYPE_PATTERN     + ")" +
        "|(?<BUILTIN>" + BUILTIN_PATTERN  + ")" +
        "|(?<STRING>"  + STRING_PATTERN   + ")" +
        "|(?<NUMBER>"  + NUMBER_PATTERN   + ")" +
        "|(?<CHUCKOP>" + CHUCK_OP_PATTERN + ")",
        Pattern.DOTALL
    );

    // ── State ──────────────────────────────────────────────────────────────────
    private ChuckVM vm;
    private ChuckAudio audio;
    private CodeArea editor;
    private TextArea outputArea;
    private ListView<String> shredListView;
    private final Map<String, Integer> shredNameToId = new ConcurrentHashMap<>();
    private Label statusLabel;
    private TreeView<File> fileBrowser;
    private Stage stage;
    private File currentFile;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;

        int sampleRate = 44100;
        vm = new ChuckVM(sampleRate);
        audio = new ChuckAudio(vm, 512, 2, sampleRate);
        audio.start();

        primaryStage.setTitle("ChucK-Java IDE (JDK 25)");

        MenuBar menuBar = createMenuBar(primaryStage);
        ToolBar toolBar = createToolBar();

        // ── Code editor with syntax highlighting and line numbers ──
        editor = new CodeArea();
        editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
        editor.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 13;");
        editor.replaceText(0, 0,
            "// Welcome to ChucK-Java!\nSinOsc s => dac;\n0.5 => s.gain;\n440 => s.freq;\n1::second => now;");
        // Compute highlighting on every text change (debounced 50 ms)
        editor.multiPlainChanges()
              .successionEnds(Duration.ofMillis(50))
              .subscribe(ignore -> editor.setStyleSpans(0, computeHighlighting(editor.getText())));
        editor.setStyleSpans(0, computeHighlighting(editor.getText()));

        // ── File browser ──
        fileBrowser = createFileBrowser();
        VBox leftPanel = new VBox(new Label("  Project"), fileBrowser);
        VBox.setVgrow(fileBrowser, Priority.ALWAYS);
        leftPanel.setPrefWidth(210);

        // ── Shred panel ──
        shredListView = new ListView<>();
        Button removeSelectedBtn = new Button("Stop Selected");
        removeSelectedBtn.setMaxWidth(Double.MAX_VALUE);
        removeSelectedBtn.setOnAction(e -> removeSelectedShred());
        VBox rightPanel = new VBox(8, new Label("Active Shreds"), shredListView, removeSelectedBtn);
        rightPanel.setPadding(new Insets(8));
        rightPanel.setPrefWidth(195);

        // ── Output console ──
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefHeight(110);
        outputArea.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 12;");

        statusLabel = new Label("  Ready");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setStyle("-fx-background-color: #ddd; -fx-padding: 2;");
        VBox bottomPanel = new VBox(outputArea, statusBar);

        // ── Layout ──
        SplitPane hSplit = new SplitPane(leftPanel, editor, rightPanel);
        hSplit.setDividerPositions(0.18, 0.82);
        SplitPane vSplit = new SplitPane(hSplit, bottomPanel);
        vSplit.setOrientation(Orientation.VERTICAL);
        vSplit.setDividerPositions(0.76);

        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar, toolBar));
        root.setCenter(vSplit);

        Scene scene = new Scene(root, 1250, 820);
        scene.getStylesheets().add(getClass().getResource("/chuck-ide.css") != null
            ? getClass().getResource("/chuck-ide.css").toExternalForm() : "");
        applyInlineStyles(scene);
        primaryStage.setScene(scene);
        primaryStage.show();

        print("ChucK-Java Engine Online — JDK 25 | Virtual Threads | Vector API");
    }

    // ── Syntax highlighting ────────────────────────────────────────────────────

    private static StyleSpans<Collection<String>> computeHighlighting(String text) {
        Matcher m = HIGHLIGHT_PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
        int lastEnd = 0;
        while (m.find()) {
            String styleClass =
                m.group("COMMENT") != null ? "ck-comment" :
                m.group("KEYWORD") != null ? "ck-keyword" :
                m.group("TYPE")    != null ? "ck-type"    :
                m.group("BUILTIN") != null ? "ck-builtin" :
                m.group("STRING")  != null ? "ck-string"  :
                m.group("NUMBER")  != null ? "ck-number"  :
                m.group("CHUCKOP") != null ? "ck-chuckop" : null;
            spansBuilder.add(Collections.emptyList(), m.start() - lastEnd);
            spansBuilder.add(styleClass != null
                ? Collections.singleton(styleClass) : Collections.emptyList(),
                m.end() - m.start());
            lastEnd = m.end();
        }
        spansBuilder.add(Collections.emptyList(), text.length() - lastEnd);
        return spansBuilder.create();
    }

    /** Apply highlight colours as inline JavaFX CSS (avoids needing an external file). */
    private void applyInlineStyles(Scene scene) {
        scene.getRoot().setStyle(
            ".ck-comment  { -rtfx-background-color: transparent; -fx-fill: #5c7a5c; }" +
            ".ck-keyword  { -fx-fill: #cc7722; -fx-font-weight: bold; }" +
            ".ck-type     { -fx-fill: #2255aa; -fx-font-weight: bold; }" +
            ".ck-builtin  { -fx-fill: #8844aa; }" +
            ".ck-string   { -fx-fill: #b5491c; }" +
            ".ck-number   { -fx-fill: #1c7c1c; }" +
            ".ck-chuckop  { -fx-fill: #aa3322; -fx-font-weight: bold; }");
        // Inject into scene stylesheets so RichTextFX picks them up
        scene.getStylesheets().add("data:text/css," + java.net.URLEncoder.encode(
            ".ck-comment  { -rtfx-background-color: transparent; -fx-fill: #5c7a5c; }" +
            ".ck-keyword  { -fx-fill: #cc7722; -fx-font-weight: bold; }" +
            ".ck-type     { -fx-fill: #2255aa; -fx-font-weight: bold; }" +
            ".ck-builtin  { -fx-fill: #8844aa; }" +
            ".ck-string   { -fx-fill: #b5491c; }" +
            ".ck-number   { -fx-fill: #1c7c1c; }" +
            ".ck-chuckop  { -fx-fill: #aa3322; -fx-font-weight: bold; }",
            java.nio.charset.StandardCharsets.UTF_8));
    }

    // ── Menu bar ───────────────────────────────────────────────────────────────

    private MenuBar createMenuBar(Stage stage) {
        MenuBar mb = new MenuBar();

        // File
        Menu fileMenu = new Menu("_File");
        MenuItem newItem  = new MenuItem("New");
        newItem.setAccelerator(new KeyCodeCombination(KeyCode.N, KeyCombination.CONTROL_DOWN));
        newItem.setOnAction(e -> { editor.replaceText(""); currentFile = null; stage.setTitle("ChucK-Java IDE"); });

        MenuItem openItem = new MenuItem("Open…");
        openItem.setAccelerator(new KeyCodeCombination(KeyCode.O, KeyCombination.CONTROL_DOWN));
        openItem.setOnAction(e -> openFile(stage));

        MenuItem saveItem = new MenuItem("Save");
        saveItem.setAccelerator(new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN));
        saveItem.setOnAction(e -> saveFile(stage));

        MenuItem saveAsItem = new MenuItem("Save As…");
        saveAsItem.setAccelerator(new KeyCodeCombination(KeyCode.S,
            KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN));
        saveAsItem.setOnAction(e -> { currentFile = null; saveFile(stage); });

        MenuItem exitItem = new MenuItem("Exit");
        exitItem.setOnAction(e -> Platform.exit());

        fileMenu.getItems().addAll(newItem, openItem, saveItem, saveAsItem, new SeparatorMenuItem(), exitItem);

        // Edit
        Menu editMenu = new Menu("_Edit");
        MenuItem undoItem  = new MenuItem("Undo");
        undoItem.setAccelerator(new KeyCodeCombination(KeyCode.Z, KeyCombination.CONTROL_DOWN));
        undoItem.setOnAction(e -> editor.undo());

        MenuItem redoItem  = new MenuItem("Redo");
        redoItem.setAccelerator(new KeyCodeCombination(KeyCode.Y, KeyCombination.CONTROL_DOWN));
        redoItem.setOnAction(e -> editor.redo());

        MenuItem cutItem   = new MenuItem("Cut");
        cutItem.setAccelerator(new KeyCodeCombination(KeyCode.X, KeyCombination.CONTROL_DOWN));
        cutItem.setOnAction(e -> editor.cut());

        MenuItem copyItem  = new MenuItem("Copy");
        copyItem.setAccelerator(new KeyCodeCombination(KeyCode.C, KeyCombination.CONTROL_DOWN));
        copyItem.setOnAction(e -> editor.copy());

        MenuItem pasteItem = new MenuItem("Paste");
        pasteItem.setAccelerator(new KeyCodeCombination(KeyCode.V, KeyCombination.CONTROL_DOWN));
        pasteItem.setOnAction(e -> editor.paste());

        MenuItem selAllItem = new MenuItem("Select All");
        selAllItem.setAccelerator(new KeyCodeCombination(KeyCode.A, KeyCombination.CONTROL_DOWN));
        selAllItem.setOnAction(e -> editor.selectAll());

        editMenu.getItems().addAll(undoItem, redoItem, new SeparatorMenuItem(),
            cutItem, copyItem, pasteItem, new SeparatorMenuItem(), selAllItem);

        // Examples
        Menu examplesMenu = new Menu("_Examples");
        loadExamples(examplesMenu);

        // Help
        Menu helpMenu = new Menu("_Help");
        MenuItem aboutItem = new MenuItem("About ChucK-Java");
        aboutItem.setOnAction(e -> {
            Alert a = new Alert(Alert.AlertType.INFORMATION);
            a.setTitle("About ChucK-Java");
            a.setHeaderText("ChucK-Java — JDK 25 port");
            a.setContentText(
                "A modern port of the ChucK strongly-timed music language to Java 25.\n\n" +
                "Features: Virtual Threads, Vector API, JavaFX IDE with syntax highlighting.\n\n" +
                "Original ChucK: https://chuck.stanford.edu/");
            a.showAndWait();
        });
        helpMenu.getItems().add(aboutItem);

        mb.getMenus().addAll(fileMenu, editMenu, examplesMenu, helpMenu);
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

        ToolBar tb = new ToolBar(addShredBtn, replaceBtn, removeLastBtn, stopAllBtn,
                                  new Separator(), recordBtn);

        // Keyboard shortcuts on the scene — wired after scene is set
        Platform.runLater(() -> {
            if (stage.getScene() == null) return;
            stage.getScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.ENTER, KeyCombination.CONTROL_DOWN),
                this::addShred);
            stage.getScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.ENTER,
                    KeyCombination.CONTROL_DOWN, KeyCombination.SHIFT_DOWN),
                this::replaceLastShred);
            stage.getScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.PERIOD, KeyCombination.CONTROL_DOWN),
                this::removeLastShred);
            stage.getScene().getAccelerators().put(
                new KeyCodeCombination(KeyCode.SLASH, KeyCombination.CONTROL_DOWN),
                this::clearVM);
        });

        return tb;
    }

    // ── File browser ───────────────────────────────────────────────────────────

    private TreeView<File> createFileBrowser() {
        File rootDir = new File(".");
        TreeItem<File> rootItem = buildTreeItem(rootDir);
        TreeView<File> tree = new TreeView<>(rootItem);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.getName());
            }
        });
        tree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<File> sel = tree.getSelectionModel().getSelectedItem();
                if (sel != null && sel.getValue().isFile()) loadFileIntoEditor(sel.getValue());
            }
        });
        return tree;
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
        File dir = new File("../examples");
        if (!dir.exists()) dir = new File("examples");
        if (dir.exists()) addExampleSubmenu(menu, dir);
        else menu.getItems().add(new MenuItem("(no examples/ directory found)"));
    }

    private void addExampleSubmenu(Menu menu, File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) {
                Menu sub = new Menu(f.getName());
                addExampleSubmenu(sub, f);
                menu.getItems().add(sub);
            } else if (f.getName().endsWith(".ck")) {
                MenuItem mi = new MenuItem(f.getName());
                mi.setOnAction(e -> loadFileIntoEditor(f));
                menu.getItems().add(mi);
            }
        }
    }

    // ── Shred management ───────────────────────────────────────────────────────

    private void addShred() {
        String source = editor.getText();
        statusLabel.setText("  Compiling…");
        try {
            ChuckLexer lexer = new ChuckLexer(source);
            ChuckParser parser = new ChuckParser(lexer.tokenize());
            ChuckEmitter emitter = new ChuckEmitter();
            ChuckCode code = emitter.emit(parser.parse(),
                currentFile != null ? currentFile.getName() : "Untitled");

            ChuckShred shred = new ChuckShred(code);
            vm.spork(shred);

            String label = "Shred " + shred.getId()
                + " (" + (currentFile != null ? currentFile.getName() : "User") + ")";
            shredNameToId.put(label, shred.getId());
            shredListView.getItems().add(label);
            print("Sporked " + label);
            updateStatus();

            Thread.ofVirtual().start(() -> {
                while (!shred.isDone()) {
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                }
                Platform.runLater(() -> {
                    shredListView.getItems().remove(label);
                    shredNameToId.remove(label);
                    print("Finished: " + label);
                    updateStatus();
                });
            });

        } catch (Exception ex) {
            // Extract line number from exception message if present
            String msg = ex.getMessage() != null ? ex.getMessage() : ex.toString();
            print("Compilation Error: " + msg);
            highlightErrorLine(msg);
            statusLabel.setText("  Compilation failed");
        }
    }

    private void replaceLastShred() {
        if (!shredListView.getItems().isEmpty()) {
            String last = shredListView.getItems().get(shredListView.getItems().size() - 1);
            Integer id = shredNameToId.get(last);
            if (id != null) vm.removeShred(id);
            shredListView.getItems().remove(last);
            shredNameToId.remove(last);
            print("Replacing " + last);
        }
        addShred();
    }

    private void removeLastShred() {
        if (!shredListView.getItems().isEmpty()) {
            String last = shredListView.getItems().get(shredListView.getItems().size() - 1);
            Integer id = shredNameToId.get(last);
            if (id != null) vm.removeShred(id);
            shredListView.getItems().remove(last);
            shredNameToId.remove(last);
            print("Removed " + last);
            updateStatus();
        }
    }

    private void removeSelectedShred() {
        String sel = shredListView.getSelectionModel().getSelectedItem();
        if (sel != null) {
            Integer id = shredNameToId.get(sel);
            if (id != null) vm.removeShred(id);
            shredListView.getItems().remove(sel);
            shredNameToId.remove(sel);
            print("Stopped " + sel);
            updateStatus();
        }
    }

    private void clearVM() {
        print("Stopping all shreds…");
        vm.clear();
        shredListView.getItems().clear();
        shredNameToId.clear();
        statusLabel.setText("  VM cleared");
    }

    private void updateStatus() {
        int n = shredListView.getItems().size();
        statusLabel.setText("  " + (n == 0 ? "Ready" : "Running " + n + " shred" + (n > 1 ? "s" : "")));
    }

    // ── Error line highlighting ─────────────────────────────────────────────────

    /**
     * If the error message contains "at line N", scroll the editor to that line
     * and temporarily highlight it.
     */
    private void highlightErrorLine(String msg) {
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:at|line)\\s+(\\d+)", java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(msg);
        if (!m.find()) return;
        int lineNum = Integer.parseInt(m.group(1)) - 1; // 0-indexed
        if (lineNum < 0 || lineNum >= editor.getParagraphs().size()) return;
        editor.showParagraphAtTop(Math.max(0, lineNum - 3));
        // Select the whole line so user can see where the error is
        int lineStart = editor.position(lineNum, 0).toOffset();
        int lineEnd   = editor.position(lineNum,
            editor.getParagraph(lineNum).length()).toOffset();
        editor.selectRange(lineStart, lineEnd);
    }

    // ── Recording ──────────────────────────────────────────────────────────────

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

    // ── File I/O ───────────────────────────────────────────────────────────────

    private void loadFileIntoEditor(File file) {
        try {
            editor.replaceText(Files.readString(file.toPath()));
            currentFile = file;
            stage.setTitle("ChucK-Java IDE — " + file.getName());
            print("Loaded: " + file.getAbsolutePath());
        } catch (IOException ex) {
            print("Error loading file: " + ex.getMessage());
        }
    }

    private void openFile(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ChucK Files (*.ck)", "*.ck"));
        File file = fc.showOpenDialog(stage);
        if (file != null) loadFileIntoEditor(file);
    }

    private void saveFile(Stage stage) {
        if (currentFile == null) {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ChucK Files (*.ck)", "*.ck"));
            currentFile = fc.showSaveDialog(stage);
        }
        if (currentFile != null) {
            try {
                Files.writeString(currentFile.toPath(), editor.getText());
                print("Saved: " + currentFile.getAbsolutePath());
            } catch (IOException ex) {
                print("Error saving: " + ex.getMessage());
            }
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void print(String text) {
        Platform.runLater(() -> outputArea.appendText(text + "\n"));
    }

    @Override
    public void stop() {
        if (audio != null) audio.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
