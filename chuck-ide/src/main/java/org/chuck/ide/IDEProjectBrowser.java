package org.chuck.ide;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.function.Consumer;
import javafx.scene.control.*;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** File browser component for the IDE. */
public class IDEProjectBrowser extends VBox {
  private final TreeView<File> treeView;
  private final Consumer<File> onFileOpen;
  private final Consumer<String> logger;

  public IDEProjectBrowser(File rootDir, Consumer<File> onFileOpen, Consumer<String> logger) {
    this.onFileOpen = onFileOpen;
    this.logger = logger;

    treeView = new TreeView<>(buildTreeItem(rootDir));
    treeView.setShowRoot(false);
    setupTreeView();

    VBox.setVgrow(treeView, Priority.ALWAYS);
    getChildren().add(treeView);
  }

  private void setupTreeView() {
    treeView.setCellFactory(
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
                  refresh.setOnAction(e -> refresh());

                  MenuItem newFile = new MenuItem("New File...");
                  newFile.setOnAction(
                      e -> createNewFile(item.isDirectory() ? item : item.getParentFile()));

                  MenuItem delete = new MenuItem("Delete");
                  delete.setOnAction(e -> deleteFile(item));

                  cm.getItems().addAll(refresh, newFile, new SeparatorMenuItem(), delete);
                  setContextMenu(cm);
                }
              }
            });

    treeView.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2) {
            TreeItem<File> sel = treeView.getSelectionModel().getSelectedItem();
            if (sel != null && sel.getValue().isFile()) {
              onFileOpen.accept(sel.getValue());
            }
          }
        });
  }

  public void refresh() {
    TreeItem<File> root = treeView.getRoot();
    if (root != null) {
      treeView.setRoot(buildTreeItem(root.getValue()));
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
                if (f.delete()) refresh();
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
                if (f.createNewFile()) refresh();
              } catch (IOException e) {
                logger.accept("Error creating file: " + e.getMessage());
              }
            });
  }

  private TreeItem<File> buildTreeItem(File file) {
    TreeItem<File> item = new TreeItem<>(file);
    if (file.isDirectory()) {
      File[] children = file.listFiles();
      if (children != null) {
        Arrays.sort(
            children,
            (f1, f2) -> {
              if (f1.isDirectory() && !f2.isDirectory()) return -1;
              if (!f1.isDirectory() && f2.isDirectory()) return 1;
              return f1.getName().compareToIgnoreCase(f2.getName());
            });
        for (File child : children) {
          if (!child.getName().startsWith(".")) {
            item.getChildren().add(buildTreeItem(child));
          }
        }
      }
    }
    return item;
  }
}
