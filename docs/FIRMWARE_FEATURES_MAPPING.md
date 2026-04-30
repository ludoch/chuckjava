# Deluge Firmware Features & Menus — Java Implementation Status

> Last updated: 2026-04-29 (LiSa/WvOut audio + script loading)
> Source: [SynthstromAudible/DelugeFirmware/docs](https://github.com/SynthstromAudible/DelugeFirmware/tree/main/docs)

This document maps every documented hardware feature and menu from the official Deluge Firmware to our Java/ChucK implementation. Use it to track parity and prioritize future work.

---

## 1. Feature Status Overview

| Feature | Firmware Doc | Java/ChucK Status | Notes |
|---------|-------------|-------------------|-------|
| Arpeggiator | `features/arpeggiator.md` | ❌ Not implemented | 15+ parameters (modes, patterns, randomization, MPE) |
| Automation View | `features/automation_view.md` | ❌ Not implemented | 81 automatable params; per-step/zoom editing |
| Audio Recording | `features/audio_export.md` | ✅ Implemented | LiSa-based per-track recording, looping playback via audio_shred() |
| Audio Export | `features/audio_export.md` | ✅ Implemented | WvOut2-based WAV export via Export Audio... menu; offline mastered render |
| Chord Keyboard | `features/chord_keyboard.md` | ❌ Not implemented | CORK/CORL layouts, scale-aware chords |
| DX7 Synth | `features/dx_synth.md` | ❌ Not implemented | .syx compatibility, 6-op FM engine |
| Looping in Grid View | `features/looping_in_grid_view.md` | ❌ Not implemented | Green mode create+record, LOOP/LAYERING LOOP cmds |
| MIDI Device Definitions | `features/midi_device_definition_files.md` | ❌ Not implemented | CC name mapping XML per device |
| MIDI Follow Mode | `features/midi_follow_mode.md` | ❌ Not implemented | Auto-follow active clip; 3 channels + feedback |
| Note/NoteRow Editor | `features/note_noterow_editor.md` | ⚠️ Partial | Probability (per-step) works; iterance, fill, euclidean missing |
| Performance View | `features/performance_view.md` | ❌ Not implemented | 16 FX columns x 8 values; latch/momentary modes |
| Save/Load Patterns | `features/save_load_patterns.md` | ❌ Not implemented | Pattern XML save/load, MIDI file conversion |
| Velocity View | `features/velocity_view.md` | ✅ Implemented | See §1.6 of guidebook; velocity ramps, per-step editing |
| Vuefinder | `features/Vuefinder.md` | ➕ N/A | Web-based SD browser (hardware-specific; our Library tab supersedes) |

### Legend
- ✅ **Implemented** — Feature works and is documented
- ⚠️ **Partial** — Some sub-features exist, others do not
- ❌ **Not implemented** — Not present in codebase
- ➕ **N/A** — Not applicable (hardware-specific concern)

---

## 2. Menu System Status

The firmware organizes sound editing through 5 menu groups accessed via the SELECT encoder. Below is our per-parameter implementation status.

### 2.1 Compressor (`menus/compressor/`)

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Attack | `attack.md` | ⚠️ Partial | Compressor exists (sidechain ducking); attack parameter not exposed |
| Blend | `blend.md` | ❌ | No dry/wet blend |
| HPF | `hpf.md` | ❌ | No sidechain HPF |
| Ratio | `ratio.md` | ⚠️ Partial | Compression ratio not configurable |
| Release | `release.md` | ⚠️ Partial | 120ms recovery hardcoded (see guidebook §3) |
| Threshold | `threshold.md` | ❌ | No threshold control |
| **Index** | `index.md` | ❌ | Full compressor menu not implemented |

### 2.2 Envelope (`menus/envelope/`)

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Attack | `attack.md` (1-∞ ms) | ✅ | In SynthTrackModel, per-osc ADSR; in KitSound.adsr |
| Decay | `decay.md` | ✅ | Same — per-osc and per-kit-sound |
| Release | `release.md` (50-400 ms) | ✅ | Same |
| Sustain | `sustain.md` (0-1 level) | ✅ | Same |
| **Index** | `index.md` | ⚠️ Partial | ADSR values exist; no Envelope menu UI submenu

### 2.3 Filter (`menus/filter/`)

The firmware has 2 subdirectories (HPF, LPF) plus routing and sound-level configs — 14 menu pages total.

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| LPF Freq | `lpf/frequency.md` | ✅ | lpfFreq in SynthTrackModel |
| LPF Resonance | `lpf/resonance.md` | ✅ | lpfRes |
| LPF Mode | `lpf/mode.md` | ⚠️ Partial | Filter mode enum exists (LADDER_12/24/SVF) but firmware has more |
| LPF Morph | `lpf/morph.md` | ❌ | No morph control |
| LPF Drive | `lpf/drive.md` | ❌ | No filter drive |
| HPF Freq | `hpf/frequency.md` | ❌ | HPF fields exist in model, no UI or engine support |
| HPF Res | `hpf/resonance.md` | ❌ | — |
| HPF Mode/Morph/FM | `hpf/*.md` | ❌ | Entire HPF submenu missing |
| Routing | `routing.md` | ❌ | No filter routing (HPF→LPF, LPF→HPF, Parallel) |
| Sound Filters | `sound_filters.md` | ❌ | No per-sound filter in Kit |
| **Index** | `index.md` | ⚠️ Partial | Basic LPF works; 10/14 sub-pages missing |

### 2.4 LFO (`menus/lfo/`)

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Rate | `rate.md` (Hz) | ✅ | LFO rate in SynthTrackModel (per-voice + global) |
| Sync | `sync.md` | ❌ | No tempo-synced LFO |
| Type | `type.md` | ⚠️ Partial | LFO waveform enum exists (SINE/SAW/SQUARE/TRIANGLE/S&H/RANDOM_WALK/WARBLER) but no submenu UI |
| **Index** | `index.md` | ⚠️ Partial | LFOs exist (4 per synth) but no dedicated LFO menu |

### 2.5 Oscillator (`menus/oscillator/`)

3 subdirectories (modulator, sample, unison) + 9 top-level pages = 19 menu pages total.

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Type | `type.md` | ✅ | Sine/Saw/Square/Triangle/Noise/Sample/DX7 |
| Volume | `volume.md` | ✅ | Per-oscillator volume |
| Pulse Width | `pulse_width.md` | ✅ | Pulse width slider |
| Sync | `sync.md` | ✅ | Hard sync checkbox |
| Retrigger Phase | `retrigger_phase.md` | ✅ | 0-360° |
| Feedback | `feedback.md` | ✅ | FM feedback amount |
| Wave Index | `wave_index.md` | ❌ | No wavetable position |
| File Browser | `file_browser.md` | ✅ | Library tab |
| **Modulator 1/2** | `modulator/` | ⚠️ Partial | Volume/transpose/destination/feedback exist; retrigger phase missing |
| **Sample** | `sample/` (9 files) | ❌ | No sample osc submenu at all |
| **Unison** | `unison/` (4 files) | ❌ | Unison count/detune only; stereo spread missing |
| **Index** | `index.md` | ⚠️ Partial | Osc params exist in editor; ~7/19 sub-pages missing |
| **Index** | `index.md` | ⚠️ Partial | Oscillator params exist in editor; FM matrix limited |

---

## 3. Sub-Feature Detail: Arpeggiator

The firmware arpeggiator has ~25 configurable parameters across 4 groups. Our status:

| Parameter Group | Params | Status |
|----------------|--------|--------|
| **Basic (BASI)** | Gate, Sync, Rate | ❌ |
| **Pattern (PATT)** | Octaves, Octave Mode, Chord Sim, Note Mode, Step Repeat, Rhythm, Seq Length | ❌ |
| **Randomizer (RAND)** | Lock, Octave Spread, Gate Spread, Velocity Spread, Ratchet, Chord Poly, Note/Bass/Swap/Glide/Reverse Probability | ❌ |
| **MPE** | Velocity (via Aftertouch/Y) | ❌ |

## 4. Sub-Feature Detail: Automation View

The firmware automation view supports 81 automatable parameters with per-step grid editing at any zoom level:

| Capability | Status |
|-----------|--------|
| Automation Overview (81 param grid shortcuts) | ❌ |
| Per-step automation editing | ❌ |
| Long-press linear interpolation | ❌ |
| Automation copy/paste | ❌ |
| Live Mod Encoder recording | ❌ |
| Parameter automation for individual kit sounds | ❌ |
| MIDI CC automation (0-119 + Pitch Bend + Aftertouch) | ❌ |
| Automation per Arranger track (22 params) | ❌ |
| Automation per Kit with Affect-Entire (26 params) | ❌ |

## 5. Sub-Feature Detail: Performance View

| Capability | Status |
|-----------|--------|
| 16 FX columns with 8 values each | ❌ |
| Short-press latch / Long-press momentary | ❌ |
| Value Editing Mode | ❌ |
| Param Editing Mode (reassign columns) | ❌ |
| PerformanceView.xml save/load | ❌ |

## 6. Model Hierarchy — Firmware vs Java

| Level | Firmware (C++) | Java/ChucK | Parity |
|-------|---------------|------------|--------|
| Root | `Song` (firstOutput linked list, sessionClips, arrangementOnlyClips, currentScale) | `ProjectModel` (List<TrackModel>, bpm/swing/scale/master FX) | ⚠️ |
| Track | `Output` (activeClip, ClipInstanceVector, name, type, colour) | `TrackModel` (name, type, muted, volume, pan, color, List<ClipModel>) | ⚠️ |
| Clip | `Clip` (loopLength, output*, section, launchStyle, armState) | `ClipModel` (name, rowCount, stepCount, List<List<StepData>>) | ⚠️ |
| Note Row | `NoteRow`[] in InstrumentClip | Rows = List<List<StepData>> in ClipModel | ⚠️ |
| Note | `Note` (velocity, probability, lift, iterance, fill) | `StepData` (active, velocity, gate, probability, pitch) | ⚠️ |
| Per-sound FX | Per-drum FX in Kit | `KitSound` (sample params, adsr, lpf, eq) — no per-sound FX chain | ❌ |
| Parameter Seq | `ParamManager` per Clip, per NoteRow | Not implemented | ❌ |
| Timing | `insideWorldTickMagnitude`, `ticksPerLoop` | Simple step counter | ❌ |

### Key Model Gaps

1. **Track/Clip hierarchy**: Firmware allows multiple clips per track (session slots). Java has `List<ClipModel>` but UI only shows one active.
2. **Session vs Arranger**: Firmware has distinct modes. Java "Song View" is a hybrid.
3. **Per-drum FX**: Each Kit sound in firmware has independent FX chain. Java `KitSound` has basic params only.
4. **Parameter sequences (ParamManager)**: Clips should have per-parameter automation, not just note sequences. Firmware has `ParamManager` per Clip and per NoteRow.
5. **Timing model**: Firmware uses `insideWorldTickMagnitude` rational tick timing; Java uses simple integer step counter.
6. **No NoteRow layer**: Firmware `InstrumentClip` has `NoteRowVector` (per-row length, probability, iterance, note vectors). Java `ClipModel` uses a flat 2D grid.
7. **No Drum polymorphism**: Firmware has `SoundDrum`, `MIDIDrum`, `GateDrum` with distinct behaviors. Java `KitSound` is flat data.
8. **No AudioClip model**: Entirely absent from Java.
9. **No GlobalEffectable hierarchy**: Firmware has `GlobalEffectableForSong` and `GlobalEffectableForClip`. Java has bare reverb/delay floats on `ProjectModel`.
10. **No Consequence/undo system**: Firmware uses linked-list-of-Consequences with per-type reversal. Java `UndoRedoStack` is simpler.
11. **Instrument/Sound separation**: Firmware separates `MelodicInstrument` (note routing) from `Sound` (synthesis engine). Java puts synth params into `SynthTrackModel` directly.

---

## 7. Desktop-Exclusive Extensions (No Firmware Equivalent)

These features exist only in our software implementation and have no hardware counterpart:

| Feature | Location | Description |
|---------|----------|-------------|
| **Configurable Grid Mode** | Grid viewport | 4 viewport sizes (8×16, 16×16, 24×16, 16×24) |
| **Vertical/Horizontal Grid Scrolling** | Grid viewport | Scroll buttons when content exceeds viewport |
| **Multi-Tab Track Inspector** | Right-click pad | PRESETS/CLIPBOARD/MIXER tabs |
| **Step Properties Dialog** | Right-click pad cell | Set velocity, probability per step |
| **Row-Level Velocity/Probability** | Right-click row label | Apply to all steps in row |
| **MIDI Grid Controller Mode** | Preferences | Map incoming MIDI notes to grid coordinates |
| **Continuous Automation Drawing** | Bottom panel lane | Draw curves for step parameters |
| **Visualizer Stack** | Right panel | FFT, oscilloscope, waterfall, stereo phase |
| **Audio Recording/Playback** | Engine + UI | LiSa per-track audio clip recording + looping playback; rate control |
| **Master WAV Export** | File menu | WvOut2 spliced into master output chain; records mastered stereo mix to .wav |
| **Runtime Script Loading** | File menu | Load .ck scripts at runtime via vm.eval(); script access to all UGens + engine globals |
| **64 simultaneous tracks** | Engine | 8 kits × 8 sounds each |

---

## 8. Implementation Priority (Suggested)

Based on firmware docs, this is the recommended order for closing the most significant gaps:

1. **Automation View** — Largest impact: enables parameter modulation (81 params) and unlocks per-step automation editing. Foundation for most other features.
2. **Arpeggiator** — Well-specified with 25 parameters; natural addition to Synth/MIDI tracks.
3. **Performance View** — 16×8 FX grid is a natural fit for our grid UI; high demo value.
4. **Per-parameter sequences (ParamManager)** — Deep infrastructure change but unlocks automation.
5. **Track/Clip separation** — Multiple clips per track enables session-mode workflows.
6. **Per-drum FX chain** — Required for full Kit track parity.
7. **MIDI Follow Mode** — Important for external controller integration.
8. **Audio Export** — Needed for production use; moderate difficulty.
