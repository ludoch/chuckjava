package org.chuck.ide.model;

import java.util.function.BiConsumer;

/** Consequence representing a configuration or preference change in the IDE. */
public class PreferenceConsequence<T> implements Consequence {
  private final String prefKey;
  private final T oldValue;
  private final T newValue;
  private final BiConsumer<String, T> setter;

  public PreferenceConsequence(
      String prefKey, T oldValue, T newValue, BiConsumer<String, T> setter) {
    this.prefKey = prefKey;
    this.oldValue = oldValue;
    this.newValue = newValue;
    this.setter = setter;
  }

  @Override
  public void undo() {
    if (setter != null) setter.accept(prefKey, oldValue);
  }

  @Override
  public void redo() {
    if (setter != null) setter.accept(prefKey, newValue);
  }

  @Override
  public String description() {
    return "Change setting: " + prefKey + " to " + newValue;
  }

  @Override
  public Category category() {
    return Category.PREFERENCE_CHANGE;
  }
}
