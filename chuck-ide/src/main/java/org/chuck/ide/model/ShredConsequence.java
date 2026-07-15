package org.chuck.ide.model;

import java.util.function.Consumer;

/** Consequence representing the addition or removal of a VM Shred. */
public class ShredConsequence implements Consequence {
  private final int shredId;
  private final String filename;
  private final boolean wasAdded; // true if this action added a shred, false if removed
  private final Consumer<Integer> removeAction;
  private final Consumer<String> addAction;

  public ShredConsequence(
      int shredId,
      String filename,
      boolean wasAdded,
      Consumer<Integer> removeAction,
      Consumer<String> addAction) {
    this.shredId = shredId;
    this.filename = filename;
    this.wasAdded = wasAdded;
    this.removeAction = removeAction;
    this.addAction = addAction;
  }

  @Override
  public void undo() {
    if (wasAdded) {
      if (removeAction != null) removeAction.accept(shredId);
    } else {
      if (addAction != null && filename != null) addAction.accept(filename);
    }
  }

  @Override
  public void redo() {
    if (wasAdded) {
      if (addAction != null && filename != null) addAction.accept(filename);
    } else {
      if (removeAction != null) removeAction.accept(shredId);
    }
  }

  @Override
  public String description() {
    return (wasAdded ? "Add shred " : "Remove shred ")
        + (filename != null ? filename : ("#" + shredId));
  }

  @Override
  public Category category() {
    return Category.SHRED_ACTION;
  }
}
