# Deluge Firmware Features & Menus — Pure Java Implementation Status

> Last updated: 2026-06-03 (verification audit — added "Verified" status; corrected entries found buggy by the firmware-engine test rebuild + hardware A/B)
> Source: Local `../DelugeFirmware` at commit matching community firmware **c1.3.0** (the SD card in use is c1.2.0)

This document maps every documented hardware feature and menu from the official Deluge Firmware to our Java implementation. Use it to track parity and prioritize future work.

> **IMPORTANT — "✅ Implemented" means WIRED/PRESENT, not VERIFIED CORRECT.** Many entries were marked ✅
> once the code path existed, before any comparison to real hardware. The 2026-06-03 audit found several
> ✅ entries that were actually wrong (see §0.5). Treat ✅ as "present"; see the **Verified** column / §0.5
> for what has actually been checked against firmware source, a unit test, or a hardware recording.

---

## 0. Architecture: Two Engine Paths

The Deluge can run in two modes:

| Engine | Description | Status |
|--------|-------------|--------|
| **`PureFirmwareEngine`** | Native Java engine. Audio runs through `FirmwareAudioEngine` + `PlaybackHandler` (firmware port) + `JavaAudioDriver` (javax.sound.sampled `SourceDataLine` output). All DSP uses firmware-ported Java classes (`SVFilter`, `LpLadderFilter`, `DelayBuffer`, `Freeverb`, `FmCore`, `GranularProcessor`, `RMSFeedbackCompressor`, etc.). Zero ChucK dependency — only imports `ChuckVM` for BridgeContract parameter access. | ✅ **Primary engine** (all table entries below refer to this engine) |
| **`DelugeEngineDSL`** | Legacy ChucK-based engine (ChucK UGens on the VM). **UNSUPPORTED as of 2026-06-03** — do not use in tests; the 24 JUnit classes that exercised it are `@Disabled`. Renders some material wrong (e.g. the DX7 BELL song is pure silence in this path while the pure engine is correct). | ❌ Unsupported (do not extend or test) |

**Key:** All "Notes" entries below describe the `PureFirmwareEngine` path unless explicitly noted.

---

## 0.5 Wired vs. Verified — and corrections from the 2026-06-03 audit

"✅" in the tables below historically meant **the code path exists**. It did **not** mean the output was
checked against the real Deluge. The verification ladder is:

- **wired** — code path present; never compared to anything. (Most ✅ entries are only this.)
- **src** — logic verified line-by-line against `../DelugeFirmware` source.
- **test** — covered by a firmware-pure-engine unit test (`deluge/.../firmware/engine/Firmware*Test`).
- **hw** — A/B-compared to a real-hardware recording.

**Features marked ✅ that the audit found BUGGY (presence ≠ correctness):**

| Feature (was ✅) | Bug found | Status | Now verified |
|---|---|---|---|
| DX7 synth | operators played **~6× too high** (unpack bit-fields, pitch-EG levels read from rate bytes, patch transpose wrongly applied) | **fixed** (commits 38584514, 0e3dad22…) | hw (E.PIANO + TUB BELLS A/B) |
| Ladder HPF | **silently disabled** — `hpfMode="HPLadder"`→`LADDER_12`→`OFF`; no high-pass at all | **fixed** (5c2b3b91) | src + test |
| Sample osc | **24/32-bit WAVs decoded as silence** (only 16/8-bit handled) | **fixed** (f0b70b65) | test |
| LFO (S&H / Random-Walk) | retrigger used signed `(long)phase` → wrong for upper half of every cycle | **fixed** (f0b70b65) | src + test |
| Envelope as modulation | suspected the source needed centring (firmware returns `(lastValue-2^30)<<1`, bipolar) → env→cutoff polarity | **RESOLVED — false alarm (2026-06-04 A/B).** Hardware env→filter (`ENVFILT.XML`, C3) starts dark-but-**audible** and brightens — matching Java's current **uncentered** behaviour, NOT a fade-from-silence. Java is correct; centering would be wrong. | **hw** |
| Native 2-op FM | suspected octave issue | **CONFIRMED BUGGY (2026-06-04 A/B), not yet fixed.** Real preset `049 Basic FM` at C3: **hardware ≈130 Hz fundamental + bright** (energy in 5th–16th harmonics); **Java ≈262 Hz (octave high) + dull** (fundamental-dominated, weak harmonics). So native FM has wrong carrier octave AND too little modulation depth. Needs `FmCore` carrier-pitch + modulator-level (OSC_B_VOLUME→index) fix. (DX7 path is unaffected — separate engine.) | **hw (bug confirmed)** |

Firmware-pure-engine tests now exist for: synth voice (osc/env/tuning/poly/filter), patch cables, LFO
tremolo, native FM, tuning (4 osc × 5 octaves), polyphony, HPF + LPF resonance, ring mod, 24-bit WAV,
LFO S&H wrap, and song playback. See `deluge/src/test/.../firmware/engine/` and §8.4.

---

## 1. Feature Status Overview

| Feature | Firmware Doc | Status | Verified | Notes |
|---------|-------------|--------|----------|-------|
| Arpeggiator | `features/arpeggiator.md` | ✅ | wired | All 9 note modes, 5 octave modes, stepRepeat, rhythm patterns w/ silences, seqLength, noteProbability, chordPolyphony+probability, ratchet, octave/gate/vel spread. Firmware `Arpeggiator.java` port in `PlaybackHandler`. (MPE velocity — see MPE row, ⚠️.) |
| Automation View | `features/automation_view.md` | ✅ | wired | BarAutomationDialog, AutomationParam model (26 synth params), per-step editing, XML save/load, MIDI CC. Uses firmware `AutoParam` + `ParamManager`. |
| Audio Recording | `features/audio_export.md` | ✅ | wired | Per-track recording → firmware `AudioClip` → `AudioFileReader` → WAV. Playback via firmware `Sample` + `SampleCache`. |
| Audio Export | `features/audio_export.md` | ✅ | wired | `NativeWavExporter` pure-Java RIFF/PCM export; offline mastered render via `FirmwareAudioEngine`. |
| Chord Keyboard | `features/chord_keyboard.md` | ✅ | wired | CORK/CORL layouts, scale-aware chords, 6 voicing modes. |
| DX7 Synth | `features/dx_synth.md` | ✅ | **hw** | 6-op FM (`FmCore`/`Dx7Engine`), .syx import (`DX7Cartridge`), 32 algos, MkI/Modern/Auto. **2026-06 audit found 3 pitch bugs (played ~6× high): unpack bit-fields, pitch-EG levels read from rate bytes, patch transpose wrongly applied — all fixed; E.PIANO + TUB BELLS A/B-matched to real hardware.** |
| Hardware Character (Master Sat, Filter Drive, 14-bit DAC, Rings Reverb) | — | ✅ | wired | tanh master-bus saturation, v1.3.1 filter drive, 14-bit DAC + TPDF dither, `Freeverb`/`ReverbBase`. Settings → Preferences. |
| Looping in Grid View | `features/looping_in_grid_view.md` | ✅ | wired | `ClipModel.PlayMode.LOOP`, engine auto-re-queue (firmware `Clip` loop logic), green SONG rendering. |
| MIDI Device Definitions | `features/midi_device_definition_files.md` | ✅ | wired | MidiDeviceDefinition XML model, loader, preferences, feedback service, UI browser. |
| MIDI Follow Mode | `features/midi_follow_mode.md` | ✅ | wired | `MidiFollow.java` (port of `midi_follow.cpp` Phase A): 24 CC→param maps, 4-stage routing, JUMP/PICKUP/SCALE takeover, feedback piping. |
| Note/NoteRow Editor | `features/note_noterow_editor.md` | ✅ | wired | Probability, iterance (0-3), fill (0-100%), Euclidean generation. Firmware `NoteRow` + `Note`. |
| Performance View | `features/performance_view.md` | ✅ | wired | 16×8 FX column grid, latch/momentary, value/param editing, XML save/load. |
| Save/Load Patterns | `features/save_load_patterns.md` | ✅ | wired | PatternModel + PatternSerializer, ClipSnapshot, XML save/load, sidebar UI. |
| Velocity View | `features/velocity_view.md` | ✅ | wired | Velocity ramps, per-step editing (guidebook §1.6). |
| Vuefinder | `features/Vuefinder.md` | ➕ N/A | — | Web SD browser (hardware-specific; our Library tab supersedes). |
| 4 Envelopes | `kNumEnvelopes = 4` | ✅ | **test + hw** | 4 ADSR envelopes (firmware `Envelope.java`); release shape tested (`FirmwareSynthVoiceTest`). **env-as-modulation (env→cutoff) verified correct vs real hardware (2026-06-04 A/B, `ENVFILT.XML`): Java's current uncentered source matches; the firmware's bipolar return value does NOT make the env→cutoff fade from silence. Earlier "needs centering" suspicion was a false alarm.** |
| 4 LFOs | `LFO_COUNT = 4` | ✅ | **test + src** | 4 LFOs, all 7 waveforms. S&H/Random-Walk unsigned-wrap bug fixed + tested (`FirmwareLfoModulationTest`, `LfoSampleHoldWrapTest`). |
| Warbler FX | `ModFXType::WARBLE` | ✅ | wired | `ModFXProcessor.java` random-walk + sin LFO delay-line. |
| Dimension FX | `ModFXType::DIMENSION` | ✅ | wired | `ModFXProcessor.java` 3-tap stereo chorus. |
| Patch Cable Polarity | `PatchCable::polarity` | ✅ | test | Per-cable polarity; patch routing tested (`FirmwarePatchCableTest`). |
| Voice Count (VCNT) | `Sound::maxVoiceCount` | ✅ | test | Max voice limit + stealing; POLY/MONO/LEGATO/AUTO/CHOKE. Voice allocation tested (`FirmwarePolyphonyTest`). |
| Threshold Recording | `ThresholdRecordingMode` | ✅ | wired | 4 modes (OFF/LOW/MEDIUM/HIGH) state machine. |
| Native 2-op FM | `SynthMode::FM` (`FmCore`) | ⚠️ Buggy | **hw (bug confirmed)** | **CONFIRMED WRONG vs hardware (2026-06-04 A/B, `049 Basic FM` @ C3): hardware ≈130 Hz + bright (5th–16th harmonics); Java ≈262 Hz (octave high) + dull. Wrong carrier octave + too little modulator depth. Not yet fixed — needs `FmCore` carrier-pitch + OSC_B_VOLUME→modulation-index fix.** DX7 path unaffected. |
| Ring Mod | `SynthMode::RINGMOD` | ✅ | test | Sum/difference tones, carriers suppressed (`FirmwareRingModTest`). |

### Legend
- ✅ **Implemented** — code path present and (per Verified col) working
- ⚠️ **Partial / suspect** — present but a sub-feature is missing or unverified-and-possibly-wrong
- ❌ **Not implemented** — not present in codebase
- ➕ **N/A** — not applicable (hardware-specific)
- **Verified column:** `wired` (path exists, unchecked) · `src` (matches firmware source) · `test` (firmware unit test) · `hw` (A/B vs real hardware). See §0.5.

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
| **Index** | `index.md` | ✅ | Full master compressor parameter controls (Threshold, Attack, Release, Ratio, Blend) fully implemented in bottom Master FX Swing panel and wired to pure Java RMSFeedbackCompressor DSP via Bridge sync thread. |

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
| LPF Mode | `lpf/mode.md` | ✅ | 3 Moog transistor ladder modes (12dB, 24dB, 24dB Drive with oversampling) and 2 SVF modes (Band, Notch) fully mapped and routing-configurable in GUI Sound Editor. |
| LPF Morph | `lpf/morph.md` | ✅ | `SVFilter.morph()` (0=fully LP, 50=fully HP). Bridge global G_FILTER_MORPH. |
| LPF Drive | `lpf/drive.md` | ✅ | `SVFilter` drive with tanh soft-clip saturation (0.0–2.0); drive slider in UI |
| HPF Freq | `hpf/frequency.md` | ✅ | `HpLadderFilter` or `SVFilter` in highpass mode via firmware `FilterSet.java` |
| HPF Res | `hpf/resonance.md` | ✅ | HPF Q via firmware `FilterSet` |
| HPF Mode/Morph/FM | `hpf/*.md` | ✅ (fixed 2026-06) | SVF HPF (morph/notch/band) + HPLADDER. **BUG fixed: the ladder HPF was silently OFF — `hpfMode="HPLadder"`→`LADDER_12`→`OFF` in `setHpfMode` (default case); now LADDER_12/24/DRIVE→HPLADDER. Verified src+test (`FirmwareFilterModeTest`).** |
| Routing | `routing.md` | ✅ | 3 filter routing modes via firmware `FilterSet`: SERIES_LPF_HPF, SERIES_HPF_LPF, PARALLEL |
| Sound Filters | `sound_filters.md` | ✅ | Per-sound `FilterSet` in Kit tracks |
| **Index** | `index.md` | ✅ | Both LPF/HPF filter sub-menus and routes fully exposed, configured, and synchronized. |

### 2.4 LFO (`menus/lfo/`)

Firmware has **4 LFOs** (`LFO_COUNT = 4`): LFO1 (global), LFO2 (per-voice), LFO3 (global), LFO4 (per-voice). Java uses firmware `LFO.java` port with `LFOType` enum.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Rate | `rate.md` (Hz) | ✅ | 4 LFOs with independent rates via firmware `LFO.java` port |
| Sync | `sync.md` | ✅ | LFO sync level via G_LFO_SYNC_LEVEL; works for LFO 0-3 |
| Type | `type.md` | ✅ (fixed 2026-06) | All 7 waveforms. **BUG fixed: S&H / Random-Walk retrigger used signed `(long)phase` → wrong for the upper half of every cycle; now unsigned-masked. Verified src+test (`LfoSampleHoldWrapTest`).** |
| **Index** | `index.md` | ✅ | 4 LFOs, full UI tab with type/rate/depth/target per LFO |

### 2.5 Oscillator (`menus/oscillator/`)

3 subdirectories (modulator, sample, unison) + 9 top-level pages = 19 menu pages total. Java uses firmware `OscType` enum with `BasicWaves` oscillator, `FmCore` for DX7, and `Sample` for sample playback.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Type | `type.md` | ✅ | Sine/Saw/Square/Triangle/Noise/Sample/DX7 via firmware `OscType` + `BasicWaves`. Multi-sampled band-limited anti-aliased wavetables (for Saw/Square octaves) and multi-sampled triangle AA tables (above 1420Hz) are fully ported and loaded dynamically at runtime via high-performance big-endian resource loader. |
| Volume | `volume.md` | ✅ | Per-oscillator volume |
| Pulse Width | `pulse_width.md` | ✅ | Pulse width slider |
| Sync | `sync.md` | ✅ | Hard sync checkbox |
| Retrigger Phase | `retrigger_phase.md` | ✅ | Distinct 0-360° phase starting offsets for Osc 1 and Osc 2 are parsed from XML, written to independent model tracks/drums levels, and applied as Q31 initial start phases or FREE-running offsets in key note-on voice setups. |
| Feedback | `feedback.md` | ✅ | FM feedback amount |
| Wave Index | `wave_index.md` | ✅ | Wavetable position (0.0-1.0), firmware `WaveTable` + `WaveTableBand` engine |
| File Browser | `file_browser.md` | ✅ | Library tab |
| **Modulator 1/2** | `modulator/` | ✅ | Volume/transpose/destination/feedback exist, and independent Modulator 1 / Modulator 2 initial starting reset phases are fully active and mapped in both DelugeXmlParser.java and FirmwareVoice.java. |
| **Sample** | `sample/` (9 files) | ✅ (fixed 2026-06) | All 9 sample-playback menus wired to `VoiceSample`. **BUG fixed: `AudioFileReader` only decoded 16/8-bit PCM — 24-bit and 32-bit WAVs loaded as silence; now decoded (sign-extended / IEEE-float). Verified test (`AudioFileReader24BitTest`).** |
| **Unison** | `unison/` (4 files) | ✅ | Sub-voice spawning with detune, stereo spread. Bridge globals `G_UNISON_NUM/DETUNE/SPREAD`. |
| **Index** | `index.md` | ⚠️ Partial | Osc params exist in editor; ~7/19 sub-pages missing |

### 2.6 Modulation (`menus/modulation/`)

Firmware `PatchSource` enum has 15 source types: `LFO_GLOBAL_1`, `LFO_GLOBAL_2`, `SIDECHAIN`, `ENVELOPE_0`, `ENVELOPE_1`, `ENVELOPE_2`, `ENVELOPE_3`, `LFO_LOCAL_1`, `LFO_LOCAL_2`, `X`, `Y`, `AFTERTOUCH`, `VELOCITY`, `NOTE`, `RANDOM`. Java uses firmware `PatchSource.java`, `PatchCable.java`, `PatchCableSet.java`, and `Patcher.java` for modulation routing.

| Menu Page | Firmware Params | Status | Details |
|-----------|----------------|--------|---------|
| Patch Cables | — | ✅ | Full `PatchCableSet` (source/dest/amount/polarity) per track, up to 16 cables per track |
| Mod Knobs | — | ✅ | 4×4 grid of 16 knob param selectors in MODULATION tab |
| Source options | — | ✅ | All 18 firmware PatchSource options (Envelopes 0-3, Local LFOs 1-2, Global LFOs 1-2, velocity, key note-tracking, sidechain ducking, unique random, and performance pad X/Y axes) are fully active, computed continuously, and routed in the voice synthesis engine. |
| MPE (MIDI Polyphonic Expression) | — | ⚠️ Partial | **Partial — was inconsistently marked ✅ here vs ❌ in §8.2.** WIRED: aftertouch (Z) and timbre/slide (Y) are evaluated as per-voice patch sources in `FirmwareVoice` (`mpePressure`/`mpeTimbre`). NOT WIRED: per-note pitch-bend, per-note release velocity, 14-bit resolution, and the arp `mpeVelocity` field (engine ignores it). See §8.2 #9. |
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
| Feedback | `kModFXParam::FEEDBACK` | ✅ | Full resonance-compensated delay feedback saturation curves (32-bit cubic) are active in ModFXProcessor.java. |
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
| **Pattern (PATT)** | Octaves, Octave Mode, Chord Sim, Note Mode, Step Repeat, Rhythm, Seq Length | ✅ | Octaves + 4 octave modes, ratchet (0-4 sub-divisions), and step-repeat counters (repeating each note in the list N times before advancing) are fully active and evaluated in Arpeggiator.java. |
| **Randomizer (RAND)** | Lock, Octave Spread, Gate Spread, Velocity Spread, Ratchet, Chord Poly, Note/Bass/Swap/Glide/Reverse Probability | ✅ | All 3 spreads (Velocity, Gate time, and Octave shifts), plus note/bass/ratchet/swap probabilities are fully active and computed step-by-step inside Arpeggiator.java. |
| **MPE** | Velocity (via Aftertouch/Y) | ⚠️ Partial | Aftertouch/Y is wired as a patch source; arp `mpeVelocity` field is parsed but the engine does not yet act on it (see MPE row in §2.6 and §8.2 #9). |

## 4. Sub-Feature Detail: Automation View

The firmware automation view supports 81 automatable parameters with per-step grid editing at any zoom level. Our implementation:

| Capability | Status |
|-----------|--------|
| Automation Overview (81 param grid shortcuts) | ✅ |
| Per-step automation editing | ✅ |
| Long-press linear interpolation | ✅ |
| Automation copy/paste | ✅ |
| Live Mod Encoder recording | ✅ | Live CC automation recording of physical mod encoders (Volume, Pan, Filter Cutoff, Filter Resonance, and Pitch Bend wheels) is fully functional in RtMidiInputRouter.java. |
| Parameter automation for individual kit sounds | ✅ | Full per-sound step parameters automation curves (Volume, Pan, LPF frequency/resonance/morph, HPF frequency/resonance/morph, Delay settings, and Reverb amounts) are fully supported and evaluated in InstrumentClip.java and parsed in DelugeXmlParser.java. |
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
9. **MPE (MIDI Polyphonic Expression)** — ⚠️ **Partial** (this is the authoritative MPE status; §2.6 row reconciled to match). WIRED: per-voice aftertouch (Z) and timbre/slide (Y) are evaluated as patch sources in `FirmwareVoice`. NOT WIRED: per-note pitch-bend, per-note release velocity, 14-bit resolution; `mpeVelocity` parsed into `ArpModel` but the engine ignores it; `MidiInputRouter` treats controller data as 7-bit. Blocking: MPE controllers (Roli, Osmose) will feel flat.
10. ~~**KitShred unison** — Bridge globals and UI exist for kit unison; KitShred engine never spawns sub-voices (only SynthShred has unison).~~ ✅ Done.
11. ✅ **FM feedback/amount UI sliders** — mod1Fb, mod2Amt, mod2Fb, carrier2Fb sliders added to FM section of main panel (previously only bridge arrays + engine wiring existed).

### 8.3 Audio Engine Gaps (Active Items)

1. ~~**Always-on summing tanh saturation** — Done: `SummingTanhUGen` with 1.2× pre-gain between comp and masterSat in both synth/audio buses.~~ ✅
2. ~~**Enhance RingsReverb** — YIN pitch tracking, mallet excitation, K-S mode toggle all already implemented (RingsReverb.java + bridge globals G_REVERB_EXCITATION/G_REVERB_MODE + engine wiring).~~ ✅
3. ~~**Compressor master blend** — Done: `G_MASTER_COMP_BLEND` constant, `ProjectModel.compressorBlend` field, engine reads `comp.dryWet()`.~~ ✅
4. ~~**Compressor threshold wiring** — Done: MasterShred now reads `G_SP_COMPRESSOR_THRESHOLD` as an override (non-zero values replace the knob-derived `1 - 0.8*knob` formula, 0.0 preserves backward compatibility).~~ ✅
5. ✅ **Unison engine** — Sub-voice spawning with detune, stereo spread, power-normalized gain (<code>1/√N</code>). Uses firmware `WaveTable` oscillator for synth voices, `Sample` for kit voices.

### 8.4 Firmware-pure-engine tests (the "Verified: test" basis)

Behavioral tests on the supported `PureFirmwareEngine` (`deluge/src/test/.../firmware/engine/` unless
noted). Built 2026-06-03 while migrating off the legacy `DelugeEngineDSL` (24 DSL test classes are now
`@Disabled`). These are what back the **Verified = test** entries above:

| Test | Covers |
|------|--------|
| `FirmwareSynthVoiceTest` | osc types audible/symmetric, env release decay, SINE tuning, polyphony, LPF brightness |
| `FirmwareTuningTest` | 4 osc × 5 octaves tune to MIDI note (YIN detector) |
| `FirmwarePatchCableTest` | velocity→cutoff; env→cutoff sweep (the finding-#1 / env-centering vehicle) |
| `FirmwareLfoModulationTest` | LFO→volume tremolo |
| `FirmwareNativeFmTest` | native FM richness; flags the ~131 Hz octave question |
| `FirmwarePolyphonyTest` | POLY allocates 1 voice/note, MONO reuses 1 |
| `FirmwareFilterModeTest` | HPF removes fundamental; LPF resonance emphasizes cutoff |
| `FirmwareRingModTest` | sum/difference tones, carriers suppressed |
| `LfoSampleHoldWrapTest`, `AudioFileReader24BitTest` | the LFO-S&H and 24/32-bit-WAV bug fixes |
| `Dx7ParityTest` + hardware A/B (`/home/ludo/REC00006/7.wav`) | DX7 vs real Deluge |
| `DelugeE2ETest#testSongPlayback` | 6-song playback on the pure engine |

**A/B verifications done (2026-06-04):** env→filter — Java uncentered confirmed CORRECT (no centering needed); native FM — confirmed BUGGY (octave-high + under-modulated), fix pending.

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

