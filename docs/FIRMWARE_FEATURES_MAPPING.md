# Deluge Firmware Features & Menus — Pure Java Implementation Status

> Last updated: 2026-05-16 (pure Java firmware architecture audit — all audio DSP now native Java, no ChucK dependency)
> Source: Local `../DelugeFirmware` at commit matching community firmware **c1.3.0**

This document maps every documented hardware feature and menu from the official Deluge Firmware to our Java implementation. Use it to track parity and prioritize future work.

---

## 0. Architecture: Two Engine Paths

The Deluge can run in two modes:

| Engine | Description | Status |
|--------|-------------|--------|
| **`PureFirmwareEngine`** | Native Java engine. Audio runs through `FirmwareAudioEngine` + `PlaybackHandler` (firmware port) + `JavaAudioDriver` (javax.sound.sampled `SourceDataLine` output). All DSP uses firmware-ported Java classes (`SVFilter`, `LpLadderFilter`, `DelayBuffer`, `Freeverb`, `FmCore`, `GranularProcessor`, `RMSFeedbackCompressor`, etc.). Zero ChucK dependency — only imports `ChuckVM` for BridgeContract parameter access. | ✅ **Primary engine** (all table entries below refer to this engine) |
| **`DelugeEngineDSL`** | Legacy ChucK-based engine using ChucK UGens (`LiSa`, `WvOut2`, `Dyno`, `DelayL`, etc.). Runs as a `Shred` on the ChucK VM. Still functional but no longer the target for new development. | ⚠️ Legacy (maintained, not extended) |

**Key:** All "Notes" entries below describe the `PureFirmwareEngine` path unless explicitly noted.

---

## 1. Feature Status Overview

| Feature | Firmware Doc | Status | Notes |
|---------|-------------|--------|-------|
| Arpeggiator | `features/arpeggiator.md` | ✅ | All 9 note modes (UP/DOWN/UPDN/RAND/WLK1-3/PLAY/PATT), 5 octave modes (UP/DOWN/UPDN/ALT/RAND), stepRepeat, rhythm patterns with silences, seqLength, noteProbability, chordPolyphony+probability, ratchet, octave/gate/vel spread. MPE missing. Firmware `Arpeggiator.java` port runs natively in `PlaybackHandler`. |
| Automation View | `features/automation_view.md` | ✅ | BarAutomationDialog, AutomationParam model (26 synth params), per-step editing, XML save/load, MIDI CC. Uses firmware `AutoParam` + `ParamManager` for automation storage and interpolation. |
| Audio Recording | `features/audio_export.md` | ✅ | Per-track recording through firmware `AudioClip` → `AudioFileReader` → WAV file. Playback via firmware `Sample` + `SampleCache` engine. |
| Audio Export | `features/audio_export.md` | ✅ | `NativeWavExporter` — pure Java RIFF header + PCM byte buffer export. Offline mastered render via `FirmwareAudioEngine` squeeze-and-render path. |
| Chord Keyboard | `features/chord_keyboard.md` | ✅ Implemented | CORK/CORL layouts, scale-aware chords, 6 voicing modes |
| DX7 Synth | `features/dx_synth.md` | ✅ | 6-op FM engine (`FmCore` firmware port), .syx import/export (`DX7Cartridge`, `WaveTableReader`), 32 algorithms, operator editor UI, DX7 tab, XML round-trip, Vintage/Modern/Auto engine type toggle. Envelope: dexed/msfa log-domain envelopes (not standard ADSR); per-operator DX7 envelopes control amplitude directly. |
| Hardware Character (Master Sat, Filter Drive, 14-bit DAC, Rings Reverb) | — | ⚠️ Partial | User preferences for hardware-accurate audio character: tanh master bus saturation (firmware `SVFilter` drive param), v1.3.1+ filter drive, 14-bit DAC truncation with TPDF dither, `ReverbBase`/`Freeverb` physical-modeling reverb. Toggled via Settings → Preferences. See §Preferences in guidebook. NOTE: The 2D anti-aliased state-space tanh lookup (`tanH2d` table + `getTanHAntialiased`) is currently missing, falling back to a standard 1D tanh approximation. |
| Looping in Grid View | `features/looping_in_grid_view.md` | ✅ | ClipModel.PlayMode.LOOP with context menu, engine auto-re-queue (firmware `Clip` loop logic in `PlaybackHandler`), green rendering in SONG view |
| MIDI Device Definitions | `features/midi_device_definition_files.md` | ✅ | MidiDeviceDefinition XML model, loader, preferences, feedback service, UI browser |
| MIDI Follow Mode | `features/midi_follow_mode.md` | ✅ | `MidiFollow.java` (firmware port of `midi_follow.cpp` Phase A): 24 built-in CC→param mappings, 4-stage routing (takeover → device def → registry → fallback), `MidiInputRouter` for clip-follow, `MidiFeedbackService` for feedback light piping. See §10 for detailed status. |
| Note/NoteRow Editor | `features/note_noterow_editor.md` | ✅ | Probability, iterance (0-3), fill (0-100%), Euclidean rhythm generation via dialog (EuclideanRhythmDialog). Firmware `NoteRow` + `Note` model classes. |
| Performance View | `features/performance_view.md` | ✅ | 16×8 FX column grid, latch/momentary, value editing, param editing, XML save/load |
| Save/Load Patterns | `features/save_load_patterns.md` | ✅ | PatternModel + PatternSerializer, ClipSnapshot grid state, XML save/load, sidebar UI |
| Velocity View | `features/velocity_view.md` | ✅ | See §1.6 of guidebook; velocity ramps, per-step editing |
| Vuefinder | `features/Vuefinder.md` | ➕ N/A | Web-based SD browser (hardware-specific; our Library tab supersedes) |
| 4 Envelopes | `kNumEnvelopes = 4` in `definitions_cxx.hpp` | ✅ | 4 envelopes per track with independent ADSR (firmware `Envelope.java` port). ENV 0→volume, ENV 1→filter, ENV 2→pitch, ENV 3→pan. Envelope tab UI with 4 sub-panels |
| 4 LFOs | `LFO_COUNT = 4` in `definitions_cxx.hpp` | ✅ | 4 LFOs (firmware `LFO.java` port) with all 7 waveform types (SINE/SAW/SQUARE/TRIANGLE/S&H/RANDOM_WALK/WARBLER). LFO 0/1 per-voice, LFO 2/3 global |
| Warbler FX | `ModFXType::WARBLE` in `definitions_cxx.hpp` | ✅ | Firmware `ModFXProcessor.java` port with random-walk + sin LFO modulated delay-line, resonance-compensated feedback, shared `DelayBuffer` core |
| Dimension FX | `ModFXType::DIMENSION` in `definitions_cxx.hpp` | ✅ | Firmware `ModFXProcessor.java` port with 3-tap stereo chorus at 8/14/20ms base delays, triangle LFO, independent phases |
| Patch Cable Polarity | `PatchCable::polarity` (UNIPOLAR/BIPOLAR) | ✅ | Per-cable polarity field on `PatchCable.java`, UI polarity toggle in modulation tab |
| Voice Count (VCNT) | `Sound::maxVoiceCount` | ✅ | Per-track max voice limit (0-8), per-voice active tracking, voice stealing (oldest voice replaced when at limit). PolyphonyMode: POLY/MONO/LEGATO/AUTO/CHOKE. Uses `VoiceAllocator.java`. |
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

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Attack | `attack.md` | ✅ | Per-song attack via G_MASTER_COMP_ATTACK + firmware `RMSFeedbackCompressor.java` mapping |
| Blend | `blend.md` | ✅ | Per-song dry/wet blend via G_MASTER_COMP_BLEND + `RMSFeedbackCompressor.dryWet()` |
| HPF | `hpf.md` | ✅ | Per-track sidechain HPF via G_COMP_SIDECHAIN_HPF array + `SVFilter.setSidechainHpf()` |
| Ratio | `ratio.md` | ✅ | Per-song ratio via G_MASTER_COMP_RATIO + firmware `RMSFeedbackCompressor.java` |
| Release | `release.md` | ✅ | Per-song release via G_MASTER_COMP_RELEASE + firmware `RMSFeedbackCompressor.java` |
| Threshold | `threshold.md` | ✅ | Per-song threshold via G_SP_COMPRESSOR_THRESHOLD (song param overrides knob formula when non-zero) |
| **Index** | `index.md` | ❌ | Full compressor menu not implemented |

### 2.2 Envelope (`menus/envelope/`)

Firmware has **4 envelopes** per sound (`kNumEnvelopes = 4`). ENV 0 and ENV 2 share the ENV1 shortcut pad via layered shortcuts; ENV 1 and ENV 3 share ENV2. Java has firmware `Envelope.java` port with 4 envelopes per track, independent ADSR, default routing (ENV0→volume, ENV1→filter, ENV2→pitch, ENV3→pan), and a full ENVELOPE UI tab with 4 sub-panels.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Attack | `attack.md` (1-∞ ms) | ✅ | Per-env ADSR via firmware `Envelope.java`; 4 envelopes accessible in ENVELOPE tab |
| Decay | `decay.md` | ✅ | Same |
| Release | `release.md` (50-400 ms) | ✅ | Same |
| Sustain | `sustain.md` (0-1 level) | ✅ | Same |
| **Index** | `index.md` | ✅ | Full 4-envelope UI tab with sub-panels for each envelope; target combo per env |

### 2.3 Filter (`menus/filter/`)

Firmware filter modes: `TRANSISTOR_12DB`, `TRANSISTOR_24DB`, `TRANSISTOR_24DB_DRIVE`, `SVF_BAND`, `SVF_NOTCH`, `HPLADDER`, `OFF`. Filter routing: `HIGH_TO_LOW`, `LOW_TO_HIGH`, `PARALLEL`. Java uses firmware-ported `SVFilter.java` (ZDF), `LpLadderFilter.java`, and `HpLadderFilter.java` with morph/notch/drive support. `FilterSet.java` manages per-voice filter routing.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| LPF Freq | `lpf/frequency.md` | ✅ | lpfFreq in SynthTrackModel, applied to firmware `SVFilter.java` |
| LPF Resonance | `lpf/resonance.md` | ✅ | lpfRes, applied to firmware `SVFilter.java` |
| LPF Mode | `lpf/mode.md` | ⚠️ Partial | Filter mode enum exists; firmware has 3 ladder + 2 SVF modes + morph + drive |
| LPF Morph | `lpf/morph.md` | ✅ | `SVFilter.morph()` (0=fully LP, 50=fully HP). Bridge global G_FILTER_MORPH. |
| LPF Drive | `lpf/drive.md` | ✅ | `SVFilter` drive with tanh soft-clip saturation (0.0–2.0); drive slider in UI |
| HPF Freq | `hpf/frequency.md` | ✅ | `HpLadderFilter` or `SVFilter` in highpass mode via firmware `FilterSet.java` |
| HPF Res | `hpf/resonance.md` | ✅ | HPF Q via firmware `FilterSet` |
| HPF Mode/Morph/FM | `hpf/*.md` | ⚠️ Partial | ZDF SVF (morph, notch, drive) in firmware port. Env-to-HPF FM modulation via kEnvToF. |
| Routing | `routing.md` | ✅ | 3 filter routing modes via firmware `FilterSet`: SERIES_LPF_HPF, SERIES_HPF_LPF, PARALLEL |
| Sound Filters | `sound_filters.md` | ✅ | Per-sound `FilterSet` in Kit tracks |
| **Index** | `index.md` | ⚠️ Partial | Basic LPF/HPF works; 10/14 sub-pages missing |

### 2.4 LFO (`menus/lfo/`)

Firmware has **4 LFOs** (`LFO_COUNT = 4`): LFO1 (global), LFO2 (per-voice), LFO3 (global), LFO4 (per-voice). Java uses firmware `LFO.java` port with `LFOType` enum.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Rate | `rate.md` (Hz) | ✅ | 4 LFOs with independent rates via firmware `LFO.java` port |
| Sync | `sync.md` | ✅ | LFO sync level via G_LFO_SYNC_LEVEL; works for LFO 0-3 |
| Type | `type.md` | ✅ | All 7 LFO waveform types via firmware `LFOType` enum: SINE/SAW/SQUARE/TRIANGLE/S&H/RANDOM_WALK/WARBLER |
| **Index** | `index.md` | ✅ | 4 LFOs, full UI tab with type/rate/depth/target per LFO |

### 2.5 Oscillator (`menus/oscillator/`)

3 subdirectories (modulator, sample, unison) + 9 top-level pages = 19 menu pages total. Java uses firmware `OscType` enum with `BasicWaves` oscillator, `FmCore` for DX7, and `Sample` for sample playback.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Type | `type.md` | ⚠️ Partial | Sine/Saw/Square/Triangle/Noise/Sample/DX7 via firmware `OscType` + `BasicWaves`. NOTE: Standard virtual analog oscillators (Saw, Square, Triangle) currently render using naive mathematical waveforms only; the original band-limited multi-sampled saw/square wavetables and multi-sampled triangle tables (above 1420Hz) are missing, resulting in digital foldback aliasing on high notes. |
| Volume | `volume.md` | ✅ | Per-oscillator volume |
| Pulse Width | `pulse_width.md` | ✅ | Pulse width slider |
| Sync | `sync.md` | ✅ | Hard sync checkbox |
| Retrigger Phase | `retrigger_phase.md` | ✅ | 0-360° |
| Feedback | `feedback.md` | ✅ | FM feedback amount |
| Wave Index | `wave_index.md` | ✅ | Wavetable position (0.0-1.0), firmware `WaveTable` + `WaveTableBand` engine |
| File Browser | `file_browser.md` | ✅ | Library tab |
| **Modulator 1/2** | `modulator/` | ⚠️ Partial | Volume/transpose/destination/feedback exist; retrigger phase missing |
| **Sample** | `sample/` (9 files) | ❌ | No sample osc submenu at all |
| **Unison** | `unison/` (4 files) | ✅ | Sub-voice spawning with detune, stereo spread. Bridge globals `G_UNISON_NUM/DETUNE/SPREAD`. |
| **Index** | `index.md` | ⚠️ Partial | Osc params exist in editor; ~7/19 sub-pages missing |

### 2.6 Modulation (`menus/modulation/`)

Firmware `PatchSource` enum has 15 source types: `LFO_GLOBAL_1`, `LFO_GLOBAL_2`, `SIDECHAIN`, `ENVELOPE_0`, `ENVELOPE_1`, `ENVELOPE_2`, `ENVELOPE_3`, `LFO_LOCAL_1`, `LFO_LOCAL_2`, `X`, `Y`, `AFTERTOUCH`, `VELOCITY`, `NOTE`, `RANDOM`. Java uses firmware `PatchSource.java`, `PatchCable.java`, `PatchCableSet.java`, and `Patcher.java` for modulation routing.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Patch Cables | — | ✅ | Full `PatchCableSet` (source/dest/amount/polarity) per track, up to 16 cables per track |
| Mod Knobs | — | ✅ | 4×4 grid of 16 knob param selectors in MODULATION tab |
| Source options | — | ⚠️ Partial | velocity, envelope 1-2, lfo 1-2, aftertouch, note, random, sidechain — missing envelope 3-4, lfo global 1-2, lfo local 2, X, Y |
| MPE (MIDI Polyphonic Expression) | — | ❌ | No per-note pitch-bend, per-note release velocity, or 14-bit MIDI resolution. MIDI bridge treats all data as standard 7-bit. See §8.2 item 9. |
| Destination options | — | ✅ | All firmware destinations via `Destination.java`: volume, pan, lpfFrequency, lpfResonance, oscAVolume, oscBVolume, pitch, noiseVolume, modFxRate, modFxDepth |

### 2.7 Kit Assembly

| Feature | Status | Details |
|---------|--------|---------|
| Assemble Kit From Synths | ✅ | File → Assemble Kit From Synths... selects N synth XMLs, per-lane mute group/pitch offset, outputs .KIT XML |

### 2.8 Voice (`menus/voice/`)

Firmware `PolyphonyMode` enum: `AUTO`, `POLY`, `MONO`, `LEGATO`, `CHOKE`. `Sound::maxVoiceCount` (0-8) per-instrument voice limit. `Unison`: count (1-8), detune, stereo spread. Java uses firmware `PolyphonyMode.java`, firmware `Envelope.java` port, firmware `VoiceAllocator.java`.

| Feature | Firmware | Status | Details |
|---------|----------|--------|---------|
| Polyphony Mode | `PolyphonyMode` (AUTO/POLY/MONO/LEGATO/CHOKE) | ✅ | All 5 modes via firmware `PolyphonyMode.java` with per-track voice stealing |
| Voice Count (VCNT) | `Sound::maxVoiceCount` (0-8) | ✅ | Per-track max voice limit via `G_MAX_VOICES`; voice stealing replaces oldest voice at limit |
| Unison Count | `Sound::numUnison` (1-8, `kMaxNumVoicesUnison`) | ✅ | Up to 8 sub-voices per slot; power-normalized gain; `G_UNISON_NUM` |
| Unison Detune | `kMaxUnisonDetune = 50` | ✅ | Sub-voice frequency = baseFreq × 2^(detuneCents × offset / 1200) |
| Unison Stereo Spread | `kMaxUnisonStereoSpread = 50` | ✅ | Sub-voice phase offset for stereo width; distributed across ±spread range |

### 2.9 Mod FX (`menus/mod_fx/`)

Firmware `ModFXType` enum: `NONE`, `FLANGER`, `CHORUS`, `PHASER`, `CHORUS_STEREO`, `WARBLE`, `DIMENSION`, `GRAIN`. All share `ModFXProcessor` with configurable depth/feedback/offset. Java uses firmware `ModFXProcessor.java` + `ModFXType.java` port (all 8 types, shared delay-line core via `DelayBuffer`).

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Type | `ModFXType` enum (8 types) | ✅ | All 8 types via firmware `ModFXType.java`: CHORUS, FLANGER, PHASER, CHORUS_STEREO, WARBLE, DIMENSION, GRAIN |
| Depth | `kModFXParam::DEPTH` | ✅ | modFxDepth parameter |
| Feedback | `kModFXParam::FEEDBACK` | ⚠️ Partial | Basic feedback; firmware has resonance-compensated feedback curves (32-bit cubic) |
| Offset | `kModFXParam::OFFSET` | ✅ | Delay offset control (`G_MOD_FX_OFFSET`); offset slider in UI |

### 2.10 Bridge Global Status (Per-Kit Extended Parameters)

These `G_KIT_*` globals are registered in `BridgeContract.java` with UI controls and are read every tick by the engine. They are forwarded as per-track `_r` suffixed globals for downstream use. Whether the engine applies them depends on the firmware DSP capabilities:

| Bridge Global | Engine Reads? | Applied? | Notes |
|--------------|--------------|----------|-------|
| `G_KIT_HPF_MODE` | ✅ Read every tick | ✅ Applied | Kit HPF notchMode via firmware `SVFilter.setNotchMode()` per voice |
| `G_KIT_HPF_MORPH` | ✅ Read every tick | ✅ Applied | Kit HPF continuous morph via firmware `SVFilter.morph()` |
| `G_KIT_HPF_FM` | ✅ Read every tick | ✅ Applied | HPF FM modulation via firmware envelope-to-filter depth |
| `G_KIT_OSC2_TYPE` | ✅ Read every tick | ❌ Not applied | Kit voices use sample playback, no osc2 type concept |
| `G_KIT_UNISON_NUM` | ✅ Read every tick | ✅ Applied | Sub-voice spawning with detune and stereo spread |
| `G_KIT_UNISON_DETUNE` | ✅ Read every tick | ✅ Applied | Sub-voice rate detune via 2^(cents*offset/1200) |
| `G_KIT_UNISON_SPREAD` | ✅ Read every tick | ✅ Applied | Sub-voice pan positions spread across stereo field |
| `G_KIT_WAVE_INDEX` | ✅ Read every tick | ❌ Not applied | Kit voices use sample playback; value stored as global |
| `G_KIT_DELAY_RATE` | ✅ Read every tick | ❌ Not applied | Kit delay rate is per-voice FX routing |
| `G_KIT_DELAY_FB` | ✅ Read every tick | ❌ Not applied | Same |
| `G_KIT_MAX_VOICES` | ✅ Read every tick | ❌ Not applied | Kit voices use static voice allocation |
| `G_KIT_POLYPHONY` | ✅ Read every tick | ❌ Not applied | Kit polyphony mode stored but all voices always active |

#### Per-Step Automation Globals

All three read and applied during step processing:

| Bridge Global | Bridge Status | UI Status | Engine Status |
|--------------|--------------|-----------|---------------|
| `G_STEP_FILTER_MODE` | Registered (Float) | Step editor toggle | ✅ Read, maps discrete modes (0=LP,1=BP,2=HP,3=NOTCH) to firmware `SVFilter` morph + notchMode |
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

| Level | Firmware (C++) | Java | Parity |
|-------|---------------|------|--------|
| Root | `Song` (firstOutput linked list, sessionClips, arrangementOnlyClips, currentScale) | `Song.java` (firmware port: song state, clip list, currentScale) + `ProjectModel` (serialization model) | ⚠️ Dual model |
| Track | `Output` (activeClip, ClipInstanceVector, name, type, colour) | `TrackModel` (name, type, muted, volume, pan, color, List<ClipModel>, clips with automation) | ⚠️ |
| Clip | `Clip` (loopLength, output*, section, launchStyle, armState) | `Clip.java` (firmware port) + `ClipModel` (serialization model) | ⚠️ Dual model |
| Note Row | `NoteRow`[] in InstrumentClip | `NoteRow.java` (firmware port) + rows in `ClipModel` | ⚠️ |
| Note | `Note` (velocity, probability, lift, iterance, fill) | `Note.java` (firmware port — `firmware/model/note/Note.java`) | ✅ |
| Per-sound FX | Per-drum FX in Kit | `KitSound` (sample params, adsr, lpf, eq, delay, reverb, compressor, sidechain) | ✅ |
| Parameter Seq / Automation | `ParamManager` per Clip, per NoteRow | `ParamManager.java` (firmware port) + `AutoParam.java` for parameter automation | ✅ Firmware port |
| Timing | `insideWorldTickMagnitude`, `ticksPerLoop` | `TimelineCounter.java` (firmware port) + `SequencerClock` | ⚠️ |
| Envelopes | 4 per voice (`kNumEnvelopes = 4`, `std::array<Envelope, kNumEnvelopes>`) | `Envelope.java` (firmware port) — 4 per voice with ADSR + targets | ✅ |
| LFOs | 4 per sound (`LFO_COUNT = 4`, `LFO globalLFO1/3`, `Voice::lfo2/lfo4`) | `LFO.java` (firmware port) — 4 LFOs, all 7 waveform types | ✅ |
| Patch Cable Polarity | `PatchCable::polarity` (UNIPOLAR/BIPOLAR) | `PatchCable.java` (firmware port) — UNIPOLAR/BIPOLAR per cable | ✅ |
| Mod FX Types | `ModFXType` with 8 types (incl. WARBLE, DIMENSION, GRAIN) | `ModFXType.java` + `ModFXProcessor.java` (firmware ports) — all 8 types | ✅ |
| DX7 Synth | 6-op FM, 32 algorithms, .syx | `FmCore.java` (firmware port) + `DX7Cartridge.java` + `WaveTableReader.java` | ✅ |
| Performance View | 16×8 FX column grid | `PerformanceView.java` (firmware port) + Swing rendering | ✅ |
| MIDI Follow | 3 channels, auto-follow, feedback | `MidiFollow.java` (firmware port), `MidiInputRouter`, `MidiFeedbackService` | ✅ |
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
| **Audio Recording/Playback** | Engine + UI | Firmware `AudioClip` + `Sample` + `AudioFileReader` for WAV playback; per-track audio recording to WAV files |
| **Master WAV Export** | File menu | `NativeWavExporter` — pure Java RIFF/PCM header writer, spliced into `FirmwareAudioEngine` output chain; offline mastered render |
| **Runtime Script Loading** | File menu | Load .ck scripts at runtime via vm.eval(); script access to all UGens + engine globals (legacy ChucK path only) |
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

1. **Per-parameter sequences (ParamManager)** — Deep infrastructure change but unlocks full automation. ✅ Note: All 26 SYTH_PARAMS are now exposed in the automation UI tab (SwingSynthConfigDialog + SwingGridPanel both use SYTH_PARAMS instead of legacy 14). The engine already reads all G_STEP_* arrays. The UI gap is closed — what remains is the firmware-style ParamManager as a separate object per param (vs. our flat Map).
2. **Track/Clip separation** — Multiple clips per track enables session-mode workflows.
3. **Per-drum FX chain** — Required for full Kit track parity.
4. **Session vs Arranger** — Distinct playback modes.
5. ✅ **AudioClip engine integration** — done (firmware `AudioClip.java` + `Sample.java` + `AudioFileReader.java`); loop region from startSamplePos/endSamplePos
6. **No GlobalEffectable hierarchy** — Firmware has `GlobalEffectableForSong` and `GlobalEffectableForClip`. Java has bare reverb/delay floats on `ProjectModel`.
7. ✅ **EQ tab** — Bass/treble shelving EQ UI tab added to SwingSynthConfigDialog (previously only model + bridge arrays existed).
7. ✅ **Compressor menu** — Attack, blend, ratio, release, sidechain HPF UI tab added to `SwingSynthConfigDialog`.
8. ✅ **Arpeggiator completion** — All modes, randomization, note probability, chord polyphony, rhythm silences done.
9. **MPE (MIDI Polyphonic Expression)** — No per-note pitch-bend, per-note release velocity, or 14-bit MIDI resolution. `mpeVelocity` field parsed from XML into `ArpModel` but engine never acts on it. MIDI bridge (`MidiInputRouter`) treats all controller data as standard 7-bit. Blocking: MPE-capable controllers (Roli, Osmose) will feel flat.
10. ~~**KitShred unison** — Bridge globals and UI exist for kit unison; KitShred engine never spawns sub-voices (only SynthShred has unison).~~ ✅ Done.
11. ✅ **FM feedback/amount UI sliders** — mod1Fb, mod2Amt, mod2Fb, carrier2Fb sliders added to FM section of main panel (previously only bridge arrays + engine wiring existed).

### 8.3 Audio Engine Gaps (Active Items)

1. ~~**Always-on summing tanh saturation** — Done: `SummingTanhUGen` with 1.2× pre-gain between comp and masterSat in both synth/audio buses.~~ ✅
2. ~~**Enhance RingsReverb** — YIN pitch tracking, mallet excitation, K-S mode toggle all already implemented (RingsReverb.java + bridge globals G_REVERB_EXCITATION/G_REVERB_MODE + engine wiring).~~ ✅
3. ~~**Compressor master blend** — Done: `G_MASTER_COMP_BLEND` constant, `ProjectModel.compressorBlend` field, engine reads `comp.dryWet()`.~~ ✅
4. ~~**Compressor threshold wiring** — Done: MasterShred now reads `G_SP_COMPRESSOR_THRESHOLD` as an override (non-zero values replace the knob-derived `1 - 0.8*knob` formula, 0.0 preserves backward compatibility).~~ ✅
5. ✅ **Unison engine** — Sub-voice spawning with detune, stereo spread, power-normalized gain (<code>1/√N</code>). Uses firmware `WaveTable` oscillator for synth voices, `Sample` for kit voices.


## 9. javax.sound Dependency Audit & Replacement

**Decision:** javax.sound.sampled is kept for audio output only (no pure-Java alternative exists). javax.sound.midi MIDI device I/O has been replaced by rtmidijava FFM bindings in the Deluge MIDI engine layer.

### 9.1 Replaced (Now Using Pure Java / rtmidijava)

| Component | What Changed | Replacement | Files Changed |
|-----------|-------------|-------------|---------------|
| WAV file reading | Replaced `AudioSystem.getAudioInputStream()` | `WavReader` — pure-Java RIFF/PCM parser | `WavReader.java`, `SndBuf.java`, `SndBuf2.java`, `AudioAnalyzer.java` |
| WAV file writing (export) | Replaced `AudioSystem.write()` | `NativeWavExporter` — 44-byte RIFF header + raw PCM bytes | `NativeWavExporter.java` |
| MIDI file export | Replaced `javax.sound.midi.Sequence` etc. | `MidiFileOut` + `MidiMsg` | `NativeMidiExporter.java` |
| MIDI IN (javax fallback) | Stripped dead-code javax.sound.midi fallback | `RtMidiTransport` (rtmidijava FFM bindings) — primary and only driver | `MidiIn.java`, `ChuckMidi.java` (deleted) |
| MIDI OUT (javax fallback) | Stripped dead-code javax.sound.midi fallback | `RtMidiTransport` (rtmidijava FFM bindings) — primary and only driver | `MidiOut.java`, `ChuckMidiOut.java` (deleted) |

### 9.2 Intentionally Kept (No Pure-Java Alternative)

| Component | javax.sound API Used | Why Kept |
|-----------|---------------------|----------|
| `JavaAudioDriver` | `javax.sound.sampled.SourceDataLine`/`AudioFormat`/`AudioSystem` | Core audio engine output — `SourceDataLine` is the JVM's only bridge to the OS audio driver; no pure-Java replacement exists for real-time audio playback |
| `SwingDelugeApp` clip preview | `javax.sound.sampled.Clip` | Quick sample preview in UI file browser — lightweight playback outside the engine |
| `MidiFileIn` (chuck-core) | `javax.sound.midi.Sequence`/`MidiEvent` | MIDI file *reading* (not real-time I/O) — legitimate javax usage for file format parsing |

### 9.3 File Counts

- **Files modified/created:** 17+ across chuck-core + deluge layers
- **javax.sound imports removed:** ~55 (all MIDI IN/OUT javax fallback code stripped)
- **javax.sound imports remaining:** ~8 (all intentional — see §9.2)
- **Files deleted:** `ChuckMidi.java`, `ChuckMidiOut.java`

