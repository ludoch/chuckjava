# Deluge Grid Views & Model — UI Architecture Reference

> Goal of this document: describe **every concept behind the grid views** (logical vs
> physical), **what you can do** on cells / rows / scrollers, **how playback animates** them,
> and **the data model** they edit (loaded from XML). It is written so a brand-new UI could be
> generated from it without reading the current Swing code. The reference implementation is
> `org.deluge.ui.SwingGridPanel` (one class, reused for every view) over the model in
> `org.deluge.model.*`; the conceptual source of truth is the Synthstrom Deluge firmware
> (`~/a/DelugeFirmware/src/deluge/gui/views/`).

---

## 1. Mental model: a spreadsheet with a small movable window

The Deluge is a **7×16 (desktop: 8×16) pad grid** — a *physical* window onto a much larger
*logical* grid. This is exactly the Excel/Google-Sheets relationship:

- **Logical grid (virtual):** the full data — all pitches × all time steps of a clip, or all
  tracks × all bars of the arrangement. Can be hundreds of rows/columns.
- **Physical grid (viewport):** the fixed pad matrix actually drawn. You move it over the
  logical grid with **scroll** (pan) and change how many logical units each pad represents with
  **zoom**.

Two transforms connect them, and **every view defines them differently**:

```
logicalRow    = f_row(physicalRow, scrollOffset, zoom, foldMap)
logicalColumn = f_col(physicalColumn, scrollOffsetX, zoom)
cellValue     = model.lookup(logicalRow, logicalColumn)     // a "cell"
```

A UI is "correct" when those two functions and the inverse (pixel → logical, for clicks) match
the view's contract in §4.

---

## 2. The views

Five view modes share one grid widget (`GridViewMode`): **CLIP, SONG, ARRANGEMENT, AUTOMATION,
KEYPLAY**. Each maps to a real Deluge firmware view.

| View | Deluge firmware | Logical rows | Logical columns | A "cell" is | Primary action |
|---|---|---|---|---|---|
| **CLIP** | `instrument_clip_view` / `audio_clip_view` | pitches (synth/MIDI) or drum lanes (kit) — up to 128 | time **steps** of the clip (clip length, up to 256) | a note/step (`StepData`) | toggle a note on/off |
| **SONG** | `session_view` | tracks (one per row) | clip slots / sections (clip launcher) | a launchable clip instance | launch / mute a clip |
| **ARRANGEMENT** | `arranger_view` | tracks | absolute time (bars) on a timeline | a clip *instance* placed at a time | place / move / resize a clip block |
| **AUTOMATION** | `automation_view` | value lanes (0–100% of one parameter) | time steps of the clip | an automation value at a step | paint a parameter curve |
| **KEYPLAY** | `keyboard_screen` (isomorphic) | — (a playable keyboard, not data) | — | a playable note | trigger a live note |

### 2.1 CLIP view (the piano roll / step sequencer)
- **Synth/MIDI track:** rows are **pitches**. Row→pitch is the isomorphic mapping
  `pitch = scrollOffset + physicalRow*rowInterval` style, or the per-row stored note
  (`ClipModel.getRowYNote`). In-scale vs out-of-scale rows are coloured/dimmed.
- **Kit track:** rows are **drum lanes** (one row per drum), velocity-drums layout (not
  isomorphic).
- **Columns** are time steps; clip length can exceed the 16 visible → horizontal paging.
- Optional **Fold mode**: collapse the logical pitch axis to only rows that actually contain
  notes (`foldedPitches`), so a sparse melody fills the screen. The row transform then indexes
  `foldedPitches` instead of a linear pitch scale.
- **Triplet mode**: a clip renders 12 steps instead of 16 per page.

### 2.2 SONG view (clip launcher / session)
- Rows = tracks, columns = clip slots grouped into **sections** (A–Z). A cell launches/queues
  or mutes the active clip of that track. Launch is quantised to the loop; the cell **blinks**
  while queued, solid while playing. Sections can loop/repeat (`SongSection`).

### 2.3 ARRANGEMENT view (timeline)
- Rows = tracks, columns = **absolute time** (bars). Cells are **clip instances**
  (`ArrangerClip` / `clipInstances`) placed and resized along the timeline. This is the linear
  song timeline, as opposed to SONG's loop-based launcher.

### 2.4 AUTOMATION view
- Same column (time) axis as CLIP, but rows are **value lanes** of one selected parameter
  (`AutomationParam`, e.g. `lpfFrequency`, `env0Attack`, `volume`). Painting cells writes a
  per-step value curve (`ClipModel.automationData` / `rowAutomationData`).

### 2.5 KEYPLAY
- Not a data grid: an **isomorphic playable keyboard**
  (`pitch = scrollOffset + x + y*rowInterval`, default `rowInterval=5`). Pressing a pad triggers
  a live note; it flashes. Used for input, not editing.

---

## 3. Physical viewport mechanics (scroll, zoom, fold)

State lives on the grid widget; persisted defaults in `PreferencesManager`.

### 3.1 Zoom — `GridMode`
Discrete zoom presets (rows × step-columns):

| GridMode | rows | step columns |
|---|---|---|
| `GRID_8x16` (default) | 8 | 16 |
| `GRID_16x16` | 16 | 16 |
| `GRID_24x16` | 24 | 16 |
| `GRID_16x24` | 16 | 24 |

`columnCount = stepColumns + 2` in CLIP (the extra two are the **MUTE** and **AUDITION/SOLO**
side columns, mirroring hardware columns x=16/17). Changing `GridMode` is a *structural* change
(rebuild). Cell pixel size `padSz` is then solved to fit the viewport:
`padSz = min(cellsWidth/columnCount, heightLimitedPadSz)`, clamped 16..200.

### 3.2 Scroll (pan)
- `scrollOffset` — vertical pan over logical rows (pitch in CLIP, default 67 ≈ C4 at top).
- `scrollOffsetX` — horizontal pan over logical step columns (time paging for long clips).
- Backed by real scrollbars + mouse wheel (vertical) + Shift+wheel (horizontal). Scroll is
  non-destructive: it only changes the transform, components are refreshed **in place**
  (`refreshInPlace`), never rebuilt — important for performance and to avoid flicker.

### 3.3 Fold
- `foldMode` + `foldedPitches`: replaces the linear pitch row mapping with a compacted list of
  only-used pitches (auto-focus). Toggling it is structural.

### 3.4 Cross-screen / wrap edit
- "Wrap edit" (`instrument_clip_view` cross-screen): editing past the right edge wraps to affect
  the corresponding step on the next page — drag a note across the page boundary.

---

## 4. The cell

### 4.1 Anatomy — `StepData` (immutable record)
A CLIP cell is one note/step:

```java
record StepData(boolean active, float velocity, float gate, float probability,
                int pitch, int iterance, float fill)
```

- `active` — note present.
- `velocity` 0..1 — loudness → drives pad **brightness**.
- `gate` 0..1 — note length within the step (default click gate ≈ 0.9).
- `probability` 0..1 — chance the note fires.
- `pitch` — semitone (synth) / drum index (kit).
- `iterance` — Nth-of-M iteration pattern (1-of-4 etc.).
- `fill` — fill/“not-fill” conditional trigger.

### 4.2 Cell actions (CLIP)
| Action | Desktop gesture | Hardware | Effect |
|---|---|---|---|
| Toggle note | click pad | press edit pad | flip `active` |
| Set velocity | hold pad + vertical drag / props dialog | hold pad + turn **X_ENC** | `velocity` |
| Set length / tie | drag to a pad on the right | hold pad + 2nd pad | `gate` / multi-step tie |
| Probability / iterance / fill | step props dialog | hold pad + gold knobs | conditional fields |
| Per-step parameter lock (automation) | Shift-arm a param + adjust | hold pad + MOD turn | writes step automation |
| Nudge / transpose | X/Y encoder semantics | X_ENC nudge / Y_ENC transpose | shift in time / pitch |
| Audition row | side AUDITION column | audition column x=17 | play the row's sound |
| Mute row | side MUTE column | mute column x=16 | toggle row mute |

For SONG/ARRANGEMENT a "cell action" is launch/mute (SONG) or place/move/resize a clip instance
(ARRANGEMENT). For AUTOMATION it is "set value at step".

### 4.3 The sidebar columns (columns 17 & 18) — **not** step cells

The Deluge grid has a **2-column sidebar** to the right of the step area, mirroring hardware
columns x=16 and x=17. They are appended after the step columns, so `columnCount = stepColumns + 2`,
and they are addressed *positionally* (independent of zoom width):

| Sidebar column | colId | 1-indexed | Helper | Role |
|---|---|---|---|---|
| **MUTE / status** | `columnCount - 2` | **col 17** (default 16-step view) | `isMuteColumn(colId)` | mute the row's track/clip; in SONG it's the clip launch/status cell (blinks while queued) |
| **SOLO / AUDITION** | `columnCount - 1` | **col 18** | `isSoloColumn(colId)` | CLIP/kit: audition (play) the row's sound; SONG/ARR: solo / "mute others" |

`isStepColumn(colId) = !isMuteColumn && !isSoloColumn` is the test every cell handler uses to
decide "is this a data step or a sidebar action". The transforms in §11 apply **only** to step
columns; the two sidebar columns carry no `StepData` and are excluded from selection, clipboard,
and the playhead. In SONG/ARRANGEMENT the sidebar only appears when `columnCount > stepCount`.

A new UI must render these two as fixed-function columns (distinct styling), route clicks through
`isMute/isSolo/isStepColumn`, and **never** treat them as part of the time axis.

---

## 5. Selection, cut / copy / paste

Two independent layers exist today:

### 5.1 Multi-cell selection (CLIP)
- `selectedCells: Set<"modelRow,activeCol">`.
- **Rectangle drag-select** (`finalizeDragSelection`): drag across pads selects the rectangle.
- **Ctrl/Cmd-click** toggles individual cells (additive).
- **Delete** clears the selected steps (`deleteSelectedStepsAction`) in model + engine.
- Live visual feedback during drag (`updateDragSelectionVisuals`, pad `setSelected`).

### 5.2 Whole-clip clipboard
- `copyClipNotes()` snapshots the entire edited clip's `StepData[rows][cols]` into a static
  `noteClipboard`.
- `pasteClipNotes()` writes it into the edited clip (model + per-cell engine sync) and refreshes.
- Clip-level **duplicate**: `ClipModel.deepCopy(name)` (copies grid, raw note events, per-row
  params, automation) → `track.addClip(copy)`.

### 5.3 What a new UI should add (gap vs hardware)
The Deluge supports **copy/paste of a selection or a row/region** (X_ENC+LEARN copy, SHIFT
paste; copy notes between rows/clips). A complete new UI should generalise §5.1+§5.2 into one
clipboard that can hold: a **cell rectangle**, a **whole row**, **multiple rows**, or a **whole
clip**, with cut (= copy + delete) and paste-at-cursor. The model already supports it (rows and
cells are plain data); only the clipboard/command layer needs to be richer.

---

## 6. Scrollers & zooming (input bindings)

| Gesture | Result |
|---|---|
| Vertical scrollbar / wheel | `scrollOffset` (pan pitch/tracks) |
| Horizontal scrollbar / Shift+wheel | `scrollOffsetX` (page through time) |
| Alt/Cmd + wheel, or Alt+PageUp/PageDown | cycle `GridMode` (zoom) |
| Drag on pads | rectangle multi-select |
| Window resize | recompute `padSz` only (no structural rebuild) |

Scrolling and zooming are **view-only**: they never touch the model. Programmatic scrolls (e.g.
playhead follow) set a `isScrollingProgrammatically` guard so they don't fight user input.

---

## 7. Playback animation

Driven by `updatePlayhead(step)` called from the transport each step.

- **Playhead cursor:** the engine's current step maps to a visual column
  `stepMod = step % trackLen − scrollOffsetX` (clamped); that column's pads render the playhead
  highlight (`DelugePadButton.setPlayhead`). `step < 0` clears it.
- **Auto page-follow:** when `trackLen > visible stepColumns` and
  `playheadFollowMode`, the view auto-scrolls `scrollOffsetX` to the page containing the current
  step (`targetPageOffset = (step % trackLen)/stepCount * stepCount`), via the scrollbar under
  the programmatic guard.
- **Velocity → brightness:** active cells blend the track colour by velocity
  (`velocityBlend(color, vel)`).
- **Blink clocks** (hardware 60/110 ms): playhead/record cursor blink, launch-queued/mute blink
  in SONG. A single shared animation clock (`UiAnimator`) should drive all blink/fade/scroll so
  they're phase-aligned.
- **Flash:** live notes from KEYPLAY/QWERTY flash the row (`flashIsomorphicNote`).
- **Note tails / blur:** held notes should render a tail across their gate length (hardware
  `forTail`/`forBlur`).
- Performance: during playback only **in-place** pad property updates happen — never a structural
  rebuild — so animation is cheap and flicker-free.

---

## 8. The data model

Loaded from XML, edited by the views, pushed live to the audio engine via a bridge.

```
ProjectModel                       (the song)
 ├─ tempo/bpm, swing, timeSig, key, scale, transpose, humanize
 ├─ master volume/pan + global FX: reverb, delay, sidechain, compressor, songParams
 ├─ UndoRedoStack (64 deep)
 ├─ List<SongSection>              (A–Z sections: patternIds, numRepeats, loop/link)
 └─ List<TrackModel>              (abstract; one per row in SONG/ARRANGEMENT)
      ├─ name, type (TrackType), muted, volume, pan, colourHex
      ├─ activeClipIndex
      └─ List<ClipModel>
           ├─ name, rowCount, stepCount, color
           ├─ playMode (NORMAL/…), playDirection (FORWARD/…), tripletMode, isArrangementOnly
           ├─ grid: List<List<StepData>>          ← the CLIP cells
           ├─ rowYNote: Map<row,midi>             ← row→pitch for synth piano-roll
           ├─ rawNoteEvents: Map<row,List<HighResNote>>  ← high-res note events (sub-step timing)
           ├─ automationData: Map<param,float[]>          ← clip-level param curves
           ├─ rowAutomationData: Map<row,Map<param,float[]>>
           ├─ rowSoundParams: Map<row,Map<param,Float>>    ← per-drum-row sound params (kits)
           └─ kitParams: Map<param,Float>

TrackModel subtypes:
 ├─ SynthTrackModel  — osc1/2 type+mix, noise, unison, wavetable index, sample paths,
 │                     FilterMode + LPF/HPF freq/res/morph, EnvelopeModel[], LfoModel[],
 │                     ArpModel, ModKnob[], PatchCable[]   (a full subtractive/FM/wavetable voice)
 ├─ KitTrackModel    — List<Drum> (SoundDrum sample/synth, MIDIDrum, GateDrum)
 ├─ MidiTrackModel   — MIDI channel/CC routing
 └─ AudioTrackModel  — audio clips (sample regions on a timeline)
```

Supporting model types: `StepData` (cell), `HighResNote` (precise note event), `EnvelopeModel`,
`LfoModel`/`LfoType`, `ArpModel`, `ChordModel`, `PatchCable`/`ModKnob` (mod matrix),
`FilterMode`, `Scales`, `AutomationParam` (the canonical parameter-name registry),
`Consequence`/`UndoRedoStack` (undo).

**Key invariant for the UI:** the views edit `ClipModel.grid` (cells) + per-row maps; the
inspector/dialogs edit the `*TrackModel` synth params. Both are pushed to the live engine through
the bridge per edit (live-apply) and serialised back to XML on save.

---

## 9. XML storage backing

- **Parse:** `org.deluge.xml.DelugeXmlParser` reads a Deluge `.XML` song into a `ProjectModel`
  (helpers: `DelugeNoteDataMapper` for packed note data, `DelugeHexMapper` for fixed-point
  params).
- **Serialise:** `ProjectSerializer` / `XMLSerializer` / `KitSynthSerializer` /
  `PatternSerializer` write the model back. Hardware-loadable songs must use the native
  **c1.2.0** format (clip↔instrument name-linkage or the Deluge reports `FILE_CORRUPTED`).
- The model is the single in-memory truth; XML is load/save only. A new UI binds to the model,
  not to XML.

---

## 10. Hardware mapping (for fidelity)

The desktop adapts the Deluge's *turn / press / press-while-turning* encoder language:

- **6 encoders:** X_ENC (horizontal: scroll time / clip length / nudge / velocity-with-pad),
  Y_ENC (vertical: scroll rows / transpose / octave), SELECT_ENC (menu/enter editor),
  TEMPO_ENC (BPM/swing), MOD_0/MOD_1 gold knobs (automate assigned param; press = delete
  automation). Encoders have a **sticky back-wiggle filter** (~0.5 s) on hardware.
- **Buttons:** PLAY, RECORD, SHIFT (modal layer), BACK (undo / Shift = redo), TAP_TEMPO,
  SESSION/CLIP/KEYBOARD/AUTOMATION view switches, SYNTH/KIT/MIDI/CV type, SCALE, LEARN,
  SAVE/LOAD, AFFECT_ENTIRE, CROSS_SCREEN.
- **Pads:** edit press/hold = note; hold-pad + 2nd pad = length; audition col (x=17); mute col
  (x=16); AUDITION+SCALE = set root.

A desktop UI substitutes mouse/keyboard gestures (click, drag, Shift/Ctrl/Alt modifiers, scroll
wheel) for these but should preserve the **same semantics and the SHIFT modal layer**.

---

## 11. Coordinate transforms (the contract to re-implement)

For each view, a new UI must implement exactly these (CLIP shown; others analogous):

```
# physical (pad) → logical
modelRow(physRow):
    if foldMode: return foldedPitches[physRow]              # compacted pitch list
    else:        return rowMapping(scrollOffset, physRow)   # isomorphic / per-row note
logicalStep(physCol): return scrollOffsetX + physCol        # (triplet: 12-step page)

# logical → cell value
cell = clip.getStep(modelRow, logicalStep)                  # StepData

# playhead (logical step) → physical column
physCol = (step % trackLen) - scrollOffsetX                 # clamp [0, stepColumns)

# click (pixel) → logical, then mutate model + engine + mark dirty for save
```

The two sidebar columns (§4.3) — MUTE = `columnCount-2` (col 17), SOLO/AUDITION = `columnCount-1`
(col 18) — are *not* part of the step axis. Test with `isStepColumn(colId)` before applying the
transforms above; they carry no `StepData`.

---

## 12. Checklist to generate a brand-new UI

1. Implement the **logical↔physical transforms** (§11) per view.
2. Render a **viewport** of `GridMode.rows × columnCount` cells + the two side columns + scrollbars.
3. Wire **scroll** (`scrollOffset`/`scrollOffsetX`) and **zoom** (`GridMode`) — view-only, never
   touch the model.
4. Bind **cell actions** (§4.2) to model mutations; push each edit live to the engine bridge.
5. Add **selection + clipboard** (§5) generalised to cell/row/multi-row/clip, with cut/copy/paste.
6. Drive **playback animation** (§7) from `updatePlayhead`: cursor, auto page-follow, velocity
   brightness, blink clock, note tails — all in-place, no rebuilds.
7. Bind to **`ProjectModel`** (§8); load/save via the **XML** layer (§9).
8. Preserve the **SHIFT modal layer** and hardware semantics (§10) for fidelity.
9. Keep one structural rule: **scroll/animate = in-place refresh; zoom/fold/view-switch =
   structural rebuild.** This is what keeps it fast and flicker-free.
