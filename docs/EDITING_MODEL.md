# Editing Model — How Values Reach the Engine and Persist

Every edit in the Swing UI follows one of these paths. They are **not interchangeable** — each path reaches a different layer of the system with different persistence guarantees.

---

## The Three System Layers

```
┌──────────────────────────────────────────────────────────────────┐
│  Layer 1: Java Model (org.chuck.deluge.model.*)                 │
│  ProjectModel → TrackModel / ClipModel / StepData               │
│  Serializes to/from XML. Has its own undo/redo stack.           │
│  Persists across restarts as .deluge files.                      │
├──────────────────────────────────────────────────────────────────┤
│  Layer 2: BridgeContract (shared memory arrays)                  │
│  Flat int/float arrays indexed by (track × steps).              │
│  The only layer the legacy ChucK DSL engine reads.              │
│  No persistence — must be rebuilt from Layer 1 on load.         │
├──────────────────────────────────────────────────────────────────┤
│  Layer 3: Firmware Model (org.chuck.deluge.firmware.model.*)    │
│  Song → InstrumentClip → NoteRow → Note                          │
│  Directly rendered by PureFirmwareEngine during playback.        │
│  Many-to-one: a single firmware Sound object backs one clip.    │
│  Has its own ActionLogger undo system (in firmware engine).      │
└──────────────────────────────────────────────────────────────────┘
```

### How layers connect

Layer 1 → Layer 3: `FirmwareFactory.createSong(ProjectModel)` — one-time conversion.
Layer 1 → Layer 2: `SwingDelugeApp.pushModelToBridge()` — iterates all tracks, writes every field.
Layer 2 → Layer 1: Project XML serializer reads Layer 1 directly (no bridge needed).
Layer 3 ↔ Layer 2: `PureFirmwareEngine.syncFromBridge()` reads BPM/volume from VM globals (Layer 2).

The firmware engine (Layer 3) does NOT read Layer 2 step/note arrays — it reads its own `NoteRow.notes` list. Editing firmware notes means mutating `Note` objects in Layer 3 and optionally syncing back to Layer 1 for persistence.

---

## Path 1: Direct Bridge Calls (Grid Cells, Row Actions)

**Used by:** Left-click on grid cells, row mute/length buttons, velocity lane

**Flow:**
```
SwingGridPanel → BridgeContract.set*(track, step, value) → ChucK ChuckArray (shared memory)
```

**What it writes:** Per-step data (step on/off, velocity, probability, mute, track length) directly into the ChucK engine's shared arrays. The legacy DSL engine reads these on every cycle.

**What it does NOT write:** `TrackModel` or `ClipModel` — the Java model objects. These values **will be lost** on app restart unless also written to the model.

**Exception:** The synth grid's left-click handler (line ~813) calls both `bridge.setStep()` **and** `cModel.setStep()`:

```java
// bridge write (engine can hear it now)
bridge.setStep(engineRow, activeCol, !stepState);
// model write (survives view switch / XML save)
cModel.setStep(modelRow, activeCol, new StepData(!stepState, ...));
```

All other direct bridge calls (row mute, track length, velocity lane drum grid) write **only** to the bridge. See "Path 3" for persistence.

**Firmware note:** In PureFirmwareEngine mode, the bridge arrays are **not read** by the audio engine. The firmware engine reads its own `NoteRow.notes` list. So Path 1 writes are only heard by the legacy DSL engine.

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

**Firmware note:** After `pushModelToBridge()`, if the firmware engine is active, the caller usually also calls `FirmwareFactory.createSong()` to rebuild Layer 3. See Path 4.

---

## Path 3: Firmware Model Direct Edit

**Used by:** PianoRollView, KitView, SessionView (firmware-native views), MidiFollow param updates

**Flow:**
```
View → Song / InstrumentClip / NoteRow / Note mutators
  → PlaybackHandler reads NoteRow.notes during renderBlock()
```

**What it writes:** Notes, note properties (velocity, probability, length, position), and the `ParamManager` on each NoteRow. These are the data structures the firmware engine reads **directly** during `InstrumentClip.processCurrentPos()`.

**How firmware playback reads notes** (`InstrumentClip.processCurrentPos()` at line 64):
```java
for (NoteRow noteRow : noteRows) {
    int dist = noteRow.processCurrentPos(ticksSinceLast, pendingNoteOns,
        lastProcessedPos, loopLength, currentlyPlayingReversed);
    // ...
}
for (PendingNoteOn noteOn : pendingNoteOns) {
    triggerNote(noteOn); // calls FirmwareSound.triggerNote() or FirmwareKit.triggerDrum()
}
```

**Persistence:** Firmware model changes are NOT automatically persisted to XML. To save firmware edits, they must be written back to Layer 1 (ProjectModel/TrackModel/ClipModel) before XML serialization. Currently this back-sync does NOT happen automatically — see Path 5.

**Undo:** The firmware model has its own `ActionLogger` for Consequence-based undo within the firmware engine, separate from `ProjectModel.getUndoRedoStack()`.

---

## Path 4: Model → Firmware (one-time conversion)

**Used by:** Project load, track structure changes (add/remove track), pattern switch

**Flow:**
```
FirmwareFactory.createSong(ProjectModel) → Song
  → for each SynthTrackModel → InstrumentClip + FirmwareSound + NoteRows
  → for each KitTrackModel   → InstrumentClip + FirmwareKit + FirmwareSound per drum
```

**Called from:** `SwingDelugeApp` lines ~1372-1428:
```java
Song fwSong = FirmwareFactory.createSong(currentProject);
// Register sounds with audio engine
fwEngine.sounds.clear();
for (Clip c : fwSong.clips) {
    if (c instanceof InstrumentClip ic && ic.sound != null) {
        fwEngine.sounds.add(ic.sound);
    }
}
// Set song on playback handler
fwHandler.setSong(fwSong);
```

**What it copies:** Step data (position, velocity, gate) from `ClipModel` → `NoteRow.notes`. Track-level params are set on `FirmwareSound` via `Param.LOCAL_VOLUME`, `Param.LOCAL_LPF_FREQ`, etc. in the `FirmwareSound` constructor.

**Limitation:** This is a **one-time snapshot**, not a live sync. Changes to `ProjectModel` after `createSong()` are NOT reflected in the firmware engine unless `createSong()` is called again.

---

## Path 5: Firmware → Model (back-sync)

**Used by:** NOT YET IMPLEMENTED in production paths

**Flow (future):**
```
Firmware Note objects → ProjectModel / ClipModel / StepData → XML save
```

Currently there is no automatic back-propagation from firmware model edits to the Java model layer. This means:

| Edit source | Heart by DSL engine? | Heart by FW engine? | Persists to XML? |
|-------------|---------------------|---------------------|-------------------|
| Grid click (synth) | Yes (Path 1) | No | Only if dual write |
| Grid click (kit) | Yes (Path 1) | No | No |
| Track editor slider | Yes (Path 2) | Yes (if createSong called) | Yes |
| PianoRollView note edit | No | Yes (Path 3) | No |
| MidiFollow CC change | Via callback | Via onSetParam | No |

**To fix:** A `syncFirmwareToModel()` method would iterate `Song.clips`, read each `NoteRow.notes` list back into the corresponding `ClipModel`, then call `pushModelToBridge()`. This would be needed before XML save if firmware-native views have been used.

---

## Path 6: Dialog-Only (No Writes)

**Used by:** `TrackInspectorDialog`, `BarAutomationDialog`

**Flow:**
```
Dialog → displayed → discarded
```

These dialogs have getters (`getVolume()`, `isLpfSweepEnabled()`, etc.) but the call sites never read them. Values are **lost** on dialog close. Wiring them requires choosing the right path:

| Dialog | Should write via | Because |
|--------|-----------------|---------|
| `StepPropertiesDialog` | Path 3 (firmware Note) + Path 1 (bridge) | Per-step data, both engines |
| `TrackInspectorDialog` preset/clone buttons | Already works (action listener → dispose) | Track-level ops |
| `TrackInspectorDialog` volume/pan sliders | Path 2 (TrackModel + pushToBridge) + re-createSong | Track-level params |
| `BarAutomationDialog` | Path 1 or custom bridge array | Per-bar arrangement data |

---

## Which Path to Use

| You're editing | DSL engine active | Use | Via |
|---------------|-------------------|-----|-----|
| Grid steps (on/off) | Legacy (DelugeEngineDSL) | Path 1 | `bridge.setStep()` + `cModel.setStep()` |
| Grid steps (on/off) | PureFirmwareEngine | Path 3 | `noteRow.attemptNoteAdd()` + future back-sync |
| Step velocity/probability | Legacy | Path 1 | `bridge.setVelocity()` + `cModel.setStep()` |
| Step velocity/probability | PureFirmwareEngine | Path 3 | Note object mutation |
| Track volume, pan, filter, etc. | Either | Path 2 | `trackModel.set*()` + `pushModelToBridge()` + `FirmwareFactory.createSong()` |
| Track mute, length | Legacy | Path 1 | `bridge.setMute()` / `bridge.setTrackLength()` |
| Track mute, length | PureFirmwareEngine | Path 3 | `noteRow.muted = true` / `clip.loopLength =` |
| Anything arrangement/bar-level | Either | Path 1 | New bridge array + getter |
| Add/remove track | Either | Path 4 | `projectModel.addTrack()` + `createSong()` + `pushModelToBridge()` |
| MidiFollow param | Either | Path 3 | `fs.paramNeutralValues[Param.LOCAL_*]` via `onSetParam` |

---

## Undo/Redo Architecture

| Layer | Undo mechanism | Scope |
|-------|---------------|-------|
| **Layer 1: Java Model** | `ProjectModel.undoRedoStack` (64 entries) | Project-level params, track structure, clip structure, pattern loads |
| **Layer 2: Bridge** | None (volatile shared memory) | N/A |
| **Layer 3: Firmware Model** | `ActionLogger` + `Consequence` subclasses | Note existence, param changes within firmware engine |

The two undo systems are **independent**. A `ProjectModel` undo restores Java model state and then pushes to bridge (`doUndo()` → `pushModelToBridge()`). Firmware model undo affects playback-engine state only. They are NOT synchronized.

---

## Key Files by Layer

### Layer 1: Java Model (org.chuck.deluge.model)
| File | Purpose |
|------|---------|
| `ProjectModel.java` | Root model: BPM, swing, master volume, track list, reverb/delay/sidechain/compressor params, undo/redo stack |
| `TrackModel.java` | Abstract base for SynthTrackModel, KitTrackModel, AudioTrackModel |
| `SynthTrackModel.java` | Synth params: osc type/freq, filter, envelopes, LFO, arp, FX |
| `KitTrackModel.java` | Kit params: drum list, mute groups, per-drum ADSR |
| `ClipModel.java` | Per-track clip: step grid, row count, step count, step automation |
| `StepData.java` | Single step: active, velocity, pitch, gate, probability, automation values |
| `Consequence.java` | Undo/redo actions: ProjectParam, TrackStructure, ClipStructure, PatternLoad |

### Layer 2: Bridge (org.chuck.deluge)
| File | Purpose |
|------|---------|
| `BridgeContract.java` | All shared arrays and globals; StepData, TrackData, SynthData, KitData inner classes |
| `BridgeUtil.java` | Helper methods for array index math |

### Layer 3: Firmware Model (org.chuck.deluge.firmware.model)
| File | Purpose |
|------|---------|
| `Song.java` | Root: clip list, BPM, swing, global automation ParamManager |
| `Clip.java` | Abstract: loop length, playback direction, position tracking, automation |
| `InstrumentClip.java` | Sequencer: NoteRow list, sound reference, processCurrentPos() |
| `NoteRow.java` | Per-note-row: notes list, mute, param manager, processCurrentPos() |
| `Note.java` | Single note: position, length, velocity, probability, iterance, fill |
| `Positionable.java` | Base for Note: position tracking |
| `ClipInstance.java` | Arrangement view clip placement |
| `TimelineCounter.java` | Position/base for Song and Clip |

### Cross-layer glue
| File | Purpose |
|------|---------|
| `FirmwareFactory.java` | `createSong(ProjectModel)` — one-time Layer 1 → Layer 3 conversion |
| `PureFirmwareEngine.java` | `syncFromBridge()` — runtime Layer 2 → Layer 3 param sync (BPM, master vol, per-sound LPF/VOL) |
| `SwingDelugeApp.java` | `pushModelToBridge()` — Layer 1 → Layer 2 bulk sync; also calls FirmwareFactory.createSong() |
