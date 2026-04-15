package org.chuck.ide;

import java.io.File;
import java.time.Duration;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Rectangle;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.LineNumberFactory;
import org.fxmisc.richtext.event.MouseOverTextEvent;

/** A single editor tab in the IDE. */
public class EditorTab extends Tab {
  private final CodeArea editor = new CodeArea();
  private final TextField argsField = new TextField();
  private final StackPane editorWrapper;
  private final Rectangle flashRect;
  private final EditorSupport support;
  private final StatusBar statusBar;

  private File file;
  private String savedText;
  private int lastSporkedShredId = -1;

  public EditorTab(
      String title,
      String content,
      EditorSupport support,
      StatusBar statusBar,
      int fontSize,
      boolean smartIndent,
      boolean useSpaces,
      int tabWidth) {
    super(title);
    this.support = support;
    this.statusBar = statusBar;
    this.savedText = content;

    editor.setParagraphGraphicFactory(LineNumberFactory.get(editor));
    editor.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: " + fontSize + ";");
    editor.replaceText(0, 0, content);

    editor
        .multiPlainChanges()
        .successionEnds(Duration.ofMillis(50))
        .subscribe(
            ignore -> editor.setStyleSpans(0, support.computeHighlighting(editor.getText())));
    editor.setStyleSpans(0, support.computeHighlighting(editor.getText()));

    // Caret position status
    editor
        .caretPositionProperty()
        .addListener(
            (obs, oldPos, newPos) -> {
              int line = editor.getCurrentParagraph() + 1;
              int col = editor.getCaretColumn() + 1;
              statusBar.setCaretPosition(line, col);
            });

    setupEventHandlers(smartIndent, useSpaces, tabWidth);

    flashRect = new Rectangle();
    flashRect.setMouseTransparent(true);
    flashRect.setOpacity(0);
    flashRect.widthProperty().bind(editor.widthProperty());
    flashRect.heightProperty().bind(editor.heightProperty());
    editorWrapper = new StackPane(editor, flashRect);

    argsField.setPromptText("script arguments  (space-separated, accessible via me.arg(0)…) ");
    argsField.setStyle("-fx-font-family: 'Monospaced'; -fx-font-size: 11;");
    Label argsLabel = new Label(" Args:");
    argsLabel.setStyle("-fx-font-size: 11; -fx-text-fill: #555;");
    HBox argsBar = new HBox(4, argsLabel, argsField);
    argsBar.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
    argsBar.setPadding(new Insets(2, 6, 2, 4));
    argsBar.setStyle(
        "-fx-background-color: #f5f5f5; -fx-border-color: #ccc; -fx-border-width: 1 0 0 0;");
    HBox.setHgrow(argsField, Priority.ALWAYS);

    VBox contentBox = new VBox(editorWrapper, argsBar);
    VBox.setVgrow(editorWrapper, Priority.ALWAYS);
    setContent(contentBox);

    // Dirty tracking
    editor
        .plainTextChanges()
        .subscribe(
            ch -> {
              boolean dirty = !editor.getText().equals(savedText);
              String base = getText().replaceFirst("^\\* ?", "");
              String wanted = dirty ? "* " + base : base;
              if (!getText().equals(wanted)) setText(wanted);
            });
  }

  private void setupEventHandlers(boolean smartIndent, boolean useSpaces, int tabWidth) {
    editor.addEventHandler(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.SPACE && e.isControlDown()) {
            e.consume();
            support.showCompletionPopup(editor);
          }
        });

    editor.addEventHandler(
        KeyEvent.KEY_TYPED,
        e -> {
          String ch = e.getCharacter();
          if (".".equals(ch) || ":".equals(ch)) {
            if (":".equals(ch)) {
              int pos = editor.getCaretPosition();
              if (pos >= 2 && "::".equals(editor.getText(pos - 2, pos))) {
                Platform.runLater(() -> support.showCompletionPopup(editor));
              }
            } else {
              Platform.runLater(() -> support.showCompletionPopup(editor));
            }
          } else if ("(".equals(ch)) {
            Platform.runLater(() -> support.showParamHint(editor));
          }
        });

    editor.addEventHandler(
        MouseOverTextEvent.MOUSE_OVER_TEXT_BEGIN,
        e -> {
          int charIdx = e.getCharacterIndex();
          String text = editor.getText();
          String word = support.extractWordAt(text, charIdx);
          if (word.isEmpty()) return;
          String docText = support.lookupDoc(word, null);
          if (docText != null) support.showDocHoverPopup(docText, e.getScreenPosition());
        });
    editor.addEventHandler(
        MouseOverTextEvent.MOUSE_OVER_TEXT_END, e -> support.hideDocHoverPopup());

    editor.addEventFilter(
        KeyEvent.KEY_PRESSED,
        e -> {
          if (e.getCode() == KeyCode.TAB && !e.isControlDown()) {
            e.consume();
            String ins = useSpaces ? " ".repeat(tabWidth) : "\t";
            editor.insertText(editor.getCaretPosition(), ins);
          }
        });
  }

  public CodeArea getEditor() {
    return editor;
  }

  public String getArguments() {
    return argsField.getText();
  }

  public File getFile() {
    return file;
  }

  public void setFile(File file) {
    this.file = file;
    if (file != null) setText(file.getName());
  }

  public boolean isDirty() {
    return !editor.getText().equals(savedText);
  }

  public void setSaved() {
    this.savedText = editor.getText();
    setText(getText().replaceFirst("^\\* ?", ""));
  }

  public int getLastSporkedShredId() {
    return lastSporkedShredId;
  }

  public void setLastSporkedShredId(int id) {
    this.lastSporkedShredId = id;
  }

  public StackPane getEditorWrapper() {
    return editorWrapper;
  }

  public Rectangle getFlashRect() {
    return flashRect;
  }
}
