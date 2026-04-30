# Editing Model — How Values Reach the Engine and Persist

Every edit in the Swing UI follows one of these paths. They are **not interchangeable** — each path reaches different parts of the system.

## Path 1: Direct Bridge Calls (Grid Cells, Row Actions)

**Used by:** Left-click on grid cells, row mute/length buttons, velocity lane

**Flow:**
```
SwingGridPanel → BridgeContract.set*(track, step, value) → ChucK ChuckArray (shared memory)
```

**What it writes:** Per-step data (step on/off, velocity, probability, mute, track length) directly into the ChucK engine's shared arrays. The engine reads these on every cycle.

**What it does NOT write:** `TrackModel` or `ClipModel` — the Java model objects. These values **will be lost** on app restart unless also written to the model.

**Exception:** The synth grid's left-click handler (line ~813) calls both `bridge.setStep()` **and** `cModel.setStep()`:

```java
// bridge write (engine can hear it now)
bridge.setStep(engineRow, activeCol, !stepState);
// model write (survives view switch / XML save)
cModel.setStep(modelRow, activeCol, new StepData(!stepState, ...));
```

All other direct bridge calls (row mute, track length, velocity lane drum grid) write **only** to the bridge. See "Path 3" for persistence.

---

## Path 2: pushModelToBridge() — Bulk Sync

**Used by:** Track editor panels after user changes a knob/slider/field

**Flow:**
```
TrackEditor → TrackModel.set*(value) → pushModelToBridge() → BridgeContract.set*(track, value) → ChucK global arrays
```

**What it writes:** Track-level params (osc type, filter freq, envelope ADSR, LFO rate, arp mode, volume, pan, FX sends, etc.) — everything in `SynthTrackModel` / `KitTrackModel`.

**Called from:**
- `SwingDelugeApp.pushModelToBridge()` — iterates all tracks and pushes every field
- Individual field editors call it after each change (e.g., slider drag → `trackModel.setLpfFreq(v)` → `pushModelToBridge()`)

**Persistence:** `TrackModel` objects are serialized to XML via `ProjectModel.save()`. So values set through Path 2 **survive** XML save/load.

---

## Path 3: Grid → Bridge + Model (Dual Write)

**Used by:** StepPropertiesDialog (velocity), Step data model sync

**Flow:**
```
Dialog → bridge.set*(track, step, value) → cModel.setStep(...)
```

Currently only `StepPropertiesDialog` does this (both call sites, synth and kit grids). Pattern:

```java
bridge.setVelocity(engineRow, colId, newVel / 100.0);
cModel.setStep(trk, colId, new StepData(st, newVel / 100.0f, ...));
```

This is the **correct** pattern — the engine hears it immediately AND it persists through XML save/load.

---

## Path 4: Dialog-Only (No Writes)

**Used by:** `TrackInspectorDialog`, `BarAutomationDialog`

**Flow:**
```
Dialog → displayed → discarded
```

These dialogs have getters (`getVolume()`, `isLpfSweepEnabled()`, etc.) but the call sites never read them. Values are **lost** on dialog close. Wiring them requires choosing the right path:

| Dialog | Should write via | Because |
|--------|-----------------|---------|
| `StepPropertiesDialog` | Path 3 (bridge + model) | Per-step data |
| `TrackInspectorDialog` preset/clone buttons | Already works (action listener → dispose) | Track-level ops |
| `TrackInspectorDialog` volume/pan sliders | Path 2 (TrackModel + pushToBridge) | Track-level params |
| `BarAutomationDialog` | Path 1 or custom bridge array | Per-bar arrangement data |

---

## Which Path to Use

| You're editing | Use | Via |
|---------------|-----|-----|
| Grid steps (on/off) | Path 3 | `bridge.setStep()` + `cModel.setStep()` |
| Step velocity/probability | Path 3 | `bridge.setVelocity()` + `cModel.setStep()` |
| Track volume, pan, filter, etc. | Path 2 | `trackModel.set*()` + `pushModelToBridge()` |
| Track mute, length | Path 1 | `bridge.setMute()` / `bridge.setTrackLength()` |
| Anything arrangement/bar-level | Path 1 | New bridge array + getter |
