# Deluge Firmware Features & Menus — Java Implementation Status

> Last updated: 2026-05-09 (v1.3.0 audit — 4 envelopes, 4 LFOs, Warbler, Dimension, polarity, VCNT, threshold recording)
> Source: Local `../DelugeFirmware` at commit matching community firmware **c1.3.0**

This document maps every documented hardware feature and menu from the official Deluge Firmware to our Java/ChucK implementation. Use it to track parity and prioritize future work.

---

## 1. Feature Status Overview

| Feature | Firmware Doc | Java/ChucK Status | Notes |
|---------|-------------|-------------------|-------|
| Arpeggiator | `features/arpeggiator.md` | ⚠️ Partial | 4 basic modes (UP/DOWN/UP_DOWN/RANDOM) with octaves, rate, gate. Missing: chord sim, note mode, step repeat, rhythm, seq length, randomization, MPE |
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
| 4 Envelopes | `kNumEnvelopes = 4` in `definitions_cxx.hpp` | ✅ | 4 envelopes per track with independent ADSR; ENV 0→volume, ENV 1→filter, ENV 2→pitch, ENV 3→pan. Envelope tab UI with 4 sub-panels |
| 4 LFOs | `LFO_COUNT = 4` in `definitions_cxx.hpp` | ✅ | 4 LFOs with all 7 waveform types (SINE/SAW/SQUARE/TRIANGLE/S&H/RANDOM_WALK/WARBLER). LFO 0/1 per-voice, LFO 2/3 global |
| Warbler FX | `ModFXType::WARBLE` in `definitions_cxx.hpp` | ✅ | Custom implementation: random-walk + sin LFO modulated delay-line with resonance-compensated feedback. Architecture differs from firmware (shared delay-line core) |
| Dimension FX | `ModFXType::DIMENSION` in `definitions_cxx.hpp` | ✅ | Custom implementation: 3 DelayL voices at 8/14/20ms base delays, triangle LFO, independent phases. Architecture differs from firmware (shared delay-line core) |
| Patch Cable Polarity | `PatchCable::polarity` (UNIPOLAR/BIPOLAR) | ✅ | Per-cable polarity field (UNIPOLAR/BIPOLAR) on all patch cable arrays. UI polarity toggle in modulation tab |
| Voice Count (VCNT) | `Sound::maxVoiceCount` | ✅ | Per-track max voice limit (0-8), per-voice active tracking, voice stealing (oldest voice replaced when at limit). PolyphonyMode: POLY/MONO/LEGATO/AUTO/CHOKE |
| Threshold Recording | `ThresholdRecordingMode` enum + SEC/ENC controls | ✅ | 4 threshold modes (OFF/LOW/MEDIUM/HIGH) with state machine (IDLE→RECORDING→STOP with 500ms hold) |

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

Firmware has **4 envelopes** per sound (`kNumEnvelopes = 4`). ENV 0 and ENV 2 share the ENV1 shortcut pad via layered shortcuts; ENV 1 and ENV 3 share ENV2. Java has 4 envelopes per track with independent ADSR, default routing (ENV0→volume, ENV1→filter, ENV2→pitch, ENV3→pan), and a full ENVELOPE UI tab with 4 sub-panels.

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Attack | `attack.md` (1-∞ ms) | ✅ | Per-env ADSR via ChuckArray; 4 envelopes accessible in ENVELOPE tab |
| Decay | `decay.md` | ✅ | Same |
| Release | `release.md` (50-400 ms) | ✅ | Same |
| Sustain | `sustain.md` (0-1 level) | ✅ | Same |
| **Index** | `index.md` | ✅ | Full 4-envelope UI tab with sub-panels for each envelope; target combo per env |

### 2.3 Filter (`menus/filter/`)

Firmware filter modes: `TRANSISTOR_12DB`, `TRANSISTOR_24DB`, `TRANSISTOR_24DB_DRIVE`, `SVF_BAND`, `SVF_NOTCH`, `HPLADDER`, `OFF`. Filter routing: `HIGH_TO_LOW`, `LOW_TO_HIGH`, `PARALLEL`. HPF has its own LADDER + SVF modes. The firmware has **HPF frequency, resonance, morph, FM, and mode sub-pages** — all fully implemented in C++ but marked ❌ in the mapping because they're absent from Java.

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| LPF Freq | `lpf/frequency.md` | ✅ | lpfFreq in SynthTrackModel |
| LPF Resonance | `lpf/resonance.md` | ✅ | lpfRes |
| LPF Mode | `lpf/mode.md` | ⚠️ Partial | Filter mode enum exists in Java; firmware has 3 ladder + 2 SVF modes + morph + drive |
| LPF Morph | `lpf/morph.md` | ❌ | No morph; firmware has dry/wet blend for filter transitions |
| LPF Drive | `lpf/drive.md` | ✅ | SVFilter drive with tanh soft-clip saturation (0.0–2.0); drive slider in UI |
| HPF Freq | `hpf/frequency.md` | ❌ | HPF fields exist in model, no UI or engine support |
| HPF Res | `hpf/resonance.md` | ❌ | — |
| HPF Mode/Morph/FM | `hpf/*.md` | ❌ | Entire HPF submenu missing |
| Routing | `routing.md` | ✅ | 3 filter routing modes: SERIES_LPF_HPF, SERIES_HPF_LPF, PARALLEL; route combo in UI |
| Sound Filters | `sound_filters.md` | ✅ | Per-sound SVFilter + HPF in Kit tracks (kitFil[]/kitHpf[] per voice) |
| **Index** | `index.md` | ⚠️ Partial | Basic LPF works; 10/14 sub-pages missing |

### 2.4 LFO (`menus/lfo/`)

Firmware has **4 LFOs** (`LFO_COUNT = 4`): LFO1 (global), LFO2 (per-voice), LFO3 (global), LFO4 (per-voice). Layered shortcuts cycle LFO1↔LFO3, LFO2↔LFO4. LFO types: `SINE`, `TRIANGLE`, `SQUARE`, `SAW`, `SAMPLE_AND_HOLD`, `RANDOM_WALK`, `WARBLER` (random-curve LFO waveform used by the Warbler FX).

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Rate | `rate.md` (Hz) | ✅ | 4 LFOs with independent rates via ChuckArray |
| Sync | `sync.md` | ❌ | No tempo-synced LFO |
| Type | `type.md` | ✅ | All 7 LFO waveform types: SINE/SAW/SQUARE/TRIANGLE/S&H/RANDOM_WALK/WARBLER |
| **Index** | `index.md` | ✅ | 4 LFOs, full UI tab with type/rate/depth/target per LFO |

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

### 2.6 Modulation (`menus/modulation/`)

Firmware `PatchSource` enum has 15 source types: `LFO_GLOBAL_1`, `LFO_GLOBAL_2`, `SIDECHAIN`, `ENVELOPE_0`, `ENVELOPE_1`, `ENVELOPE_2`, `ENVELOPE_3`, `LFO_LOCAL_1`, `LFO_LOCAL_2`, `X`, `Y`, `AFTERTOUCH`, `VELOCITY`, `NOTE`, `RANDOM`. Each patch cable has a `polarity` field (UNIPOLAR/BIPOLAR toggled via Press+Turn select encoder).

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Patch Cables | — | ✅ | Full patch cable arrays (source/dest/amount/polarity) per track, up to 16 cables per track |
| Mod Knobs | — | ✅ | 4×4 grid of 16 knob param selectors in MODULATION tab |
| Source options | — | ⚠️ Partial | velocity, envelope 1-2, lfo 1-2, aftertouch, note, random, sidechain — missing envelope 3-4, lfo global 1-2, lfo local 2, X, Y |
| Destination options | — | ✅ | volume, pan, lpfFrequency, lpfResonance, oscAVolume, oscBVolume, pitch, noiseVolume, modFxRate, modFxDepth |

### 2.7 Kit Assembly

| Feature | Status | Details |
|---------|--------|---------|
| Assemble Kit From Synths | ✅ | File → Assemble Kit From Synths... selects N synth XMLs, per-lane mute group/pitch offset, outputs .KIT XML |

### 2.8 Voice (`menus/voice/`)

Firmware `PolyphonyMode` enum: `AUTO`, `POLY`, `MONO`, `LEGATO`, `CHOKE`. `Sound::maxVoiceCount` (0-8) per-instrument voice limit. `Unison`: count (1-8), detune, stereo spread.

| Feature | Firmware | Java/ChucK Status | Details |
|---------|----------|-------------------|---------|
| Polyphony Mode | `PolyphonyMode` (AUTO/POLY/MONO/LEGATO/CHOKE) | ✅ | All 5 modes implemented (POLY/MONO/LEGATO/AUTO/CHOKE) with per-track voice stealing |
| Voice Count (VCNT) | `Sound::maxVoiceCount` (0-8) | ✅ | Per-track max voice limit via `G_MAX_VOICES`; voice stealing replaces oldest voice at limit |
| Unison Count | `Sound::numUnison` (1-8, `kMaxNumVoicesUnison`) | ⚠️ Partial | Unison count exists; stereo spread missing |
| Unison Detune | `kMaxUnisonDetune = 50` | ❌ | No unison detune control |
| Unison Stereo Spread | `kMaxUnisonStereoSpread = 50` | ❌ | No stereo spread |

### 2.9 Mod FX (`menus/mod_fx/`)

Firmware `ModFXType` enum: `NONE`, `FLANGER`, `CHORUS`, `PHASER`, `CHORUS_STEREO`, `WARBLE`, `DIMENSION`, `GRAIN`. All share `ModFXProcessor` with configurable depth/feedback/offset. Warbler uses `LFOType::WARBLER` as its LFO waveform (random second-order-filtered curve). Dimension is a Boss-style stereo chorus. Java has basic chorus/flanger only.

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Type | `ModFXType` enum (8 types) | ✅ | All 8 types: CHORUS, FLANGER, PHASER, CHORUS_STEREO, WARBLE, DIMENSION, GRAIN; custom DSP implementations |
| Depth | `kModFXParam::DEPTH` | ✅ | modFxDepth parameter |
| Feedback | `kModFXParam::FEEDBACK` | ⚠️ Partial | Basic feedback; firmware has resonance-compensated feedback curves (32-bit cubic) |
| Offset | `kModFXParam::OFFSET` | ✅ | Delay offset control (`G_MOD_FX_OFFSET`); offset slider in UI |

---

## 3. Sub-Feature Detail: Arpeggiator

The firmware arpeggiator has ~25 configurable parameters across 4 groups. Our status:

| Parameter Group | Params | Status |
|----------------|--------|--------|
| **Basic (BASI)** | Gate, Sync, Rate | ⚠️ Partial | Gate/rate work; sync not implemented |
| **Pattern (PATT)** | Octaves, Octave Mode, Chord Sim, Note Mode, Step Repeat, Rhythm, Seq Length | ⚠️ Partial | Octaves + 4 octave modes (UP/DOWN/UP_DOWN/RANDOM) implemented; chord sim, note mode, step repeat, rhythm, seq length missing |
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
| Envelopes | 4 per voice (`kNumEnvelopes = 4`, `std::array<Envelope, kNumEnvelopes>`) | 4 per track with ADSR + targets | ✅ |
| LFOs | 4 per sound (`LFO_COUNT = 4`, `LFO globalLFO1/3`, `Voice::lfo2/lfo4`) | 4 LFOs, all 7 waveform types | ✅ |
| Patch Cable Polarity | `PatchCable::polarity` (UNIPOLAR/BIPOLAR) | Implemented (UNIPOLAR/BIPOLAR per cable) | ✅ |
| Mod FX Types | `ModFXType` with 8 types (incl. WARBLE, DIMENSION, GRAIN) | All 8 types implemented | ✅ |

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
12. **4 Envelopes**: Firmware `Voice::envelopes` is `std::array<Envelope, kNumEnvelopes>` with independent ADSR for ENV 0-3. Java has 1 envelope per oscillator.
13. **4 LFOs**: Firmware `Sound::globalLFO1/3` plus `Voice::lfo2/lfo4` with per-voice local LFOs. Java has 2 LFOs.
14. **Patch Cable Polarity**: Each firmware patch cable has a UNIPOLAR/BIPOLAR toggle. Java patch cables are always bipolar.
15. **Mod FX Types**: Firmware has 8 ModFX types including `WARBLE` (modulated delay with random-walk LFO) and `DIMENSION` (stereo chorus). Java only has chorus/flanger/phaser.
16. **Voice Count (VCNT)**: Firmware `Sound::maxVoiceCount` (0-8) limits simultaneous voices per instrument. Java has no voice limit.
17. **Threshold Recording**: Firmware `ThresholdRecordingMode` (OFF/LOW/MEDIUM/HIGH) gates audio recording start on input signal level. Java always records immediately.

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

Based on firmware sources (v1.3.0 community), this is the recommended order for closing the most significant gaps:

1. **4 Envelopes + 4 LFOs** — Foundation for modulation parity; extends the existing envelope/LFO model (moderate effort, unlocks §2.6 modulation source completeness)
2. **Automation View** — Largest impact: enables parameter modulation (81 params) and unlocks per-step automation editing. Foundation for most other features.
3. **Patch Cable Polarity** — Small change to the existing modulation UI; adds UNIPOLAR/BIPOLAR toggle per cable
4. **Arpeggiator** — Well-specified with 25 parameters; natural addition to Synth/MIDI tracks.
5. **Voice Count (VCNT)** — Trim-to-fit voice management in the engine; meaningful for polyphony control
6. **Performance View** — 16×8 FX grid is a natural fit for our grid UI; high demo value.
7. **Per-parameter sequences (ParamManager)** — Deep infrastructure change but unlocks automation.
8. **Track/Clip separation** — Multiple clips per track enables session-mode workflows.
9. **Missing Mod FX types (Warbler, Dimension, Grain)** — New DSP building on existing ModFXProcessor pattern
10. **Per-drum FX chain** — Required for full Kit track parity.
11. **MIDI Follow Mode** — Important for external controller integration.
12. **Audio Export** — Already partially implemented; cleanup only.
13. **Threshold Recording** — Audio-engine level gating for recording start
