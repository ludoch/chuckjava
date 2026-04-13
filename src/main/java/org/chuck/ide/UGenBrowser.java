package org.chuck.ide;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import org.chuck.core.ChuckDocGenerator;
import org.chuck.core.UGenRegistry;
import org.chuck.core.doc;

/**
 * A browser for all registered Unit Generators, showing their hierarchy and documentation. Allows
 * double-clicking to insert a snippet into the editor.
 */
public class UGenBrowser extends VBox {

  private final TreeView<String> treeView;
  private final TextArea docArea;
  private Consumer<String> onInsert;

  public UGenBrowser() {
    setPadding(new Insets(8));
    setSpacing(5);
    setStyle("-fx-background-color: #f8f8f8; -fx-border-color: #ccc; -fx-border-width: 0 1 0 0;");

    Label title = new Label("Unit Generators");
    title.setStyle("-fx-font-weight: bold; -fx-text-fill: #333; -fx-font-size: 13;");

    treeView = new TreeView<>();
    treeView.setShowRoot(false);
    VBox.setVgrow(treeView, Priority.ALWAYS);
    treeView.setStyle("-fx-background-insets: 0;");

    docArea = new TextArea();
    docArea.setEditable(false);
    docArea.setWrapText(true);
    docArea.setPrefHeight(180);
    docArea.setStyle(
        "-fx-font-family: 'Monospaced'; -fx-font-size: 11; -fx-control-inner-background: #eee;");

    Label docLabel = new Label("Documentation");
    docLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #555; -fx-font-size: 11;");

    getChildren().addAll(title, treeView, docLabel, docArea);

    populateTree();

    treeView
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (obs, old, val) -> {
              if (val != null && val.isLeaf()) {
                updateDoc(val.getValue());
              }
            });

    treeView.setOnMouseClicked(
        event -> {
          if (event.getClickCount() == 2) {
            TreeItem<String> selected = treeView.getSelectionModel().getSelectedItem();
            if (selected != null && selected.isLeaf() && onInsert != null) {
              onInsert.accept(selected.getValue() + " s => dac;");
            }
          }
        });
  }

  /** Set the listener for double-click insertion. */
  public void setOnInsert(Consumer<String> onInsert) {
    this.onInsert = onInsert;
  }

  private void populateTree() {
    TreeItem<String> root = new TreeItem<>("Root");

    TreeItem<String> oscNode = new TreeItem<>("Oscillators");
    TreeItem<String> filterNode = new TreeItem<>("Filters");
    TreeItem<String> effectNode = new TreeItem<>("Effects");
    TreeItem<String> stkNode = new TreeItem<>("STK Instruments");
    TreeItem<String> anaNode = new TreeItem<>("Analyzers");
    TreeItem<String> utilNode = new TreeItem<>("Utilities");

    root.getChildren().addAll(oscNode, filterNode, effectNode, stkNode, anaNode, utilNode);

    Set<String> names = UGenRegistry.getRegisteredNames();
    List<String> sortedNames = new ArrayList<>(names);
    Collections.sort(sortedNames);

    for (String name : sortedNames) {
      String category = categorize(name);
      TreeItem<String> item = new TreeItem<>(name);
      switch (category) {
        case "Oscillators" -> oscNode.getChildren().add(item);
        case "Filters" -> filterNode.getChildren().add(item);
        case "Effects" -> effectNode.getChildren().add(item);
        case "STK Instruments" -> stkNode.getChildren().add(item);
        case "Analyzers" -> anaNode.getChildren().add(item);
        default -> utilNode.getChildren().add(item);
      }
    }

    // Remove empty categories and expand all
    root.getChildren().removeIf(node -> node.getChildren().isEmpty());
    for (TreeItem<String> node : root.getChildren()) {
      node.setExpanded(false); // Keep collapsed by default for tidiness
    }

    treeView.setRoot(root);
  }

  private String categorize(String name) {
    if (containsAny(name, "Osc", "Phasor", "Blit", "Noise", "Impulse", "Step"))
      return "Oscillators";
    if (containsAny(name, "Filter", "LPF", "HPF", "BPF", "BRF", "ResonZ", "Pole", "Zero", "BiQuad"))
      return "Filters";
    if (containsAny(name, "FFT", "IFFT", "RMS", "Flux", "Centroid", "Rolloff")) return "Analyzers";
    if (containsAny(name, "Echo", "Delay", "Chorus", "Rev", "FreeVerb", "PitShift", "Pan2", "Gain"))
      return "Effects";

    // STK - heuristic: common STK instrument names
    if (containsAny(
        name,
        "Clarinet",
        "Mandolin",
        "Plucked",
        "Guitar",
        "Twang",
        "Rhodey",
        "Wurley",
        "TubeBell",
        "BeeThree",
        "FMVoices",
        "HevyMetl",
        "PercFlut",
        "Moog",
        "Saxofony",
        "Flute",
        "Brass",
        "Sitar",
        "StifKarp",
        "Shakers",
        "VoicForm",
        "ModalBar",
        "BandedWG",
        "BlowBotl",
        "BlowHole")) return "STK Instruments";

    return "Utilities";
  }

  private boolean containsAny(String name, String... searchStrings) {
    for (String s : searchStrings) {
      if (name.contains(s)) return true;
    }
    return false;
  }

  private void updateDoc(String name) {
    Class<?> clazz = ChuckDocGenerator.findClass(name);
    if (clazz == null) {
      docArea.setText("No rich documentation available for " + name);
      return;
    }

    StringBuilder sb = new StringBuilder();
    sb.append("Class: ").append(clazz.getSimpleName()).append("\n");

    doc classDoc = clazz.getAnnotation(doc.class);
    if (classDoc != null) {
      sb.append("Description: ").append(classDoc.value()).append("\n\n");
    } else {
      sb.append("\n");
    }

    // Fields (parameters)
    List<Field> fields = new ArrayList<>();
    for (Field f : clazz.getDeclaredFields()) {
      if (!Modifier.isStatic(f.getModifiers()) && !f.getName().equals("sampleRate")) {
        fields.add(f);
      }
    }

    if (!fields.isEmpty()) {
      sb.append("Parameters:\n");
      for (Field f : fields) {
        sb.append("  . ")
            .append(f.getName())
            .append(" (")
            .append(f.getType().getSimpleName())
            .append(")\n");
      }
      sb.append("\n");
    }

    // Methods
    List<Method> methods = new ArrayList<>();
    for (Method m : clazz.getMethods()) {
      if (m.getDeclaringClass() == clazz
          || m.getDeclaringClass().getSimpleName().contains("Osc")
          || m.getDeclaringClass().getSimpleName().contains("Filter")) {
        if (!Modifier.isStatic(m.getModifiers())
            && !m.getName().equals("tick")
            && !m.getName().equals("compute")
            && !m.getName().equals("chuckTo")
            && !m.getName().equals("unchuck")) {
          methods.add(m);
        }
      }
    }

    if (!methods.isEmpty()) {
      sb.append("Methods:\n");
      for (Method m : methods) {
        doc methodDoc = m.getAnnotation(doc.class);
        sb.append("  . ").append(m.getName()).append("(");
        Class<?>[] params = m.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
          sb.append(params[i].getSimpleName()).append(i < params.length - 1 ? ", " : "");
        }
        sb.append(") -> ").append(m.getReturnType().getSimpleName());
        if (methodDoc != null) {
          sb.append("\n    : ").append(methodDoc.value());
        }
        sb.append("\n");
      }
    }

    docArea.setText(sb.toString());
    docArea.setScrollTop(0);
  }
}
