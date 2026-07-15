package org.chuck.ide.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class UndoRedoStackTest {
  private UndoRedoStack stack;

  @BeforeEach
  void setUp() {
    stack = new UndoRedoStack(5);
  }

  @Test
  void testPushAndUndoRedo() {
    AtomicInteger val = new AtomicInteger(0);
    Consequence c1 = new PreferenceConsequence<>("test", 0, 10, (k, v) -> val.set(v));
    c1.redo(); // apply
    assertEquals(10, val.get());

    stack.push(c1);
    assertTrue(stack.canUndo());
    assertFalse(stack.canRedo());

    stack.undo();
    assertEquals(0, val.get());
    assertFalse(stack.canUndo());
    assertTrue(stack.canRedo());

    stack.redo();
    assertEquals(10, val.get());
    assertTrue(stack.canUndo());
    assertFalse(stack.canRedo());
  }

  @Test
  void testMaxDepthBounded() {
    for (int i = 0; i < 10; i++) {
      int idx = i;
      stack.push(new PreferenceConsequence<>("k" + i, idx, idx + 1, (k, v) -> {}));
    }
    assertEquals(5, stack.getUndoSize());
  }

  @Test
  void testReplaceLast() {
    Consequence c1 = new PreferenceConsequence<>("gain", 0.5f, 0.6f, (k, v) -> {});
    Consequence c2 = new PreferenceConsequence<>("gain", 0.5f, 0.7f, (k, v) -> {});
    stack.push(c1);
    assertEquals(c1, stack.peekUndo());

    stack.replaceLast(c2);
    assertEquals(1, stack.getUndoSize());
    assertEquals(c2, stack.peekUndo());
  }

  @Test
  void testClear() {
    stack.push(new PreferenceConsequence<>("a", 1, 2, (k, v) -> {}));
    stack.undo();
    stack.clear();
    assertFalse(stack.canUndo());
    assertFalse(stack.canRedo());
    assertEquals(0, stack.getUndoSize());
    assertEquals(0, stack.getRedoSize());
  }
}
