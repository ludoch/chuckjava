package org.chuck.ide.model;

/**
 * A single undoable action or mutation within the ChucK-Java IDE or VM.
 *
 * <p>Implements the Command/Memento pattern to capture the exact delta needed to reverse or
 * re-apply an action.
 */
public interface Consequence {
  /** Reverse this mutation. */
  void undo();

  /** Re-apply this mutation. */
  void redo();

  /** Human-readable summary (shown in undo/redo tooltip or status bar). */
  String description();

  /** Category tag for grouping or coalescing. */
  Category category();

  /** Categories of undoable actions in the IDE. */
  enum Category {
    SHRED_ACTION,
    PREFERENCE_CHANGE,
    MIDI_MAPPING,
    EDITOR_TEXT,
    CUSTOM
  }
}
