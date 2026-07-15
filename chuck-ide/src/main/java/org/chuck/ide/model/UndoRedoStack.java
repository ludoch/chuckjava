package org.chuck.ide.model;

import java.util.ArrayDeque;
import java.util.Deque;

/** Bounded undo/redo stack for {@link Consequence} objects. */
public class UndoRedoStack {
  private final int maxDepth;
  private final Deque<Consequence> undoStack = new ArrayDeque<>();
  private final Deque<Consequence> redoStack = new ArrayDeque<>();

  public UndoRedoStack(int maxDepth) {
    if (maxDepth <= 0) throw new IllegalArgumentException("maxDepth must be > 0");
    this.maxDepth = maxDepth;
  }

  /**
   * Pushes a new consequence onto the undo stack and clears the redo stack. If the stack exceeds
   * maxDepth, the oldest item is dropped.
   */
  public void push(Consequence consequence) {
    if (consequence == null) return;
    undoStack.push(consequence);
    while (undoStack.size() > maxDepth) {
      undoStack.removeLast();
    }
    redoStack.clear();
  }

  public boolean canUndo() {
    return !undoStack.isEmpty();
  }

  public boolean canRedo() {
    return !redoStack.isEmpty();
  }

  /**
   * Undoes the top consequence on the undo stack and pushes it to the redo stack.
   *
   * @return the undone consequence, or null if nothing to undo.
   */
  public Consequence undo() {
    if (undoStack.isEmpty()) return null;
    Consequence c = undoStack.pop();
    c.undo();
    redoStack.push(c);
    return c;
  }

  /**
   * Redoes the top consequence on the redo stack and pushes it back to the undo stack.
   *
   * @return the redone consequence, or null if nothing to redo.
   */
  public Consequence redo() {
    if (redoStack.isEmpty()) return null;
    Consequence c = redoStack.pop();
    c.redo();
    undoStack.push(c);
    return c;
  }

  public Consequence peekUndo() {
    return undoStack.peek();
  }

  public Consequence peekRedo() {
    return redoStack.peek();
  }

  /**
   * Replaces the top of the undo stack with the new consequence (useful for coalescing slider
   * events).
   */
  public void replaceLast(Consequence consequence) {
    if (consequence == null) return;
    if (!undoStack.isEmpty()) {
      undoStack.pop();
    }
    push(consequence);
  }

  public void clear() {
    undoStack.clear();
    redoStack.clear();
  }

  public int getUndoSize() {
    return undoStack.size();
  }

  public int getRedoSize() {
    return redoStack.size();
  }
}
