# Hardcoded `16` Step Values — Complete Audit

> Generated: 2026-04-29
> Scope: All `.java` files in `deluge/src/`

This document catalogs every occurrence of the literal `16` in the deluge module and
classifies it. The root issue: the Deluge hardware allows up to **192 steps** per clip,
but our codebase hardcodes `16` as the step capacity in many places instead of reading
the variable `ClipModel.stepCount`.

---

## Category 1: `BridgeContract.STEPS` (The Central Constant)

`STEPS = 16` at `BridgeContract.java:13` serves **two conflated purposes**:

1. **Array stride** — `PATTERN_SIZE = TRACKS * STEPS` sizes all 14 step-data arrays
   to `64 × 16 = 1024` slots. Indexed `track * STEPS + step`.
2. **Max step capacity per clip** — Real Deluge supports up to 192 steps.

### Occurrences

| File | Line | Code | Action |
|------|------|------|--------|
| `BridgeContract.java` | 13 | `public static final int STEPS = 16;` | Change to `192` |
| `BridgeContract.java` | 14 | `PATTERN_SIZE = TRACKS * STEPS` | Auto-adjusts |
| `BridgeContract.java` | 293 | `trackLength.setInt(t, 16L)` | Keep as default |
| `BridgeContract.java` | 516 | `Math.min(16, steps)` in `setTrackLength()` | Remove clamp |

All `track * STEPS + step` accessors (lines 386, 396, 400, 404, 408, 412, 416, 420,
424, 428) auto-adjust when STEPS changes.

---

## Category 2: Engine DSL — Literal `16` (Duplicates STEPS)

These should use `BridgeContract.STEPS` (or no clamp at all).

| File | Line | Code | Fix |
|------|------|------|-----|
| `DelugeEngineDSL.java` | 242 | `Math.min(16, trkLen.getInt(r))` | Remove min(), use trkLen directly |
| `DelugeEngineDSL.java` | 244 | `r * 16 + step` | Use `r * BridgeContract.STEPS + step` |
| `DelugeEngineDSL.java` | 559 | `Math.min(16, trkLen.getInt(r))` | Remove min(), use trkLen directly |
| `DelugeEngineDSL.java` | 561 | `r * 16 + step` | Use `r * BridgeContract.STEPS + step` |
| `DelugeEngineDSL.java` | 637 | `idx % 16` (debug print) | Use `BridgeContract.STEPS` |

---

## Category 3: UI Data Push Loops — Should Use `clip.getStepCount()`

These iterate `s < 16` but the clip may be longer.

| File | Line | Loop | Fix |
|------|------|------|-----|
| `SwingDelugeApp.java` | 128 | `for (int s = 0; s < 16; s++)` (kit data push) | `s < clip.getStepCount()` |
| `SwingDelugeApp.java` | 149 | `for (int s = 0; s < 16; s++)` (synth data push) | `s < clip.getStepCount()` |
| `SwingDelugeApp.java` | 220 | `for (int s = 0; s < 16; s++)` (audio data push) | `s < clip.getStepCount()` |
| `SwingDelugeApp.java` | 996 | `for (int s = 0; s < 16; s++)` (onEditRequest clear) | `s < clip.getStepCount()` |
| `SwingDelugeApp.java` | 1004 | `for (int s = 0; s < 16; s++)` (onEditRequest push) | `s < clip.getStepCount()` |
| `SwingDelugeApp.java` | 232 | `track.getClips().isEmpty() ? 16 : ...` (track length) | Already uses stepCount |
| `SwingDelugeApp.java` | 750, 768, 786 | `new ClipModel("CLIP 1", 8, 16)` | Keep 16 as default |

---

## Category 4: Grid Rendering — Column Bounds

These should use `stepCount` (the instance field already derived from `gridMode.columns`).

| File | Line | Code | Fix |
|------|------|------|-----|
| `SwingGridPanel.java` | 34 | `stepCount = 16` | Fine, derived from gridMode |
| `SwingGridPanel.java` | 548 | `bridge.getTrackLength(modelRow) : 16` | Fine, fallback |
| `SwingGridPanel.java` | 554 | `stepLen == 16 ? Color.GRAY : ...` | Fine, comparing to default |
| `SwingGridPanel.java` | 992 | `colId < 16` (SONG view step bounds) | `colId < stepCount` |
| `SwingGridPanel.java` | 1010 | `16` as fallback in dialog | Use stepCount |
| `SwingGridPanel.java` | 1092 | `c < 16` (CLIP MACROS row) | `c < stepCount` |
| `SwingGridPanel.java` | 1108 | `c < 16` (CLIP SLIDERS row) | `c < stepCount` |
| `SwingGridPanel.java` | 1472 | `bridge.getTrackLength(trk) : 16` | Fine, fallback |
| `SwingGridPanel.java` | 1478 | `stepLen == 16` | Fine, default check |
| `SwingGridPanel.java` | 1530 | `colId < 16` (ARR SLIDERS row) | `colId < stepCount` |
| `SwingGridPanel.java` | 1558 | `c < 16` (ARR MACROS row) | `c < stepCount` |
| `SwingGridPanel.java` | 1572 | `colId < 16` (ARR SLIDERS row) | `colId < stepCount` |
| `SwingGridPanel.java` | 2038 | `colId < 16` (SONG step bounds) | `colId < stepCount` |
| `SwingGridPanel.java` | 2058 | `16` as default stepCount in dialog | Use stepCount |
| `SwingGridPanel.java` | 2233 | `currentStep >= 16` (one-shot mute) | `currentStep >= stepCount` |

---

## Category 5: Velocity Lane Panel

| File | Line | Code | Fix |
|------|------|------|-----|
| `SwingVelocityLanePanel.java` | 48 | `step >= 0 && step < 16` | Accept stepCount param |
| `SwingVelocityLanePanel.java` | 120 | `for (int i = 0; i < 16; i++)` | Use stepCount |

---

## Category 6: Matrix Panel (Older Grid)

| File | Line | Code | Fix |
|------|------|------|-----|
| `SwingMatrixPanel.java` | 46 | `"16"` as default N in Euclidian dialog | Keep (separate concern) |
| `SwingMatrixPanel.java` | 50 | `for (int i = 0; i < 16; i++)` | Use stepCount |
| `SwingMatrixPanel.java` | 85 | `(currentStep / 16) * 16` page offset | Use stepCount |
| `SwingMatrixPanel.java` | 87 | `>= 16 * cellW` spacer | Use stepCount |
| `SwingMatrixPanel.java` | 252 | `c < 16` step cell bounds | Use stepCount |
| `SwingMatrixPanel.java` | 288 | `c < 16` step rendering | Use stepCount |
| `SwingMatrixPanel.java` | 326 | `currentStep % 16` | Use stepCount |
| `SwingMatrixPanel.java` | 346 | `currentStep % 16` | Use stepCount |

### Column-16 = Mute/Solo Separator (Leave Alone)

These are about the mute/solo **column index** (always the 17th column), not step count:

- `SwingMatrixPanel.java:195, 245, 254, 261, 268, 269, 276, 301, 314` — all `c == 16`
- `SwingGridPanel.java:1672` — `colId == 16`

---

## Category 7: Test Files

| File | Line | Code | Fix |
|------|------|------|-----|
| `BridgeContractTest.java` | 34 | `assertEquals(16, BridgeContract.STEPS)` | Update to 192 |
| `BridgeContractTest.java` | 179 | `arr.getInt(1 * 16 + 8)` | Use STEPS |
| `MidiInputRouterTest.java` | 55 | `4 * 16 + 0` | Use STEPS |
| `ProjectModelTest.java` | 38 | `new ClipModel("Beat 1", 8, 16)` | Keep as default |
| `ProjectModelTest.java` | 43 | `assertEquals(16, ...)` | Keep, 16 is default |
| `DelugeNoteDataMapperTest.java` | 15 | `for (int i = 0; i < 16; i++)` | Use variable |
| `ProjectSerializerTest.java` | 25 | `new ClipModel("CLIP 1", 8, 16)` | Keep as default |
| `MuteTest.java` | 27 | `for (int s = 0; s < 16; s++)` | Use variable |
| `MuteTest.java` | 43 | `for (int s = 0; s < 16; s++)` | Use variable |
| `SoundTest.java` | 46 | `for (int s = 0; s < 16; s++)` | Use variable |
| `VerifySync.java` | 24, 29, 51, 78, 94 | `for (int s = 0; s < 16; s++)` | Use variable |

---

## Category 8: Non-Step `16`s (Leave Alone)

These are unrelated to step capacity:

| File | Line | Value | Reason |
|------|------|-------|--------|
| `SwingGridPanel.java` | 1253 | `Math.max(16, Math.min(200, padSz))` | Pixel size clamp |
| `SwingGridPanel.java` | 1672 | `colId == 16` | Mute/solo column index |
| `SwingMatrixPanel.java` | 161 | `JSlider(1, 16, 1)` | Gate duration ticks |
| `SwingMatrixPanel.java` | 195, 245, 254, 261, 268, 269, 276, 301, 314 | `c == 16` | Mute/solo column index |
| `SwingMatrixPanel.java` | 291 | `Font.BOLD, 16` | Font size |
| `SynthTrackModel.java` | 34 | `initialCapacity(16)` | HashMap sizing hint |
| `SwingProjectSidebarPanel.java` | 472 | `"Bitcrush Bits: 16"` | Bit depth label |
| `SwingProjectSidebarPanel.java` | 537 | Font size 16 | Font size |
| `DelugeNoteDataMapper.java` | 48 | Hex string offset | Data format |
| `BridgeContractTest.java` | 89 | `"4 envelopes × 4 params = 16 slots"` | Comment math |

---

## Completed Changes

| File | What Changed | Status |
|------|-------------|--------|
| `BridgeContract.java` | `STEPS = 192`, `PATTERN_SIZE = 12288`, removed `Math.min(16, steps)` clamp | ✅ |
| `DelugeEngineDSL.java` | Removed `Math.min(16, trkLen)` in both shreds; stride `r * BridgeContract.STEPS` | ✅ |
| `SwingDelugeApp.java` | All 5 clip push loops use `clip.getStepCount()`; onEditRequest uses `clipSteps` | ✅ |
| `SwingGridPanel.java` | Track length dialogs: `1-64` → `1-192`, use `BridgeContract.STEPS`; one-shot mute: `stepCount` | ✅ |
| `SwingVelocityLanePanel.java` | Added `stepCount` field; bounds check + render loop use it | ✅ |
| `SwingMatrixPanel.java` | Added `stepCount` field; step loops, page offset, playhead, bounds use it | ✅ |
| Test files (8 files) | `STEPS` assertions to 192; stride: `BridgeContract.STEPS`; clip loops: `getStepCount()` | ✅ |

### Key Decisions

1. **Grid panel `colId < 16` in SONG/MACROS/SLIDERS rows** — Left at 16. These represent either fixed 16 clip-slot columns (SONG view) or 16 parameter labels (MACROS/SLIDERS), not step capacity.
2. **Column-16 mute/solo separator** — All `c == 16` / `c >= 16` references left unchanged. These are fixed column indices, not step counts.
3. **ClipModel defaults** — `new ClipModel(name, rows, 16)` left as default step count. 16 is a sensible default for new clips.
4. **Gate slider (1-16), font sizes, bit depth labels, pixel clamps** — Left unchanged. These are unrelated to step capacity.
