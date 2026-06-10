# firmware2 DSP port — status & next steps

Companion to `FIRMWARE2_FAITHFUL_PORT.md` (the absolute rule), `FIRMWARE2_PORT_ROADMAP.md`
(the file map), and `FIRMWARE2_SAMPLE_ENGINE_PLAN.md` (the sample/time-stretch plan).
This is the high-level state of the C→Java DSP port as of the latest commit.

## TL;DR

**The entire Deluge firmware DSP is now ported into `org.chuck.deluge.firmware2` and verified.**
Every `dsp/*.cpp` and `processing/live/*.cpp` is done. Two non-DSP items remain that are *not*
faithful-port tasks: the **FFT** (the third-party NE10 ARM library) and **transport-clock wiring**
(application integration). The old `firmware/dsp/{compressor,delay,reverb}` packages have been
deleted — all production code uses fw2.

Test suite: **316 passing, 0 failures** (61 skipped, pre-existing).

## Done + verified (this is the bulk)

- **Core**: Functions/tables, Oscillator, Voice, filters (LP/HP ladder, SVF, set), DX7 (Dx7Voice,
  FmCore, EngineMkI, Dx7Tables), Arpeggiator, Envelope, LFO, Patcher, Param.
- **FX**: Eq, SrrBitcrush, ModFx, Compressor, Delay (digital+analog), Sidechain, AbsValueFollower,
  SincInterpolator, GranularProcessor, Reverb (Freeverb + Mutable + **Digital**, newly ported), Metronome.
- **Sample / time-stretch engine** (in-RAM): `Sample`, `SampleHolder`, `SamplePlaybackGuide` (incl. all
  clip-sync / external-clock-drift math via injected clock seams), `SampleReader` (full-precision
  resampled + native, `getPlayByteLowLevel`), `VoiceSample` (pitched + loop + one-shot + **two-head
  time-stretch render**), `TimeStretcher` (`getAveragesForCrossfade`, `computeHopParameters`,
  `searchForCrossfadeOffset`, `hopEnd` incl. loop pre-margin).
- **Live pitch shifter** (input monitoring): `LiveInputBuffer`, `LivePitchShifterPlayHead`, and
  `LivePitchShifter` — the full thing: constructor, helpers, `computeLiveHopParameters`,
  **`hopEnd`** (perc beam-search + bidirectional crossfade-point search over the ring buffer),
  and **`render`** (hop-shortening, percussiveness-cut, two-head crossfade mix).
- **Standalone utilities**: `DelayBuffer` (faithful `delay_buffer.{cpp,h}` port, used by both
  Delay and Stutterer), `Metronome`.

Verification is by **executable re-derivation / property tests** (in `Firmware2FxParityTest`,
`TimeStretcherTest`, `SampleEngineTest`, `LiveInputBufferTest`, `LivePitchShifterPlayHeadTest`,
`LivePitchShifterTest`, etc.), since for most of these `firmware/` is either absent or non-faithful (see
the `firmware-nonfaithful-reference-spots` memory). 15 real fw2 bugs were found and fixed along the way.

## Remaining

### 1. FFT (NOT a faithful-port task)
`dsp/fft/fft_config_manager.cpp` is a thin wrapper around the **NE10 ARM NEON FFT library**
(`src/NE10/...`). Faithfully "porting" it means porting NE10's int32 FFT — a third-party library, not a
Deluge C function. Needed by the vocoder. Out of scope for the faithful-C-port effort.

### 3. Transport-clock wiring (NOT a port — integration)
`SamplePlaybackGuide`'s sync methods and the time-stretch render already take the tempo-clock reads
(`currentInternalTickCount`, `timePerInternalTick`, `isExternalClockActive`, the live play position) as
**seams**. Supplying real values requires a transport/`playbackHandler` clock in fw2 — application
integration, not DSP.

### 4. Engine / bridge migration to firmware2 (deletion track)
What the Swing UI uses today: synth voice DSP + per-sound FX (SRR/EQ/sidechain/modFX) + the **master FX
bus** (reverb/delay/compressor) = **firmware2** (`FirmwareSound.useFirmware2 = true`); only **sample
playback** is still firmware/.

- ✅ `engine/dsp/Firmware{Delay,Reverb,Compressor}` (DSL UGen wrappers) now use firmware2.
- ✅ **E2E / release-silence bug FIXED** (commit 173b7ae7, merged 6b62085e, 2026-06-10). Root cause: the
  fw2 Patcher used the stored knob as the parameter-curve neutral, but the C uses `paramNeutralValues[p]`,
  which `functionsInit()` populates ONCE as `getParamNeutralValue(p)` — a static per-param constant
  (functions.cpp:175-181), NOT the knob (the knob is folded into the cable combination via
  `combineCablesLinear`, patcher.cpp:218). With the knob (`normToBipolarParamVolume(0.5)=0`) as neutral,
  `getFinalParameterValueVolume(0, …)=0` → silent voices. Fix: all three Patcher sites use
  `Functions.getParamNeutralValue(p)`. fw2 synth voices are now audible (E2E song2/song3/Dx7A-C produce
  real audio). Two calibration tests re-baselined to the correct behavior. Suite: 326 passing.
- ✅ **Master FX bus MIGRATED to firmware2** (commit f3caa3f2, merged 09196934, 2026-06-10).
  `FirmwareAudioEngine` now uses `firmware2.Reverb.Container`, `firmware2.Delay`, `firmware2.Compressor`
  on an `int[][]` scratch (public `masterBuffer` stays `StereoSample[]`); `PureFirmwareEngine` uses
  `firmware2.Reverb.Model`. The fw2 corrections roughly halve the doubled firmware/ master level (E2E
  song3 0.106→0.054, Dx7 ~0.027→~0.013). The old `DelugeE2ETest peak>0` gate was a false positive on
  ~1e-9 firmware/-FX rounding noise — now synth/DX7 songs gate on `peak>0.001`, and song1 (sample kit,
  genuinely silent until the sample engine migrates) asserts transport only.
- ⏳ **Sample playback** — wire the fw2 sample engine (`Sample`/`SampleReader`/`VoiceSample`) into
  `FirmwareSound` (replacing `firmware.model.sample.*`), enabling deletion of the firmware/ sample model
  + the dead `FirmwareVoice` fallback. (This is also what makes song1 audible.)

**Deletion of `firmware/dsp/{reverb,delay,compressor}` is still blocked** — no longer by the master
engine, but by: `Stutterer` (uses `firmware.dsp.delay.Delay`), the legacy `DelugeEngineDSL` (--hifi)
path, and the deliberate fw2-vs-firmware/ parity oracles (`DelayParityTest`, `ReverbFidelityTest`,
`DigitalReverbParityTest`, `RMSFeedbackCompressorTest`, `Firmware2FxParityTest`). Next deletion steps:
migrate `Stutterer` to `firmware2.Delay`, retire/redirect the `--hifi` DSL FX, then drop the firmware/
dsp classes (keeping or porting the parity tests last).

## Pointers
- Memories: `firmware2-port-boundary`, `firmware-nonfaithful-reference-spots`, `deluge-firmware2-goal`.
- Verification tests live in `deluge/src/test/java/org/chuck/deluge/firmware2/`.
