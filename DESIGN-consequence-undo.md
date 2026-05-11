# Consequence/Undo System Design

## Overview

Design an undo system for the Deluge emulator that wraps every model mutation in a "Consequence" object -- a structured `UndoableAction` that captures the before/after state needed to reverse the operation. The existing `UndoRedoStack` (class + inner interface) is already defined but orphaned; this design wires it in, introduces the Consequence abstraction, registers keyboard shortcuts, and covers every mutation category.

---

## 1. The Consequence Abstraction

Replace the bare `UndoableAction` with a richer sealed interface / abstract class that captures state deltas rather than requiring hand-written undo/redo logic everywhere.

### `Consequence.java` (new file, `model/Consequence.java`)

```java
package org.chuck.deluge.model;

/** A single undoable mutation to the project model. */
public interface Consequence {
  /** Reverse this mutation. */
  void undo();
  /** Re-apply this mutation. */
  void redo();
  /** Human-readable summary (shown in undo/redo tooltip or status bar). */
  String description();
  /** Category tag for grouping/coalescing. */
  Category category();

  enum Category {
    STEP,           // individual step toggle in grid
    AUTOMATION,     // automation point change
    CLIP_STRUCT,    // add/delete/duplicate/rename clip
    TRACK_STRUCT,   // add/delete/reorder/rename track
    SYNTH_PARAM,    // synth/kit parameter slider change
    PROJECT_PARAM,  // BPM, swing, master volume, etc.
    PATTERN_LOAD,   // pattern snapshot application
  }
}
```

### Concrete implementations

Each concrete class captures only the data needed to reverse its specific mutation. Most follow the Memento pattern: save the old value before mutation, the new value after.

**A. `StepConsequence`** -- for grid pad toggles

```java
public record StepConsequence(
    int trackIndex,
    int clipIndex,
    int row,
    int step,
    StepData oldData,
    StepData newData
) implements Consequence {
  public void undo() { /* set track.clips[clipIndex].setStep(row, step, oldData) */ }
  public void redo() { /* set track.clips[clipIndex].setStep(row, step, newData) */ }
  public String description() { return "Toggle step " + (step+1) + ":" + (row+1); }
  public Category category() { return STEP; }
}
```

**B. `AutomationConsequence`** -- for automation point set/clear

```java
public record AutomationConsequence(
    int trackIndex,
    int clipIndex,
    String paramName,
    int step,
    float oldValue,   // -1f means "no automation"
    float newValue
) implements Consequence { ... }
```

**C. `TrackStructureConsequence`** -- for add/delete/reorder track. Uses a memento of the full `TrackModel` to snapshot state at the time of removal.

```java
public record TrackStructureConsequence(
    int operation,  // ADD, REMOVE, MOVE_UP, MOVE_DOWN
    int index,
    TrackModel trackSnapshot,  // null for ADD (it already exists in the list)
    String description
) implements Consequence { ... }
```

**D. `ClipStructureConsequence`** -- for add/delete/duplicate/rename clip

```java
public record ClipStructureConsequence(
    int trackIndex,
    int clipIndex,
    int operation,  // ADD, REMOVE, DUPLICATE, RENAME
    ClipModel clipSnapshot,
    String previousName,  // for RENAME
    String newName
) implements Consequence { ... }
```

**E. `SynthParamConsequence`** -- for a single synth/kit parameter slider change. With 300ms coalescing: if the last consequence is also a SynthParamConsequence for the same track+param within 300ms, merge (update newValue) instead of pushing a new entry.

```java
public record SynthParamConsequence(
    int trackIndex,
    String paramName,
    float oldValue,
    float newValue,
    long timestamp
) implements Consequence { ... }
```

**F. `ProjectParamConsequence`** -- for BPM, swing, master volume, reverb, delay, sidechain, compressor, songParams.

```java
public record ProjectParamConsequence(
    String paramName,
    float oldValue,
    float newValue
) implements Consequence { ... }
```

**G. `PatternApplyConsequence`** -- for pattern snapshot application (destructive clip overwrite). Captures the full clip state before the apply so it can be restored.

```java
public record PatternApplyConsequence(
    int trackIndex,
    int clipIndex,
    ClipModel.ClipSnapshot beforeSnapshot,
    ClipModel.ClipSnapshot afterSnapshot
) implements Consequence { ... }
```

---

## 2. Where the UndoRedoStack Lives

Single instance held in `ProjectModel`:

```java
public class ProjectModel {
  private final UndoRedoStack undoRedoStack = new UndoRedoStack(64);

  public UndoRedoStack getUndoRedoStack() { return undoRedoStack; }
}
```

`SwingDelugeApp` accesses it via `currentProject.getUndoRedoStack()`.

The `UndoRedoStack.undoableAction` inner interface should be refactored to just use the `Consequence` interface directly. The `push(Consequence)` method calls `delegate.push(action)` on the inner `UndoRedoStack`. Or simpler: replace `UndoRedoStack.UndoableAction` entirely with `Consequence`.

---

## 3. Wiring into UI Mutation Points

### 3a. SwingGridPanel -- Step toggles (HIGHEST FREQUENCY)

All four pad click handlers (lines ~916-990, ~2035-2100, for kit/synth/MIDI tracks) follow the same dual-write pattern:

```java
// BEFORE
boolean stepState = bridge.getStep(engineRow, col);
bridge.setStep(engineRow, col, !stepState);
cModel.setStep(row, col, new StepData(...));

// AFTER -- wrap in undo
StepData oldStep = cModel.getStep(row, col);
bridge.setStep(engineRow, col, !stepState);
cModel.setStep(row, col, new StepData(...));
pushConsequence(new StepConsequence(trackIdx, clipIdx, row, col, oldStep, cModel.getStep(row, col)));
```

Each of the 4 click handlers (kit row in first builder, synth row in first builder, MIDI in second builder, kit/synth in second builder) needs the same treatment. A helper method should encapsulate the push:

```java
private void pushStepEdit(int modelRow, int col, StepData oldStep, StepData newStep) {
  if (projectModel == null) return;
  int tIdx = editedModelTrack;
  if (tIdx >= projectModel.getTracks().size()) return;
  int cIdx = projectModel.getTracks().get(tIdx).getActiveClipIndex();
  projectModel.getUndoRedoStack().push(
    new StepConsequence(tIdx, cIdx, modelRow, col, oldStep, newStep));
}
```

**Coalescing for step toggles**: Not needed (each toggle is intentional), but rapid clicking of the same pad could coalesce via the same mechanism -- if the last consequence matches track+clip+row+col, pop it before pushing the new one (treat toggle as state flip, not history entry).

### 3b. SwingGridPanel -- Automation editing (HIGH FREQUENCY)

Three automation edit sites:
- Detail editor `mousePressed` (line 2457): `cM.setAutomation(finalParam, colIdx, val)`
- Detail editor `mouseDragged` (line 2481): `cM.setAutomation(finalParam, colIdx, val)`
- Overview editor `mousePressed` (line 2641): `cM.setAutomation(fParam, colIdx, val)`
- Overview editor `mouseDragged` (line 2659): `cM.setAutomation(fParam, colIdx, val)`

Each needs before/after capture:

```java
float oldVal = cM.getAutomation(finalParam, colIdx);
cM.setAutomation(finalParam, colIdx, val);
projectModel.getUndoRedoStack().push(
  new AutomationConsequence(tIdx, cIdx, finalParam, colIdx, oldVal, val));
```

**Coalescing**: The drag handlers fire many events per gesture. Wrap with a flag: on `mousePressed`, capture old value and set a `dirty` flag; on `mouseReleased`, push a single `AutomationConsequence` using the pre-drag old value and final value (end of drag). This avoids flooding the stack with 50+ entries per drag.

### 3c. SwingGridPanel -- Step properties (velocity/probability)

Right-click `StepPropertiesDialog` handler (lines ~891-910, ~2010-2030):

```java
StepData oldStep = cModel.getStep(modelRow, activeCol);
bridge.setVelocity(engineRow, activeCol, newVel / 100.0);
cModel.setStep(modelRow, activeCol, new StepData(st, newVel/100.0f, 0.5f, (float)prob, 0));
pushStepEdit(modelRow, activeCol, oldStep, cModel.getStep(modelRow, activeCol));
```

### 3d. SwingGridPanel -- Row clear (shift+click MUTE) and mute/solo

Shift+MUTE row clear (lines 815, 1933): Clear all steps. Push a batch `Consequence` or push individual `StepConsequence` wrapped in a `CompoundConsequence` (see section 4).

Mute/solo toggles (lines 822, 1940, 837-842): Lower priority -- mute/solo are transient playback state. Decide: make undoable or not. Recommendation: skip for now; mute/solo are not persisted in the model and are less critical.

### 3e. SwingGridPanel -- Context menus (rename, recolor, move track)

These are tracked in `SwingGridPanel`'s JPopupMenu handlers (not shown in the snippets above but inferred from the summary -- track name, colour, move up/down, remove track, clip name, clip play mode, add/remove clip). Each creates the appropriate `Consequence`:

```java
// Before rename
String oldName = track.getName();
track.setName(newName);
pushConsequence(new ClipStructureConsequence(tIdx, cIdx, RENAME, null, oldName, newName));
```

### 3f. SwingSynthConfigDialog -- All slider/combo changes (MEDIUM FREQUENCY)

Every slider and combo action listener writes directly to the model + bridge. Each needs a before/after snapshot. For sliders using `addSlider()`:

```java
// BEFORE:
val -> { model.setFilterDrive(val / 100.0f); bridge.setFilterDrive(trackIndex, val / 100.0f); }

// AFTER:
val -> {
  float oldVal = model.getFilterDrive();
  model.setFilterDrive(val / 100.0f);
  bridge.setFilterDrive(trackIndex, val / 100.0f);
  pushParamConsequence(trackIndex, "filterDrive", oldVal, val/100.0f);
}
```

**Coalescing**: Essential. Most sliders fire action events every ~100ms during a drag. Use the 300ms coalescing window:

```java
// In pushParamConsequence:
UndoRedoStack stack = projectModel.getUndoRedoStack();
if (stack.canUndo()) {
  Consequence last = stack.peekUndo();
  if (last instanceof SynthParamConsequence spc
      && spc.trackIndex() == trackIndex
      && spc.paramName().equals(paramName)
      && (System.currentTimeMillis() - spc.timestamp()) < 300) {
    // Replace last entry with updated newValue
    stack.replaceLast(new SynthParamConsequence(trackIndex, paramName, spc.oldValue(), newValue, System.currentTimeMillis()));
    return;
  }
}
stack.push(new SynthParamConsequence(trackIndex, paramName, oldValue, newValue, System.currentTimeMillis()));
```

This requires adding `peekUndo()` and `replaceLast()` to `UndoRedoStack`.

There are ~50+ such sliders across SwingSynthConfigDialog. A systematic approach: add a `pushParamChange()` helper accessed via the trackIndex, and wrap every slider callback. This can be done track-by-track across the dialog's tabs.

### 3g. SwingDelugeApp -- Track CRUD

In `AppTopBarListener.onAddTrack()` (line 1808): After adding, push a `TrackStructureConsequence(ADD, index, null, "Add track")`.

Track removal (from context menu): Before removing, snapshot the TrackModel. Push `TrackStructureConsequence(REMOVE, index, snapshot, "Remove track")`.

### 3h. SwingDelugeApp -- Pattern load

In `loadPatternIntoActiveTrack()` (line 1201): Before calling `patternSnapshot.applyTo(clip)`, capture `ClipSnapshot.fromClipModel(clip, ...)` to save the current state. Push a `PatternApplyConsequence`.

### 3i. ProjectModel setters (BPM, swing, reverb, etc.)

These already have listener notification patterns. The simplest approach: push consequences directly in the setter if the value actually changes:

```java
public void setBpm(float bpm) {
  float old = this.bpm;
  this.bpm = Math.max(1.0f, Math.min(300.0f, bpm));
  if (old != this.bpm) {
    undoRedoStack.push(new ProjectParamConsequence("bpm", old, this.bpm));
    notifyBpmChanged(this.bpm);
  }
}
```

This is the most invasive change but also the most thorough. Alternatively, push from the listener callbacks in `BridgeProjectListener` -- but those are in SwingDelugeApp and would miss direct model calls.

### 3j. SwingMasterFxPanel slider changes

Follows the same pattern as SwingSynthConfigDialog sliders -- before/after + coalescing.

---

## 4. Compound Consequence for Batch Operations

An operation that mutates multiple things at once (e.g., "clear all steps in a row", "load pattern") needs a single undo entry that reverses everything.

```java
public class CompoundConsequence implements Consequence {
  private final List<Consequence> children;
  private final String description;
  public void undo() { children.forEach(c -> c.undo()); }  // reverse order
  public void redo() { children.forEach(c -> c.redo()); }  // forward order
  ...
}
```

Usage for row clear:
```java
CompoundConsequence batch = new CompoundConsequence("Clear row " + row);
for (int s = 0; s < stepCount; s++) {
  StepData oldStep = cModel.getStep(row, s);
  batch.add(new StepConsequence(tIdx, cIdx, row, s, oldStep, StepData.empty()));
}
projectModel.getUndoRedoStack().push(batch);
bridge.setStep(engineRow, s, false); // actual mutation after recording
```

---

## 5. Keyboard Shortcuts and UI Binding

### Ctrl+Z / Ctrl+Y in SwingDelugeApp

Add to the frame's `InputMap`/`ActionMap`, alongside the existing Ctrl+N/O/S:

```java
// In SwingDelugeApp.setupUI() or constructor:

// Ctrl+Z = Undo
getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
    KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
getRootPane().getActionMap().put("undo", new AbstractAction() {
  public void actionPerformed(ActionEvent e) {
    currentProject.getUndoRedoStack().undo();
    refreshGrids();
    propagateCurrentModel();
  }
});

// Ctrl+Y = Redo
getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(
    KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
getRootPane().getActionMap().put("redo", new AbstractAction() {
  public void actionPerformed(ActionEvent e) {
    currentProject.getUndoRedoStack().redo();
    refreshGrids();
    propagateCurrentModel();
  }
});
```

The `undo()` / `redo()` methods already call `System.out.println()` for logging. After undo/redo, the UI must refresh to reflect the restored state.

### Menu items

Add to the File/Edit menu:

```java
JMenuItem undoItem = new JMenuItem("Undo");
undoItem.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK));
undoItem.addActionListener(e -> {
  currentProject.getUndoRedoStack().undo();
  refreshGrids();
  propagateCurrentModel();
});
```

---

## 6. UndoRedoStack Enhancements Required

The existing stack needs two new operations and the `UndoableAction` type should be replaced:

| Change | Rationale |
|---|---|
| Replace `UndoableAction` with `Consequence` | Richer type with category + coalescing support |
| Add `peekUndo()` | Returns top of undo stack without popping -- needed for coalescing |
| Add `replaceLast(Consequence)` | Pops and repushes during coalescing |
| Add `getUndoDescription()` / `getRedoDescription()` | For status bar or undo tooltip display |
| Remove `System.out.println` | Replace with proper logging or delegate to callback |

---

## 7. Implementation Order (Priority)

| Priority | Area | Files | Effort | Rationale |
|---|---|---|---|---|
| 1 | `Consequence` interface, `StepConsequence`, `AutomationConsequence` | `model/Consequence.java` | Low | Foundation types |
| 2 | `UndoRedoStack` enhancements | `model/UndoRedoStack.java` | Low | Add peek/replace |
| 3 | Keyboard shortcuts (Ctrl+Z/Y) | `SwingDelugeApp.java` | Low | Enables testing |
| 4 | Step toggle wiring in grid panel | `SwingGridPanel.java` (~8 sites) | Medium | Highest frequency mutation |
| 5 | Automation editing wiring | `SwingGridPanel.java` (~4 sites) | Medium | Drag coalescing pattern |
| 6 | Slider changes with coalescing | `SwingSynthConfigDialog.java` (~50 sites) | Large | Needs careful per-slider wrapping |
| 7 | Track/clip CRUD | `SwingGridPanel.java`, `SwingDelugeApp.java` | Medium | Structure changes |
| 8 | ProjectParam consequences | `ProjectModel.java` | Medium | Many setters but simple pattern |
| 9 | CompoundConsequence + pattern load | `Consequence.java`, `SwingDelugeApp.java` | Low | Batch operations |
| 10 | Mute/solo, audio track, arranger | Various | Low | Nice-to-have |

---

## 8. Edge Cases

| Case | Handling |
|---|---|
| UndoStack empty, user presses Ctrl+Z | `canUndo()` returns false, no-op. Optionally flash a "Nothing to undo" status bar message. |
| RedoStack empty, user presses Ctrl+Y | Same as above. |
| User loads a new project | Call `undoRedoStack.clear()` on `ProjectModel` creation. |
| Max depth exceeded (64) | `UndoRedoStack.push()` already removes oldest via `removeLast()`. |
| Coalescing window expires | If 300ms passes without a new param change for the same param, subsequent change becomes a new entry. |
| Pattern load overwrites clip | PatternApplyConsequence captures full before-snapshot. Undo calls `snapshot.applyTo(clip)`. |
| Undo after project close/reopen | Stack is ephemeral (in-memory only). On reload, stack is empty. |
| Undo while playing | Step changes and param changes should be undoable during playback. The bridge globals must be re-synced after undo. |
| Rapid same-pad toggling | Coalesce: if last undo entry is StepConsequence for same track/clip/row/step, pop it (net state = initial state, no history entry). |
| Drag on automation editor | Only push on `mouseReleased`, not per `mouseDragged` event. This prevents flooding the stack. |

---

## 9. Architectural Diagram (Data Flow)

```
User action (pad click, slider drag, menu item)
    |
    v
UI handler captures OLD state from model
    |
    v
UI handler mutates model (setter call) + bridge (bridge.set* call)
    |
    v
UI handler creates Consequence(old, new) and pushes to
    |
    v
ProjectModel.undoRedoStack (coalesces if applicable)
    |
    v
...
Ctrl+Z pressed
    |
    v
SwingDelugeApp keyboard handler calls undoRedoStack.undo()
    |
    v
Consequence.undo() reverses mutation (model setter with old value)
    |
    v
UI refresh + propagate to bridge
```

The key invariant: **model is the source of truth**. Undo/redo always reads from and writes to the model. The bridge (engine) is re-synced after undo/redo via `pushModelToBridge()` or targeted bridge calls.

---

## 10. Files to Create/Modify

### Create:
1. `deluge/src/main/java/org/chuck/deluge/model/Consequence.java` -- Consequence interface + all record implementations
2. `deluge/src/test/java/org/chuck/deluge/model/ConsequenceTest.java` -- unit tests for each Consequence type

### Modify:
1. `deluge/src/main/java/org/chuck/deluge/model/UndoRedoStack.java` -- add peek/replace/clear, replace UndoableAction with Consequence
2. `deluge/src/main/java/org/chuck/deluge/model/ProjectModel.java` -- add undoRedoStack field + getter
3. `deluge/src/main/java/org/chuck/deluge/ui/SwingGridPanel.java` -- wire step edits, automation edits, context menu mutations
4. `deluge/src/main/java/org/chuck/deluge/ui/SwingDelugeApp.java` -- keyboard shortcuts, track CRUD, pattern load
5. `deluge/src/main/java/org/chuck/deluge/ui/SwingSynthConfigDialog.java` -- wire all ~50 sliders with coalescing
6. `deluge/src/test/java/org/chuck/deluge/model/UndoRedoStackTest.java` -- extend for peek/replace
