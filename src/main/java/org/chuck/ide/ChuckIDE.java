package org.chuck.ide;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.chuck.audio.ChuckAudio;
import org.chuck.compiler.ChuckEmitter;
import org.chuck.compiler.ChuckLexer;
import org.chuck.compiler.ChuckParser;
import org.chuck.core.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A comprehensive professional desktop IDE for ChucK-Java (JDK 25).
 * Replicates and extends the Stanford WebChucK IDE features.
 */
public class ChuckIDE extends Application {
    private ChuckVM vm;
    private ChuckAudio audio;
    private TextArea editor;
    private TextArea outputArea;
    private ListView<String> shredListView;
    private Map<String, Integer> shredNameToId = new ConcurrentHashMap<>();
    private Label statusLabel;
    private TreeView<File> fileBrowser;
    
    private Stage stage;
    private File currentFile;

    @Override
    public void start(Stage primaryStage) {
        this.stage = primaryStage;
        
        // Initialize VM and Audio Engine
        int sampleRate = 44100;
        vm = new ChuckVM(sampleRate);
        audio = new ChuckAudio(vm, 512, 2, sampleRate);
        audio.start();

        primaryStage.setTitle("🎸 ChucK-Java Professional IDE (JDK 25)");

        // --- 1. Menu Bar ---
        MenuBar menuBar = createMenuBar();

        // --- 2. Tool Bar ---
        ToolBar toolBar = createToolBar();

        // --- 3. Editor Area ---
        editor = new TextArea();
        editor.setPromptText("Write your ChucK code here...");
        editor.setText("// Welcome to ChucK-Java!\nSinOsc s => dac;\n0.5 => s.gain;\n440 => s.freq;\n1::second => now;");
        editor.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 14;");

        // --- 4. File Browser (Left) ---
        fileBrowser = createFileBrowser();
        VBox leftPanel = new VBox(new Label("  Project Explorer"), fileBrowser);
        VBox.setVgrow(fileBrowser, Priority.ALWAYS);
        leftPanel.setPrefWidth(220);

        // --- 5. Shred List (Right) ---
        shredListView = new ListView<>();
        Button removeSelectedBtn = new Button("Stop Selected");
        removeSelectedBtn.setMaxWidth(Double.MAX_VALUE);
        removeSelectedBtn.setOnAction(e -> removeSelectedShred());
        
        VBox rightPanel = new VBox(10, new Label("Active Shreds"), shredListView, removeSelectedBtn);
        rightPanel.setPadding(new Insets(10));
        rightPanel.setPrefWidth(200);

        // --- 6. Output & Status (Bottom) ---
        outputArea = new TextArea();
        outputArea.setEditable(false);
        outputArea.setPrefHeight(120);
        outputArea.setStyle("-fx-control-inner-background: #f4f4f4; -fx-font-family: 'Monospaced';");

        statusLabel = new Label(" Ready");
        HBox statusBar = new HBox(statusLabel);
        statusBar.setStyle("-fx-background-color: #ddd; -fx-padding: 2;");

        VBox bottomPanel = new VBox(outputArea, statusBar);

        // --- Layout ---
        BorderPane root = new BorderPane();
        root.setTop(new VBox(menuBar, toolBar));
        
        SplitPane horizontalSplit = new SplitPane();
        horizontalSplit.getItems().addAll(leftPanel, editor, rightPanel);
        horizontalSplit.setDividerPositions(0.2, 0.8);
        
        SplitPane verticalSplit = new SplitPane();
        verticalSplit.setOrientation(javafx.geometry.Orientation.VERTICAL);
        verticalSplit.getItems().addAll(horizontalSplit, bottomPanel);
        verticalSplit.setDividerPositions(0.75);

        root.setCenter(verticalSplit);

        Scene scene = new Scene(root, 1200, 800);
        primaryStage.setScene(scene);
        primaryStage.show();

        print("🔊 ChucK-Java Engine Online - High Performance Synthesis Ready");
    }

    private MenuBar createMenuBar() {
        MenuBar mb = new MenuBar();

        Menu fileMenu = new Menu("File");
        MenuItem newFile = new MenuItem("New");
        newFile.setOnAction(e -> { editor.clear(); currentFile = null; });
        MenuItem openFile = new MenuItem("Open...");
        openFile.setOnAction(e -> openFile());
        MenuItem saveFile = new MenuItem("Save");
        saveFile.setOnAction(e -> saveFile());
        MenuItem exit = new MenuItem("Exit");
        exit.setOnAction(e -> Platform.exit());
        fileMenu.getItems().addAll(newFile, openFile, saveFile, new SeparatorMenuItem(), exit);

        Menu editMenu = new Menu("Edit");
        editMenu.getItems().addAll(new MenuItem("Undo"), new MenuItem("Redo"), new SeparatorMenuItem(), new MenuItem("Cut"), new MenuItem("Copy"), new MenuItem("Paste"));

        Menu examplesMenu = new Menu("Examples");
        loadExamples(examplesMenu);

        Menu helpMenu = new Menu("Help");
        MenuItem about = new MenuItem("About ChucK-Java");
        about.setOnAction(e -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("About ChucK-Java");
            alert.setHeaderText("ChucK-Java (JDK 25 Migration)");
            alert.setContentText("A modern, high-performance migration of the ChucK language to Java 25.\n\nUtilizing Virtual Threads, Vector API, and FFM API.");
            alert.showAndWait();
        });
        helpMenu.getItems().add(about);

        mb.getMenus().addAll(fileMenu, editMenu, examplesMenu, helpMenu);
        return mb;
    }

    private ToolBar createToolBar() {
        Button addShred = new Button("➕ Add Shred");
        addShred.setStyle("-fx-background-color: #ccffcc; -fx-font-weight: bold;");
        addShred.setOnAction(e -> addShred());

        Button replaceShred = new Button("🔄 Replace Last");
        replaceShred.setOnAction(e -> replaceLastShred());

        Button removeLast = new Button("➖ Remove Last");
        removeLast.setOnAction(e -> removeLastShred());

        Button stopAll = new Button("🛑 Stop All");
        stopAll.setStyle("-fx-background-color: #ffcccc; -fx-font-weight: bold;");
        stopAll.setOnAction(e -> clearVM());

        Button recordBtn = new Button("⏺ Record");
        recordBtn.setOnAction(e -> toggleRecord(recordBtn));

        return new ToolBar(addShred, replaceShred, removeLast, stopAll, new Separator(), recordBtn);
    }

    private TreeView<File> createFileBrowser() {
        File rootDir = new File(".");
        TreeItem<File> rootItem = createTreeItem(rootDir);
        TreeView<File> tree = new TreeView<>(rootItem);
        tree.setShowRoot(false);
        tree.setCellFactory(tv -> new TreeCell<>() {
            @Override
            protected void updateItem(File item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.getName());
                }
            }
        });
        tree.setOnMouseClicked(event -> {
            if (event.getClickCount() == 2) {
                TreeItem<File> item = tree.getSelectionModel().getSelectedItem();
                if (item != null && item.getValue().isFile()) {
                    loadFile(item.getValue());
                }
            }
        });
        return tree;
    }

    private TreeItem<File> createTreeItem(File file) {
        TreeItem<File> item = new TreeItem<>(file);
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    if (child.getName().startsWith(".") || child.getName().equals("target")) continue;
                    item.getChildren().add(createTreeItem(child));
                }
            }
        }
        return item;
    }

    private void loadExamples(Menu menu) {
        File examplesDir = new File("../examples");
        if (!examplesDir.exists()) examplesDir = new File("examples");
        if (examplesDir.exists()) addExampleSubmenu(menu, examplesDir);
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
                mi.setOnAction(e -> loadFile(f));
                menu.getItems().add(mi);
            }
        }
    }

    private void addShred() {
        String source = editor.getText();
        statusLabel.setText(" Compiling...");
        try {
            ChuckLexer lexer = new ChuckLexer(source);
            ChuckParser parser = new ChuckParser(lexer.tokenize());
            ChuckEmitter emitter = new ChuckEmitter();
            ChuckCode code = emitter.emit(parser.parse(), currentFile != null ? currentFile.getName() : "Untitled");

            ChuckShred shred = new ChuckShred(code);
            vm.spork(shred);
            
            String shredName = "Shred " + shred.getId() + " (" + (currentFile != null ? currentFile.getName() : "User") + ")";
            shredNameToId.put(shredName, shred.getId());
            shredListView.getItems().add(shredName);
            
            print("🚀 Sporked " + shredName);
            statusLabel.setText(" Running " + shredListView.getItems().size() + " shreds");

            Thread.ofVirtual().start(() -> {
                while (!shred.isDone()) {
                    try { Thread.sleep(100); } catch (InterruptedException e) {}
                }
                Platform.runLater(() -> {
                    if (shredListView.getItems().contains(shredName)) {
                        shredListView.getItems().remove(shredName);
                        shredNameToId.remove(shredName);
                        print("🏁 " + shredName + " finished.");
                        statusLabel.setText(" Running " + shredListView.getItems().size() + " shreds");
                    }
                });
            });

        } catch (Exception e) {
            print("❌ Compilation Error: " + e.getMessage());
            statusLabel.setText(" Compilation failed");
        }
    }

    private void removeLastShred() {
        if (!shredListView.getItems().isEmpty()) {
            String last = shredListView.getItems().get(shredListView.getItems().size() - 1);
            int id = shredNameToId.get(last);
            vm.removeShred(id);
            shredListView.getItems().remove(last);
            shredNameToId.remove(last);
            print("➖ Removed " + last);
        }
    }

    private void replaceLastShred() {
        if (!shredListView.getItems().isEmpty()) {
            String last = shredListView.getItems().get(shredListView.getItems().size() - 1);
            int id = shredNameToId.get(last);
            vm.removeShred(id);
            shredListView.getItems().remove(last);
            shredNameToId.remove(last);
            print("🔄 Replacing " + last + "...");
        }
        addShred();
    }

    private void removeSelectedShred() {
        String selected = shredListView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int id = shredNameToId.get(selected);
            vm.removeShred(id);
            shredListView.getItems().remove(selected);
            shredNameToId.remove(selected);
            print("🛑 Stopped " + selected);
        }
    }

    private void clearVM() {
        print("🧹 Stopping all shreds and clearing VM state...");
        vm.clear();
        shredListView.getItems().clear();
        shredNameToId.clear();
        statusLabel.setText(" VM Reset Complete");
    }

    private void toggleRecord(Button btn) {
        try {
            if (audio.isRecording()) {
                audio.stopRecording();
                btn.setText("⏺ Record");
                btn.setStyle("");
                print("💾 Recording saved to session.wav");
            } else {
                audio.startRecording("session.wav");
                btn.setText("⏹ Stop Recording");
                btn.setStyle("-fx-text-fill: red; -fx-font-weight: bold;");
                print("⏺ Recording started (session.wav)...");
            }
        } catch (IOException e) {
            print("❌ Recording Error: " + e.getMessage());
        }
    }

    private void loadFile(File file) {
        try {
            String content = Files.readString(file.toPath());
            editor.setText(content);
            currentFile = file;
            stage.setTitle("🎸 ChucK-Java - " + file.getName());
            print("📂 Loaded: " + file.getAbsolutePath());
        } catch (IOException e) {
            print("❌ Error loading file: " + e.getMessage());
        }
    }

    private void openFile() {
        FileChooser fc = new FileChooser();
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ChucK Files", "*.ck"));
        File file = fc.showOpenDialog(stage);
        if (file != null) loadFile(file);
    }

    private void saveFile() {
        if (currentFile == null) {
            FileChooser fc = new FileChooser();
            fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("ChucK Files", "*.ck"));
            currentFile = fc.showSaveDialog(stage);
        }
        if (currentFile != null) {
            try {
                Files.writeString(currentFile.toPath(), editor.getText());
                print("💾 Saved: " + currentFile.getAbsolutePath());
            } catch (IOException e) {
                print("❌ Error saving file: " + e.getMessage());
            }
        }
    }

    private void print(String text) {
        outputArea.appendText(text + "\n");
    }

    @Override
    public void stop() {
        if (audio != null) audio.stop();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
