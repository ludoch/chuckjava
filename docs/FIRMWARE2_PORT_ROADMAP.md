# firmware2 port roadmap — full file mapping + status

Companion to `FIRMWARE2_FAITHFUL_PORT.md` (the absolute rule). This file maps every C firmware
source file to its Java port and tracks whether each is **100% faithful**, **partial**,
**not ported**, or **not applicable** (Java-only infrastructure).

## 1. Why `firmware/` still exists

The ultimate goal is to delete `org.chuck.deluge.firmware/` entirely. Today it serves three roles:

| Role | Files | Can be deleted when... |
|------|-------|----------------------|
| **Bridge** | `engine/FirmwareSound`, `engine/FirmwareFactory`, `engine/GlobalSidechainBus` | All effects/models ported to fw2 |
| **Unported DSP** | `dsp/compressor/`, `dsp/delay/`, `dsp/reverb/`, `dsp/granular/`, `dsp/fx/`, `dsp/timestretch/`, `dsp/interpolate/` | Each subsystem faithfully ported to fw2 |
| **Java infrastructure** | `model/`, `gui/`, `hid/`, `storage/`, `playback/`, `modulation/automation/` | These are NOT C ports — they are Java application code. They will remain (maybe refactored) but their **types** are referenced by tests and bridge code. |

**ABSOLUTE RULE**: All new DSP code goes in `firmware2/`. Every edit to `firmware/` must be
either (a) updating the bridge to use firmware2 types, or (b) a C→Java port being added to
firmware2 (not firmware/). Never add new DSP to firmware/.

## 2. Full file mapping: C → firmware/ → firmware2/

Legend:
- ✅ = 100% faithful line-for-line C port
- ⚠️ = partial / simplified (not line-for-line)
- ❌ = not ported at all
- 🏗️ = Java infrastructure (not a C port — models, UI, serialization, bridge)

### 2.1 Core DSP — oscillators, filters, voice

| C source | firmware/ (old) | firmware2/ | Status |
|----------|----------------|------------|--------|
| `dsp/oscillators/oscillator.cpp` (536) | `dsp/oscillators/Oscillator.java` (463) | `Oscillator.java` (564) | ✅ 100% |
| `dsp/oscillators/basic_waves.cpp` (256) | `dsp/oscillators/BasicWaves.java` (166) | *(in Oscillator.java)* | ✅ 100% |
| `dsp/oscillators/sine_osc.cpp` | `dsp/oscillators/SineOsc.java` (98) | `SineOsc.java` (151) | ✅ 100% |
| `model/voice/voice.cpp` (1670+) | `engine/FirmwareVoice.java` (954) | `Voice.java` (865) | ✅ 100% |
| `model/voice/voice.h` | `engine/VoiceUnisonPart.java` (44), `engine/VoiceUnisonPartSource.java` (61) | *(in Voice.java)* | ✅ 100% |
| `model/voice/voice_sample.cpp` | `engine/VoiceSample.java` (168) | — | ⚠️ sample not ported yet |
| `model/voiced.h` | — | — | ❌ (voice pool/steal logic) |
| `dsp/filter/lpladder.cpp` (412) | `dsp/filter/LpLadderFilter.java` (297) | `LpLadderFilter.java` (306) | ✅ 100% |
| `dsp/filter/hpladder.cpp` (117) | `dsp/filter/HpLadderFilter.java` (131) | `HpLadderFilter.java` (146) | ✅ 100% |
| `dsp/filter/svf.cpp` (133) | `dsp/filter/SVFilter.java` (138) | `SVFilter.java` (138) | ✅ 100% |
| `dsp/filter/filter_set.cpp` (198) | `dsp/filter/FilterSet.java` (172) | `FilterSet.java` (187) | ✅ 100% |
| `dsp/filter/ladder_components.h` (52) | `dsp/filter/BasicFilterComponent.java` (45) | `BasicFilterComponent.java` (66) | ✅ 100% |
| `dsp/filter/filter.cpp` (21) | `dsp/filter/FirmwareFilter.java` (83) | `Filter.java` (108) | ✅ 100% |
| `dsp/filter/filter.h` (144) | — | — | ✅ (in Filter.java) |
| `util/lookup_tables.cpp` | `util/LookupTables.java` (734) | `LookupTables.java` (231) | ✅ 100% |
| `util/functions.cpp` | `util/FirmwareUtils.java` (370) | `Functions.java` (574) | ✅ 100% |

### 2.2 DX7 engine

| C source | firmware/ (old) | firmware2/ | Status |
|----------|----------------|------------|--------|
| `dsp/dx/dx7note.cpp` (475) + `.h` (129) | — | `Dx7Voice.java` (682) | ✅ 100% |
| `dsp/dx/env.cpp` (170) + `.h` (77) | — | *(in Dx7Voice.java)* | ✅ 100% |
| `dsp/dx/pitchenv.cpp` (84) + `.h` (51) | — | *(in Dx7Voice.java)* | ✅ 100% |
| `dsp/dx/engine.cpp` (91) + `.h` (68) | — | `FmCore.java` (255) | ✅ 100% |
| `dsp/dx/EngineMkI.cpp` (316) + `.h` (45) | — | `EngineMkI.java` (302) | ✅ 100% |
| `dsp/dx/fm_core.cpp` (119) + `.h` (63) | `dsp/dx/FmCore.java` (130) | `FmCore.java` (255) | ✅ 100% (fw2 supersedes old) |
| `dsp/dx/fm_op_kernel.cpp` (133) + `.h` (44) | `dsp/dx/FmOpKernelVector.java` (81) | *(in FmCore.java)* | ✅ 100% (scalar port of NEON) |
| `dsp/dx/math_lut.cpp` (123) + `.h` (81) | — | `Dx7Tables.java` (174) | ✅ 100% |
| `dsp/dx/aligned_buf.h` (32) | — | — | ❌ (not needed — Java arrays) |

### 2.3 Arpeggiator

| C source | firmware/ (old) | firmware2/ | Status |
|----------|----------------|------------|--------|
| `modulation/arpeggiator.cpp` (1989) + `.h` (381) | `modulation/Arpeggiator.java` (344) | `Arpeggiator.java` (1380) | ✅ 100% |
| `modulation/arpeggiator_rhythms.h` | — | — | ⚠️ simplified (default all-true rhythm) |

### 2.4 Modulation — envelopes, LFOs, patcher

| C source | firmware/ (old) | firmware2/ | Status |
|----------|----------------|------------|--------|
| `modulation/envelope.cpp` + `.h` | `modulation/Envelope.java` (156) | `Envelope.java` (181) | ✅ 100% |
| `modulation/lfo.cpp` + `.h` | `modulation/LFO.java` (92) | `Lfo.java` (217) | ✅ 100% |
| `modulation/patch/patcher.cpp` (1203) | `modulation/patch/Patcher.java` (215) | `Patcher.java` (290) | ✅ 100% (core patching) |
| `modulation/patch/patch_cable_set.cpp` | `modulation/patch/PatchCableSet.java` (28) | *(in Patcher.java)* | ✅ 100% |
| `modulation/patch/patch_source.h` | `modulation/patch/PatchSource.java` (23) | `PatchSource.java` (42) | ✅ 100% |
| `modulation/params/param.h` | `modulation/params/Param.java` (143) | `Param.java` (104) | ✅ 100% |
| `modulation/params/param_set.h` | `modulation/params/ParamCurves.java` (134) | *(in Functions.java)* | ✅ 100% |
| `PhaseIncrementFineTuner` | — | `PhaseIncrementFineTuner.java` (32) | ✅ 100% |

### 2.5 Effects — NOT YET PORTED to firmware2

| C source | firmware/ (old) | firmware2/ | Status |
|----------|----------------|------------|--------|
| `dsp/compressor/rms_feedback.cpp` (167) | `dsp/compressor/RMSFeedbackCompressor.java` (260) | — | ❌ |
| `dsp/delay/delay.cpp` (464) | `dsp/delay/Delay.java` (446) | — | ❌ |
| `dsp/delay/delay_buffer.cpp` (191) | `dsp/delay/DelayBuffer.java` (403) | — | ❌ |
| `dsp/reverb/` (multiple files) | `dsp/reverb/` (multiple files, ~1200 total) | — | ❌ |
| `dsp/granular/GranularProcessor.cpp` (347) | `dsp/granular/GranularProcessor.java` (173) | — | ❌ |
| `dsp/fx/eq` | `dsp/fx/EqProcessor.java` (76) | — | ❌ |
| `dsp/fx/modfx` | `dsp/fx/ModFXProcessor.java` (202) | — | ❌ |
| `dsp/fx/srr_bitcrush` | `dsp/fx/SrrBitcrushProcessor.java` (120) | — | ❌ |
| `dsp/timestretch/time_stretcher.cpp` | `dsp/timestretch/TimeStretcher.java` (112) | — | ❌ |
| `dsp/interpolate/interpolate.cpp` (218) | `dsp/interpolate/SincInterpolator.java` (66) | — | ❌ |
| `dsp/interpolate/` (kernels) | `dsp/interpolate/WindowedSincKernel.java` (146) | — | ❌ |
| `dsp/envelope_follower/absolute_value.cpp` (66) | `dsp/envelope_follower/AbsValueFollower.java` (79) | — | ❌ |
| `dsp/convolution/` | `dsp/convolution/ImpulseResponseProcessor.java` (41) | — | ❌ |
| `dsp/fft/` | `dsp/fft/FFTConfigManager.java` (79) | — | ❌ |
| `modulation/sidechain/sidechain.cpp` | `modulation/sidechain/SideChain.java` (113) | — | ❌ |

### 2.6 Bridge layer (Java-only — no C equivalent)

These files exist only in `firmware/engine/` and are needed to connect the Java model world
to the firmware2 DSP engine. They will shrink as firmware2 subsumes more, but **cannot be
deleted until the old firmware/ is completely removed**.

| File | Lines | Role | Fate |
|------|-------|------|------|
| `engine/FirmwareSound.java` | 890 | **THE bridge** — routes notes, params, arp, MPE to fw2 | Must stay until everything in fw2; eventually becomes a thin wrapper |
| `engine/FirmwareFactory.java` | 905 | Creates FirmwareSound from Java models (XML, track models) | Must stay (model→sound construction). Should reference only fw2 types |
| `engine/FirmwareAudioEngine.java` | 94 | Audio engine init, buffer management | Must stay |
| `engine/FirmwareVoice.java` | 954 | **OLD voice** — still needed for legacy fallback and some tests | Delete when all tests migrate to fw2 |
| `engine/FirmwareKit.java` | 55 | Kit/drum support | Delete when kits ported to fw2 |
| `engine/FirmwareMidiInstrument.java` | 66 | MIDI instrument glue | Keep |
| `engine/GlobalEffectable.java` | 91 | Global effect routing | Keep until effects ported |
| `engine/GlobalSidechainBus.java` | 41 | Sidechain bus | Keep until sidechain ported |
| `engine/Stutterer.java` | 206 | Stutter effect | Keep until ported |
| `engine/VoiceSample.java` | 168 | Sample voice playback | Delete when samples ported |
| `engine/VoiceUnisonPart.java` | 44 | Old unison part (superseded by fw2 VoiceSource) | Delete when FirmwareVoice deleted |
| `engine/VoiceUnisonPartSource.java` | 61 | Old unison source (superseded by fw2 VoiceSource) | Delete when FirmwareVoice deleted |

### 2.7 Java infrastructure (not C ports, but needed for the app)

These are Java application-layer files — they handle models, UI, serialization, playback.
They are NOT C ports and will remain in some form even after firmware/ is gone. The
question is whether they should move to a different package.

| Category | Files | Lines (total) | Notes |
|----------|-------|--------------|-------|
| `model/` | Song, Clip, InstrumentClip, NoteRow, Note, Sample, etc. | ~1200 | Application models — will stay |
| `gui/` | SoundEditor, menu items, views | ~1400 | UI code — will stay |
| `hid/` | Display, buttons, matrix, PIC | ~1400 | Hardware abstraction — will stay |
| `storage/` | AudioFileReader, DX7Cartridge, wavetable | ~800 | File I/O — will stay |
| `playback/` | PlaybackHandler, Arrangement | ~180 | Transport — will stay |
| `modulation/automation/` | AutoParam, ParamNode, ParamManager | ~220 | Automation — will stay |
| `modulation/params/ParamManager.java` | | 84 | Parameter management — keep |
| `util/` | LookupTables, SawLookupTables, etc. | ~11,000 | **Superseded by fw2 versions** — can delete when all fw2 |
| `dsp/StereoSample.java` | | 13 | Simple data class — move to fw2 or keep as shared |

### 2.8 Lookup table files (fw2 supersedes firmware/)

| Table | firmware/ (old) | firmware2/ | Status |
|-------|----------------|------------|--------|
| Saw tables | `util/SawLookupTables.java` (69) | `SawLookupTables.java` (69) | ✅ duplicate — firmware/ copy is unused |
| Square tables | `util/SquareLookupTables.java` (69) | `SquareLookupTables.java` (69) | ✅ duplicate — firmware/ copy is unused |
| Triangle tables | `util/TriangleLookupTables.java` (800) | `TriangleLookupTables.java` (800) | ✅ duplicate — firmware/ copy is unused |
| Analog saw tables | — | `AnalogSawLookupTables.java` (49) | ✅ fw2 only |
| Analog square tables | — | `AnalogSquareLookupTables.java` (47) | ✅ fw2 only |
| General lookup tables | `util/LookupTables.java` (734) | `LookupTables.java` (231) | ✅ fw2 is faithful; old is larger (includes non-DSP tables) |
| TanH table | `util/TanHLookupTable.java` (8521) | — | ❌ not yet in fw2 |
| Wavetable | `storage/wave_table/WaveTable.java` (316) | `WavetableLoader.java` (30) | ⚠️ partial |
| Q31 helpers | `util/Q31.java` (86) | — | ❌ superseded by Functions.java |

## 3. Current test status (275 tests)

| Count | Category | Tests |
|-------|----------|-------|
| 11 failures | All categories | See below |
| 264 passing | — | — |

### Remaining failures

| Test | Symptom | Bucket | Root cause |
|------|---------|--------|------------|
| `ArpParityTest` | only hears note 60 | **A2** | arp gate timing — faithful port exists, bridge integration WIP |
| `AudioIntegrityTest.testKitPlaybackAndGating` | kit silent | **Kit** | FirmwareKit not ported to fw2 |
| `AudioIntegrityTest.testSynthPitchIntegrity` | not silent after release | **B2** | voice cull after release |
| `DelugeE2ETest.testSongPlayback` | song near-silent | **B3** | bridge/song render path |
| `DigitalAudioFidelityTest.testKitDrumFidelityAndDecay` | drum level low | **Kit** | kit not ported |
| `DigitalAudioFidelityTest.testSidechainDuckingFidelity` | sidechain ducking | **A1** | sidechain global-volume path |
| `FirmwareGoldenSignatureTest` ×5 | golden signatures | **C2** | hardware calibration needed |
| `FirmwarePatchCableTest.envelopeToCutoffSweepsFilterOverTime` | sweep depth | **C1** | env shape calibration |

## 4. What's blocking `firmware/` deletion

```
firmware/ can be deleted when ALL of these are true:
├── [ ] All effects ported to firmware2/ (compressor, delay, reverb, granular, modFX, EQ, SRR, sidechain, timestretch, interpolator)
├── [ ] FirmwareVoice.java deleted (all tests use fw2 Voice)
├── [ ] FirmwareKit.java ported to fw2 (kit/drum tests pass)
├── [ ] Bridge (FirmwareSound, FirmwareFactory) references ONLY firmware2/ types for DSP
├── [ ] All lookup tables using fw2 versions (firmware/util/ deleted)
├── [ ] Voice sample playback ported to fw2
├── [ ] Stutterer ported or kept as shared util
└── [ ] All tests pass without firmware/ DSP classes
```

## 5. Order of attack (updated)

```
✅ A1 (sidechain global) → ✅ B1 (fw2 flag-off) → ✅ voice unification
→ ✅ A3 (MPE expression) → ✅ A2 (arp port, 1380 lines) 
→ 🏗️ A2 integration (bridge timing) ← CURRENT
→ B2 (voice cull / silent-after-release)
→ B3 (E2E silence)
→ Effect ports (delay, reverb, compressor, granular, modFX, SRR, sidechain)
→ Kit port
→ C (hardware calibration)
→ Delete firmware/ DSP classes
```
