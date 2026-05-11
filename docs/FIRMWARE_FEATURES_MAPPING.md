# Deluge Firmware Features & Menus — Java Implementation Status

> Last updated: 2026-05-11 (Unison engine in SynthShred + KitShred; doc cleanup)
> Source: Local `../DelugeFirmware` at commit matching community firmware **c1.3.0**

This document maps every documented hardware feature and menu from the official Deluge Firmware to our Java/ChucK implementation. Use it to track parity and prioritize future work.

---

## 1. Feature Status Overview

| Feature | Firmware Doc | Java/ChucK Status | Notes |
|---------|-------------|-------------------|-------|
| Arpeggiator | `features/arpeggiator.md` | ✅ | All 9 note modes (UP/DOWN/UPDN/RAND/WLK1-3/PLAY/PATT), 5 octave modes (UP/DOWN/UPDN/ALT/RAND), stepRepeat, rhythm patterns with silences, seqLength, noteProbability, chordPolyphony+probability, ratchet, octave/gate/vel spread. MPE missing.
| Automation View | `features/automation_view.md` | ✅ Implemented | BarAutomationDialog, AutomationParam model, per-step editing, XML save/load, MIDI CC |
| Audio Recording | `features/audio_export.md` | ✅ Implemented | LiSa-based per-track recording, looping playback via audio_shred(); pre-existing WAV file loading via AudioClip.filePath → WavReader → LiSa |
| Audio Export | `features/audio_export.md` | ✅ Implemented | WvOut2-based WAV export via Export Audio... menu; offline mastered render |
| Chord Keyboard | `features/chord_keyboard.md` | ✅ Implemented | CORK/CORL layouts, scale-aware chords, 6 voicing modes |
| DX7 Synth | `features/dx_synth.md` | ✅ Implemented | 6-op FM engine (Dx7Engine), .syx import/export (Dx7SyxParser), 32 algorithms, operator editor UI, DX7 tab, XML round-trip, Vintage/Modern/Auto engine type toggle. **Note:** envelope shape uses dexed/msfa log-domain envelopes (not standard ADSR); track-level DelugeAdsr bypassed for DX7 tracks — per-operator DX7 envelopes control amplitude directly |
| Hardware Character (Master Sat, Filter Drive, 14-bit DAC, Rings Reverb) | — | ✅ Implemented | User preferences for hardware-accurate audio character: tanh master bus saturation, v1.3.1+ filter drive (SVFilter tanh at drive > 1.0), 14-bit DAC truncation with TPDF dither, RingsReverb physical-modeling reverb. Toggled via Settings → Preferences. See §Preferences in guidebook. |
| Looping in Grid View | `features/looping_in_grid_view.md` | ✅ Implemented | ClipModel.PlayMode.LOOP with context menu, engine auto-re-queue, green rendering in SONG view |
| MIDI Device Definitions | `features/midi_device_definition_files.md` | ✅ Implemented | MidiDeviceDefinition XML model, loader, preferences, feedback service, UI browser |
| MIDI Follow Mode | `features/midi_follow_mode.md` | ✅ Implemented | MidiInputRouter, 3 follow channels, feedback light piping, auto-clip-follow |
| Note/NoteRow Editor | `features/note_noterow_editor.md` | ⚠️ Partial | Probability (per-step) works; iterance, fill, euclidean missing |
| Performance View | `features/performance_view.md` | ✅ Implemented | 16×8 FX column grid, latch/momentary, value editing, param editing, XML save/load |
| Save/Load Patterns | `features/save_load_patterns.md` | ✅ Implemented | PatternModel + PatternSerializer, ClipSnapshot grid state, XML save/load, sidebar UI |
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
| Attack | `attack.md` | ✅ | Per-song attack via G_MASTER_COMP_ATTACK + engine RMSFeedbackCompressor mapping |
| Blend | `blend.md` | ✅ | Per-song dry/wet blend via G_MASTER_COMP_BLEND + comp.dryWet() |
| HPF | `hpf.md` | ❌ | No sidechain HPF |
| Ratio | `ratio.md` | ✅ | Per-song ratio via G_MASTER_COMP_RATIO + engine RMSFeedbackCompressor mapping |
| Release | `release.md` | ✅ | Per-song release via G_MASTER_COMP_RELEASE + engine RMSFeedbackCompressor mapping |
| Threshold | `threshold.md` | ✅ | Per-song threshold via G_SP_COMPRESSOR_THRESHOLD (song param overrides knob formula when non-zero) |
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
| HPF Freq | `hpf/frequency.md` | ✅ | KitShred applies `G_KIT_HPF_FREQ` to per-voice Butterworth HPF every tick (line 1346); per-synth HPF via `G_SP_HPF_FREQ` in MasterShred (line 482); model + UI slider + bridge global |
| HPF Res | `hpf/resonance.md` | ✅ | KitShred applies `G_KIT_HPF_RES` to per-voice HPF Q every tick (line 1347) |
| HPF Mode/Morph/FM | `hpf/*.md` | ❌ | `G_KIT_HPF_MODE` + `G_KIT_HPF_MORPH` bridge globals registered and forwarded as per-track globals, but Butterworth HPF UGen has no mode/morph support |
| Routing | `routing.md` | ✅ | 3 filter routing modes: SERIES_LPF_HPF, SERIES_HPF_LPF, PARALLEL; route combo in UI |
| Sound Filters | `sound_filters.md` | ✅ | Per-sound SVFilter + HPF in Kit tracks (kitFil[]/kitHpf[] per voice) |
| **Index** | `index.md` | ⚠️ Partial | Basic LPF/HPF works; 10/14 sub-pages missing |

### 2.4 LFO (`menus/lfo/`)

Firmware has **4 LFOs** (`LFO_COUNT = 4`): LFO1 (global), LFO2 (per-voice), LFO3 (global), LFO4 (per-voice). Layered shortcuts cycle LFO1↔LFO3, LFO2↔LFO4. LFO types: `SINE`, `TRIANGLE`, `SQUARE`, `SAW`, `SAMPLE_AND_HOLD`, `RANDOM_WALK`, `WARBLER` (random-curve LFO waveform used by the Warbler FX).

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Rate | `rate.md` (Hz) | ✅ | 4 LFOs with independent rates via ChuckArray |
| Sync | `sync.md` | ✅ | lfoSyncRate() in KitShred (line 1183) and SynthShred (line 1985) reads G_LFO_SYNC_LEVEL; works for LFO 0-3 |
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
| Wave Index | `wave_index.md` | ✅ | Wavetable position (0.0-1.0) per oscillator, model+bridge+engine+UI+XML |
| File Browser | `file_browser.md` | ✅ | Library tab |
| **Modulator 1/2** | `modulator/` | ⚠️ Partial | Volume/transpose/destination/feedback exist; retrigger phase missing |
| **Sample** | `sample/` (9 files) | ❌ | No sample osc submenu at all |
| **Unison** | `unison/` (4 files) | ✅ | SynthShred spawns sub-voice MorphingWavetable instances per slot (up to 8), applies detune (±cents), stereo spread (phase offset). KitShred spawns sub-SndBuf instances with detuned playback rates and per-sub Pan2 stereo spread. Bridge globals `G_UNISON_NUM/DETUNE/SPREAD` / `G_KIT_UNISON_NUM/DETUNE/SPREAD` read per-step. |
| **Index** | `index.md` | ⚠️ Partial | Osc params exist in editor; ~7/19 sub-pages missing |

### 2.6 Modulation (`menus/modulation/`)

Firmware `PatchSource` enum has 15 source types: `LFO_GLOBAL_1`, `LFO_GLOBAL_2`, `SIDECHAIN`, `ENVELOPE_0`, `ENVELOPE_1`, `ENVELOPE_2`, `ENVELOPE_3`, `LFO_LOCAL_1`, `LFO_LOCAL_2`, `X`, `Y`, `AFTERTOUCH`, `VELOCITY`, `NOTE`, `RANDOM`. Each patch cable has a `polarity` field (UNIPOLAR/BIPOLAR toggled via Press+Turn select encoder).

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Patch Cables | — | ✅ | Full patch cable arrays (source/dest/amount/polarity) per track, up to 16 cables per track |
| Mod Knobs | — | ✅ | 4×4 grid of 16 knob param selectors in MODULATION tab |
| Source options | — | ⚠️ Partial | velocity, envelope 1-2, lfo 1-2, aftertouch, note, random, sidechain — missing envelope 3-4, lfo global 1-2, lfo local 2, X, Y |
| MPE (MIDI Polyphonic Expression) | — | ❌ | `mpeVelocity` field parsed from XML into `ArpModel`, but engine has no per-note pitch-bend, per-note release velocity, or 14-bit MIDI resolution. MIDI bridge treats all data as standard 7-bit. See §8.2 item 9. |
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
| Unison Count | `Sound::numUnison` (1-8, `kMaxNumVoicesUnison`) | ✅ | SynthShred spawns up to 8 sub-voices per slot; power-normalized gain; `G_UNISON_NUM` read per-step from bridge |
| Unison Detune | `kMaxUnisonDetune = 50` | ✅ | Sub-voice frequency = baseFreq × 2^(detuneCents × offset / 1200); symmetric distribution |
| Unison Stereo Spread | `kMaxUnisonStereoSpread = 50` | ✅ | Sub-voice phase offset for stereo width; distributed across ±spread range |

### 2.9 Mod FX (`menus/mod_fx/`)

Firmware `ModFXType` enum: `NONE`, `FLANGER`, `CHORUS`, `PHASER`, `CHORUS_STEREO`, `WARBLE`, `DIMENSION`, `GRAIN`. All share `ModFXProcessor` with configurable depth/feedback/offset. Warbler uses `LFOType::WARBLER` as its LFO waveform (random second-order-filtered curve). Dimension is a Boss-style stereo chorus. Java has basic chorus/flanger only.

| Menu Page | Firmware Params | Java/ChucK Status | Details |
|-----------|----------------|-------------------|---------|
| Type | `ModFXType` enum (8 types) | ✅ | All 8 types: CHORUS, FLANGER, PHASER, CHORUS_STEREO, WARBLE, DIMENSION, GRAIN; custom DSP implementations |
| Depth | `kModFXParam::DEPTH` | ✅ | modFxDepth parameter |
| Feedback | `kModFXParam::FEEDBACK` | ⚠️ Partial | Basic feedback; firmware has resonance-compensated feedback curves (32-bit cubic) |
| Offset | `kModFXParam::OFFSET` | ✅ | Delay offset control (`G_MOD_FX_OFFSET`); offset slider in UI |

### 2.10 Bridge Global Status (Per-Kit Extended Parameters)

These `G_KIT_*` globals are registered in `BridgeContract.java` with UI controls and are read every tick by `KitShred`. They are forwarded as per-track `_r` suffixed globals for downstream use. Whether the engine applies them depends on the UGen capabilities:

| Bridge Global | Engine Reads? | Applied? | Notes |
|--------------|--------------|----------|-------|
| `G_KIT_HPF_MODE` | ✅ Read every tick | ❌ Not applied | Per-track HPF is a Butterworth (no mode/morph); value stored as `G_KIT_HPF_MODE_#` global |
| `G_KIT_HPF_MORPH` | ✅ Read every tick | ❌ Not applied | Same — Butterworth HPF has no morph parameter |
| `G_KIT_OSC2_TYPE` | ✅ Read every tick | ❌ Not applied | Kit voices use SndBuf (sample playback), no osc2 type concept |
| `G_KIT_UNISON_NUM` | ✅ Read every tick | ✅ Applied | KitShred spawns sub-SndBuf instances with detuned rate and stereo spread per-sub Pan2 |
| `G_KIT_UNISON_DETUNE` | ✅ Read every tick | ❌ Not applied | Same |
| `G_KIT_UNISON_SPREAD` | ✅ Read every tick | ❌ Not applied | Same |
| `G_KIT_WAVE_INDEX` | ✅ Read every tick | ❌ Not applied | Kit voices use SndBuf (not wavetable); value stored as global |
| `G_KIT_DELAY_RATE` | ✅ Read every tick | ❌ Not applied | Kit delay rate is per-voice FX routing; stored for potential FX bus readers |
| `G_KIT_DELAY_FB` | ✅ Read every tick | ❌ Not applied | Same |
| `G_KIT_MAX_VOICES` | ✅ Read every tick | ❌ Not applied | No per-voice voice stealing in kit (all voices are static SndBufs) |
| `G_KIT_POLYPHONY` | ✅ Read every tick | ❌ Not applied | Kit polyphony mode stored but all voices are always active |

#### Per-Step Automation Globals

All three now read and applied during step processing (SynthShred + KitShred):

| Bridge Global | Bridge Status | UI Status | Engine Status |
|--------------|--------------|-----------|---------------|
| `G_STEP_FILTER_MODE` | Registered (Float) | Step editor toggle | ✅ Read, maps discrete modes (0=LP,1=BP,2=HP,3=NOTCH) to SVFilter morph + notchMode |
| `G_STEP_DELAY` | Registered (Float) | Step delay amount | ✅ Read, overrides per-track delay send when > 0 |
| `G_STEP_REVERB` | Registered (Float) | Step reverb amount | ✅ Read, overrides per-track reverb send when > 0 |

---

## 3. Sub-Feature Detail: Arpeggiator

The firmware arpeggiator has ~25 configurable parameters across 4 groups. Our status (updated 2026-05-11: sync/ratchet/randomizer parsers added):

| Parameter Group | Params | Status |
|----------------|--------|--------|
| **Basic (BASI)** | Gate, Sync, Rate | ⚠️ Partial | Gate, rate, and sync work; `lfoSyncRate()` in engine maps sync level → note divisions |
| **Pattern (PATT)** | Octaves, Octave Mode, Chord Sim, Note Mode, Step Repeat, Rhythm, Seq Length | ⚠️ Partial | Octaves + 4 octave modes (UP/DOWN/UP_DOWN/RANDOM) + ratchet (0-4 sub-divisions, engine + UI slider) work; chord sim, note mode, step repeat, rhythm, seq length missing |
| **Randomizer (RAND)** | Lock, Octave Spread, Gate Spread, Velocity Spread, Ratchet, Chord Poly, Note/Bass/Swap/Glide/Reverse Probability | ⚠️ Partial | Ratchet amount/probability + all 7 probability params parsed from XML into `ArpModel`, bridge globals registered, engine reads ratchet per-voice. Missing: lock, octave/gate/velocity spread, chord poly engine |
| **MPE** | Velocity (via Aftertouch/Y) | ❌ | `mpeVelocity` parsed from XML into `ArpModel` field; no engine behavior. See §8.2 item 9. |

## 4. Sub-Feature Detail: Automation View

The firmware automation view supports 81 automatable parameters with per-step grid editing at any zoom level. Our implementation:

| Capability | Status |
|-----------|--------|
| Automation Overview (81 param grid shortcuts) | ✅ |
| Per-step automation editing | ✅ |
| Long-press linear interpolation | ✅ |
| Automation copy/paste | ✅ |
| Live Mod Encoder recording | ⚠️ Partial — MIDI CC in, mod encoder routing WIP |
| Parameter automation for individual kit sounds | ⚠️ Partial — per-track, not per-sound in kit |
| MIDI CC automation (0-119 + Pitch Bend + Aftertouch) | ✅ |
| Automation per Arranger track (22 params) | ✅ |
| Automation per Kit with Affect-Entire (26 params) | ✅ |

## 5. Sub-Feature Detail: Performance View

| Capability | Status |
|-----------|--------|
| 16 FX columns with 8 values each | ✅ |
| Short-press latch / Long-press momentary | ✅ |
| Value Editing Mode | ✅ |
| Param Editing Mode (reassign columns) | ✅ |
| PerformanceView.xml save/load | ✅ |

## 6. Model Hierarchy — Firmware vs Java

| Level | Firmware (C++) | Java/ChucK | Parity |
|-------|---------------|------------|--------|
| Root | `Song` (firstOutput linked list, sessionClips, arrangementOnlyClips, currentScale) | `ProjectModel` (List<TrackModel>, bpm/swing/scale/master FX, PatternModel) | ⚠️ |
| Track | `Output` (activeClip, ClipInstanceVector, name, type, colour) | `TrackModel` (name, type, muted, volume, pan, color, List<ClipModel>, clips with automation) | ⚠️ |
| Clip | `Clip` (loopLength, output*, section, launchStyle, armState) | `ClipModel` (name, rowCount, stepCount, List<List<StepData>>, AutomationParam[]) | ⚠️ |
| Note Row | `NoteRow`[] in InstrumentClip | Rows = List<List<StepData>> in ClipModel | ⚠️ |
| Note | `Note` (velocity, probability, lift, iterance, fill) | `StepData` (active, velocity, gate, probability, pitch) | ⚠️ |
| Per-sound FX | Per-drum FX in Kit | `KitSound` (sample params, adsr, lpf, eq) — no per-sound FX chain | ❌ |
| Parameter Seq / Automation | `ParamManager` per Clip, per NoteRow | `AutomationParam[]` per ClipModel; per-bar + per-step automation; XML save/load | ✅ |
| Timing | `insideWorldTickMagnitude`, `ticksPerLoop` | Simple step counter | ❌ |
| Envelopes | 4 per voice (`kNumEnvelopes = 4`, `std::array<Envelope, kNumEnvelopes>`) | 4 per track with ADSR + targets | ✅ |
| LFOs | 4 per sound (`LFO_COUNT = 4`, `LFO globalLFO1/3`, `Voice::lfo2/lfo4`) | 4 LFOs, all 7 waveform types | ✅ |
| Patch Cable Polarity | `PatchCable::polarity` (UNIPOLAR/BIPOLAR) | Implemented (UNIPOLAR/BIPOLAR per cable) | ✅ |
| Mod FX Types | `ModFXType` with 8 types (incl. WARBLE, DIMENSION, GRAIN) | All 8 types implemented | ✅ |
| DX7 Synth | 6-op FM, 32 algorithms, .syx | Dx7Engine, Dx7Patch, Dx7SyxParser, 32 algos, operator UI tab | ✅ |
| Performance View | 16×8 FX column grid | SwingPerformanceViewPanel, latch/momentary, param edit, XML | ✅ |
| MIDI Follow | 3 channels, auto-follow, feedback | MidiInputRouter, MidiFeedbackService, device definitions | ✅ |
| Patterns | Save/load clip state | PatternModel + PatternSerializer + sidebar UI | ✅ |
| Chord Keyboard | CORK/CORL layouts | SwingChordKeyboardPanel, scale-aware chords | ✅ |

### Key Model Gaps

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
| **Audio Recording/Playback** | Engine + UI | LiSa per-track audio clip recording + looping playback; rate control; pre-existing WAV file loading from AudioClip.filePath |
| **Master WAV Export** | File menu | WvOut2 spliced into master output chain; records mastered stereo mix to .wav |
| **Runtime Script Loading** | File menu | Load .ck scripts at runtime via vm.eval(); script access to all UGens + engine globals |
| **64 simultaneous tracks** | Engine | 8 kits × 8 sounds each |

---

## 8. Remaining Gaps

### 8.1 XML Parsing Gaps (Concrete, Fixable in `DelugeXmlParser.java`)

These are XML attributes/sub-elements that the DelugeFirmware C++ code writes and reads but our Java parser silently ignores. All verified against firmware source (`../DelugeFirmware`).

| # | Gap | Status | Java Impact | Priority |
|---|-----|--------|-------------|----------|
| 1 | **`<stutter>` element** — `quantized`, `reverse`, `pingPong` sub-attributes | ✅ Fixed | Stutter config now parsed in `populateSynth()` and `parseSoundDrum()` | HIGH |
| 2 | **`oscillatorSync` attribute on `<osc2>`** | ✅ Fixed | OscillatorSync field on `SynthTrackModel`, parsed in `populateSynth()` | HIGH |
| 3 | **Osc2 missing sample-playback attrs** — `loopMode`, `reversed`, `timeStretchEnable`, `timeStretchAmount`, `linearInterpolation` on `<osc2>` | ✅ Fixed | Model fields + parser on both `SynthTrackModel` and `SoundDrum` | MEDIUM |
| 4 | **`linearInterpolation` attribute on sample osc** | ✅ Fixed | `osc1LinearInterpolation` field + getter/setter on `SynthTrackModel`, parsed in osc1 block | MEDIUM |
| 5 | **`dx7randomdetune` + `dx7enginemode` attributes on DX7 osc block** | ✅ Fixed | `dx7RandomDetune` field + `engineType` (maps to dx7enginemode) parsed from osc1 attrs | MEDIUM |
| 6 | **`startLoopPos`/`endLoopPos` in `<zone>`** | ✅ Fixed | Parsed in `parseZoneFromOsc()` (osc1 zones) and osc2 zone block in `parseSoundDrum()` | MEDIUM |
| 7 | **Arp randomization params** — 10 probability params + chordPolyphony | ✅ Fixed | Fields added to `ArpModel` record, parsed as attributes on `<arpeggiator>` element | MEDIUM |
| 8 | **`sidechainCompressorVolume`** — written alongside `sidechainCompressorShape` | ✅ Fixed | Parsed at song level (`ProjectModel`), audio clip level (`AudioTrackModel.AudioClip`), kit level (`ClipModel.kitParams`) | MEDIUM |

### 8.2 Architectural Gaps (Large Features)

Features still not implemented (descending priority):

1. **Per-parameter sequences (ParamManager)** — Deep infrastructure change but unlocks full automation.
2. **Track/Clip separation** — Multiple clips per track enables session-mode workflows.
3. **Per-drum FX chain** — Required for full Kit track parity.
4. **Session vs Arranger** — Distinct playback modes.
5. ✅ **AudioClip engine integration** — done (loads WAV via WavReader, LiSa.loadSamples() added, loop region from startSamplePos/endSamplePos)
6. **No GlobalEffectable hierarchy** — Firmware has `GlobalEffectableForSong` and `GlobalEffectableForClip`. Java has bare reverb/delay floats on `ProjectModel`.
7. **Compressor menu** — Attack, blend, ratio, release, threshold are individually wired via globals; no unified compressor menu UI.
8. ✅ **Arpeggiator completion** — All modes, randomization, note probability, chord polyphony, rhythm silences done.
9. **MPE (MIDI Polyphonic Expression)** — No per-note pitch-bend, per-note release velocity, or 14-bit MIDI resolution. `mpeVelocity` field parsed from XML into `ArpModel` but engine never acts on it. MIDI bridge (`MidiInputRouter`) treats all controller data as standard 7-bit. Blocking: MPE-capable controllers (Roli, Osmose) will feel flat.
10. ~~**KitShred unison** — Bridge globals and UI exist for kit unison; KitShred engine never spawns sub-voices (only SynthShred has unison).~~ ✅ Done.

### 8.3 Audio Engine Gaps (Active Items)

1. ~~**Always-on summing tanh saturation** — Done: `SummingTanhUGen` with 1.2× pre-gain between comp and masterSat in both synth/audio buses.~~ ✅
2. ~~**Enhance RingsReverb** — YIN pitch tracking, mallet excitation, K-S mode toggle all already implemented (RingsReverb.java + bridge globals G_REVERB_EXCITATION/G_REVERB_MODE + engine wiring).~~ ✅
3. ~~**Compressor master blend** — Done: `G_MASTER_COMP_BLEND` constant, `ProjectModel.compressorBlend` field, engine reads `comp.dryWet()`.~~ ✅
4. ~~**Compressor threshold wiring** — Done: MasterShred now reads `G_SP_COMPRESSOR_THRESHOLD` as an override (non-zero values replace the knob-derived `1 - 0.8*knob` formula, 0.0 preserves backward compatibility).~~ ✅
5. ✅ **SynthShred + KitShred unison engine** — SynthShred: sub-voice MorphingWavetable spawn, detune, phase-based stereo spread. KitShred: sub-SndBuf spawn, rate detune, per-sub Pan2 stereo spread. Both use power-normalized gain (<code>1/√N</code>).


## 9. javax.sound Dependency Audit & Replacement

**Decision:** Replace javax.sound only where a chuck-core API provides equivalent functionality. Keep javax.sound where no chuck equivalent exists (real MIDI device I/O, audio system playback, core audio engine).

### 9.1 Replaced (Now Using chuck-core APIs)

| Component | What Changed | chuck-core API | Files Changed |
|-----------|-------------|----------------|---------------|
| WAV file reading | Replaced `AudioSystem.getAudioInputStream()` + `AudioFormat` parsing | New `WavReader` utility — pure-Java RIFF/PCM parser (8/16/24-bit, mono/stereo) | `chuck-core/.../WavReader.java` (NEW), `SndBuf.java`, `SndBuf2.java`, `AudioAnalyzer.java` |
| WAV file writing (export) | Replaced `AudioSystem.write()` with manual RIFF header via `ByteBuffer` | 44-byte RIFF header + raw PCM bytes (same pattern as `WvOut2`) | `NativeWavExporter.java` |
| MIDI file export | Replaced `javax.sound.midi.Sequence`/`Track`/`MidiEvent`/`ShortMessage`/`MidiSystem` | `MidiFileOut` + `MidiMsg` (seconds-based timing, 120 BPM, 480 PPQ) | `NativeMidiExporter.java` |
| Test WAV loading | 10 test files had duplicated `loadWavAsFloat()`/`readFrames()` methods | Consolidated to `AudioAnalyzer.loadWav()` — single source of truth | 10 test files + `Dx7SingleNoteAnalysis.java` |

### 9.2 Intentionally Kept (No chuck Equivalent)

| Component | javax.sound API Used | Why Kept |
|-----------|---------------------|----------|
| `NativeMidiInputRouter` | `javax.sound.midi.MidiDevice`/`Transmitter`/`Receiver` | Real MIDI device I/O — no chuck-core abstraction exists for enumerating/opening physical MIDI ports |
| `SwingDelugeApp` clip preview | `javax.sound.sampled.Clip` | Quick sample preview in UI file browser — lightweight playback, no chuck engine needed |
| `ChuckAudio` | `javax.sound.sampled.SourceDataLine`/`AudioFormat`/`AudioSystem` | Core audio engine output — `SourceDataLine` is the JVM's bridge to the OS audio driver; no chuck replacement exists |
| `ChuckMidi`/`ChuckMidiOut`/`MidiFileIn` | `javax.sound.midi.*` (chuck-core internals) | These are chuck-core's own fallback implementations — replacing them would be circular |

### 9.3 File Counts

- **Files modified:** 15 (2 chuck-core + 12 deluge + 1 test utility)
- **Files created:** 1 (`WavReader.java`)
- **javax.sound imports removed:** ~40
- **javax.sound imports remaining:** ~15 (all intentional — see §9.2)

