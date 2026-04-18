package org.chuck.ide;

import java.util.*;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Popup;
import javafx.stage.Stage;
import org.chuck.core.doc;
import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/** Helper class for editor-related logic: syntax highlighting, code completion, and doc lookups. */
public class EditorSupport {

  public record UserSymbol(String name, String type, String signature) {}

  public record CompItem(String insertText, String label, String kind) {}

  private static final List<String> TYPE_CANDIDATES =
      List.of(
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
          "Event",
          "Object",
          "UGen",
          "UAna",
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
          "ADSR",
          "Adsr",
          "Envelope",
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
          "KNN",
          "KNN2",
          "SVM",
          "MLP",
          "HMM",
          "PCA",
          "Word2Vec",
          "Wekinator",
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

  private static final String KEYWORD_PATTERN =
      "\\b(if|else|while|for|repeat|return|break|continue|new|spork|fun|class|extends|public|private|static|void|abstract|interface|loop|do|until|auto|switch|case|default)\\b";
  private static final String TYPE_PATTERN =
      "\\b(int|float|dur|time|string|complex|polar|vec2|vec3|vec4|SinOsc|SawOsc|TriOsc|SqrOsc|PulseOsc|Phasor|Noise|Impulse|Step|BlitSaw|BlitSquare|Blit|LPF|HPF|BPF|BRF|ResonZ|BiQuad|OnePole|OneZero|TwoPole|TwoZero|PoleZero|Echo|Delay|DelayL|DelayA|DelayP|Chorus|JCRev|AllPass|Pan2|Gain|ADSR|Adsr|Envelope|Mandolin|Clarinet|Plucked|Rhodey|Wurley|BeeThree|HevyMetl|PercFlut|TubeBell|FMVoices|Bowed|StifKarp|Moog|Flute|Sitar|Brass|Saxofony|Shakers|ModalBar|VoicForm|FFT|IFFT|DCT|IDCT|ZCR|RMS|Centroid|MFCC|SFM|Kurtosis|AutoCorr|XCorr|Chroma|FeatureCollector|SndBuf|WvOut|LiSa|MidiIn|MidiMsg|MidiFileIn|OscIn|OscOut|OscMsg|OscEvent|Hid|HidMsg|HidOut|IO|FileIO|StringTokenizer|ConsoleInput|KBHit|SerialIO|KNN|KNN2|SVM|MLP|HMM|PCA|Bitcrusher|FoldbackSaturator|Overdrive|MagicSine|ExpEnv|PowerADSR|WPDiodeLadder|WPKorg35|Range|FIR|KasFilter|WinFuncEnv|ExpDelay|Perlin|Event|Object|UGen|UAna)\\b";
  private static final String BUILTIN_PATTERN =
      "\\b(dac|adc|blackhole|now|second|ms|samp|minute|hour|day|week|Std|Math|Machine|me|chout|cherr|newline)\\b";
  private static final String BOOLEAN_PATTERN = "\\b(true|false|null|maybe|pi|e|sqrt2)\\b";
  private static final String ANNOTATION_PATTERN =
      "@(doc|operator|construct|destruct|this|return)\\b";
  private static final String NUMBER_PATTERN = "\\b\\d+(\\.\\d+)?\\b";
  private static final String STRING_PATTERN = "\"[^\"\\\\]*(\\\\.[^\"\\\\]*)*\"";
  private static final String COMMENT_PATTERN = "//[^\n]*|/\\*(?!\\*).*?\\*/";
  private static final String DOC_PATTERN = "/\\*\\*.*?\\*/";
  private static final String CHUCK_OP_PATTERN = "=>|@=>|::|<=>|!=>";

  private static final Pattern HIGHLIGHT_PATTERN =
      Pattern.compile(
          "(?<DOC>"
              + DOC_PATTERN
              + ")"
              + "|(?<COMMENT>"
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

  private final Preferences prefs;
  private final Stage stage;
  private Popup currentPopup;
  private Popup docHoverPopup;

  public EditorSupport(Preferences prefs, Stage stage) {
    this.prefs = prefs;
    this.stage = stage;
  }

  public StyleSpans<Collection<String>> computeHighlighting(String text) {
    Matcher m = HIGHLIGHT_PATTERN.matcher(text);
    StyleSpansBuilder<Collection<String>> spansBuilder = new StyleSpansBuilder<>();
    int lastEnd = 0;
    while (m.find()) {
      String styleClass =
          m.group("DOC") != null
              ? "ck-doc"
              : m.group("COMMENT") != null
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

  public String generateSyntaxCss() {
    return String.format(
        ".ck-doc       { -fx-fill: %s; -fx-font-style: italic; }"
            + ".ck-comment    { -fx-fill: %s; }"
            + ".ck-annotation { -fx-fill: %s; -fx-font-style: italic; }"
            + ".ck-keyword    { -fx-fill: %s; -fx-font-weight: bold; }"
            + ".ck-type       { -fx-fill: %s; -fx-font-weight: bold; }"
            + ".ck-builtin    { -fx-fill: %s; -fx-font-weight: bold; }"
            + ".ck-boolean    { -fx-fill: %s; -fx-font-weight: bold; }"
            + ".ck-string     { -fx-fill: %s; }"
            + ".ck-number     { -fx-fill: %s; }"
            + ".ck-chuckop    { -fx-fill: %s; -fx-font-weight: bold; }"
            + ".code-area, .virtual-indexed-cell { -fx-background-color: %s; }",
        prefs.get("color.doc", "#008000"),
        prefs.get("color.comment", "#008000"),
        prefs.get("color.annotation", "#800080"),
        prefs.get("color.keyword", "#0000FF"),
        prefs.get("color.type", "#2B91AF"),
        prefs.get("color.builtin", "#000080"),
        prefs.get("color.boolean", "#A52A2A"),
        prefs.get("color.string", "#A31515"),
        prefs.get("color.number", "#098658"),
        prefs.get("color.chuckop", "#000000"),
        prefs.get("color.background", "#FFFFFF"));
  }

  public void showCompletionPopup(CodeArea editor) {
    if (currentPopup != null) {
      currentPopup.hide();
      currentPopup = null;
    }

    int caretPos = editor.getCaretPosition();
    String textBefore = editor.getText(0, caretPos);

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
      int colIdx = prefix.lastIndexOf("::");
      filter = prefix.substring(colIdx + 2);
      candidates =
          DURATION_CANDIDATES.stream()
              .map(d -> new CompItem(d, d, "dur"))
              .collect(Collectors.toList());
    } else if (prefix.contains(".")) {
      int dotIdx = prefix.lastIndexOf('.');
      String varName = prefix.substring(0, dotIdx);
      filter = prefix.substring(dotIdx + 1);
      resolvedType = resolveVariableType(varName, textBefore);
      candidates = getDynamicMemberCompItems(resolvedType);
    } else {
      candidates = new ArrayList<>();
      for (String name : TYPE_CANDIDATES) {
        candidates.add(new CompItem(name, name, kindOf(name)));
      }
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
            .sorted(Comparator.comparing(CompItem::insertText))
            .collect(Collectors.toList());

    if (matches.isEmpty()) return;

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
                String[] lines = rawDoc.split("\n", 2);
                docTitle.setText(lines[0]);
                if (lines.length > 1) {
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
            }
          } else if (ke.getCode() == KeyCode.ESCAPE) {
            popup.hide();
          }
        });

    listView.setOnMouseClicked(
        me -> {
          if (me.getClickCount() == 2) {
            CompItem sel = listView.getSelectionModel().getSelectedItem();
            if (sel != null) {
              complete(editor, sel.insertText(), finalFilter);
              popup.hide();
            }
          }
        });

    editor.getCaretBounds().ifPresent(b -> popup.show(editor, b.getMaxX(), b.getMaxY()));
    listView.requestFocus();
    listView.getSelectionModel().select(0);
  }

  private void complete(CodeArea editor, String completion, String filter) {
    if (completion == null) return;
    int caret = editor.getCaretPosition();
    editor.replaceText(caret - filter.length(), caret, completion);
  }

  public String lookupDoc(String typeName, String memberName) {
    if (typeName == null || typeName.isEmpty()) return null;
    Class<?> cls = resolveChuckClass(typeName);
    if (cls == null) return null;

    if (memberName == null || memberName.isEmpty()) {
      doc classDoc = cls.getAnnotation(doc.class);
      if (classDoc != null) return typeName + "\n" + classDoc.value();
      String parent = cls.getSuperclass() != null ? cls.getSuperclass().getSimpleName() : "Object";
      return typeName + " (" + parent + ")";
    }

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

  public void showDocHoverPopup(String text, javafx.geometry.Point2D pos) {
    hideDocHoverPopup();
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

  public void hideDocHoverPopup() {
    if (docHoverPopup != null) {
      docHoverPopup.hide();
      docHoverPopup = null;
    }
  }

  public void showParamHint(CodeArea editor) {
    int caret = editor.getCaretPosition();
    if (caret < 2) return;
    String textBefore = editor.getText(0, caret - 1);
    String word = extractWordAt(textBefore, textBefore.length());
    if (word.isEmpty()) return;

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

  public String extractWordAt(String text, int pos) {
    if (text == null || pos < 0 || pos > text.length()) return "";
    int end = pos;
    while (end < text.length() && isWordChar(text.charAt(end))) end++;
    int start = pos;
    while (start > 0 && isWordChar(text.charAt(start - 1))) start--;
    return text.substring(start, end);
  }

  public String resolveVariableType(String varName, String textBefore) {
    Pattern p = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\s+" + Pattern.quote(varName) + "\\b");
    Matcher m = p.matcher(textBefore);
    String lastFound = "UGen";
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
    LinkedHashSet<String> seen = new LinkedHashSet<>();
    Set<String> setterProps = new HashSet<>();
    for (java.lang.reflect.Method m : cls.getMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
      String n = m.getName();
      if (n.startsWith("set") && n.length() > 3) {
        String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
        setterProps.add(prop);
        seen.add(prop);
      }
    }
    for (java.lang.reflect.Method m : cls.getMethods()) {
      if (!java.lang.reflect.Modifier.isPublic(m.getModifiers())) continue;
      String n = m.getName();
      if (objectMethods.contains(n) || n.startsWith("set")) continue;
      if (n.startsWith("get") && n.length() > 3) {
        String prop = Character.toLowerCase(n.charAt(3)) + n.substring(4);
        if (setterProps.contains(prop)) continue;
      }
      seen.add(n);
    }
    return new ArrayList<>(seen);
  }

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
    if (!"void".equals(ret)) sb.append(" \u2192 ").append(ret);
    return sb.toString();
  }

  public List<UserSymbol> scanUserSymbols(String code) {
    List<UserSymbol> symbols = new ArrayList<>();
    Set<String> builtinKeywords =
        Set.of("if", "else", "while", "for", "return", "new", "fun", "class", "spork", "repeat");
    Matcher m = Pattern.compile("\\b([A-Z][a-zA-Z0-9]*)\\s+([a-z_][a-zA-Z0-9_]*)\\b").matcher(code);
    while (m.find()) {
      String type = m.group(1), name = m.group(2);
      if (!builtinKeywords.contains(name))
        symbols.add(new UserSymbol(name, type, type + " " + name));
    }
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

  private boolean isWordChar(char c) {
    return Character.isLetterOrDigit(c) || c == '_';
  }

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
      case "ADSR", "Adsr", "Envelope", "ExpEnv", "PowerADSR" -> "env";
      case "Bitcrusher",
          "FoldbackSaturator",
          "Overdrive",
          "MagicSine",
          "WPDiodeLadder",
          "WPKorg35",
          "Range",
          "FIR",
          "KasFilter",
          "WinFuncEnv",
          "ExpDelay",
          "Perlin" ->
          "chugin";
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
      case "KNN", "KNN2", "SVM", "MLP", "HMM", "PCA", "Word2Vec", "Wekinator" -> "ai";
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
}
