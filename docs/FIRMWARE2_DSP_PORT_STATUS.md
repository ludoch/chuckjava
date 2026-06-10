# firmware2 DSP port — status & next steps

Companion to `FIRMWARE2_FAITHFUL_PORT.md` (the absolute rule), `FIRMWARE2_PORT_ROADMAP.md`
(the file map), and `FIRMWARE2_SAMPLE_ENGINE_PLAN.md` (the sample/time-stretch plan).
This is the high-level state of the C→Java DSP port as of the latest commit.

## TL;DR

**Essentially the entire Deluge firmware DSP is ported into `org.chuck.deluge.firmware2` and verified.**
Every `dsp/*.cpp` is done. The only remaining DSP unit is the **`LivePitchShifter` render + `hopEnd`
body** (~600 lines; its foundation + parameter head are done). Two non-DSP items remain that are *not*
faithful-port tasks: the **FFT** (the third-party NE10 ARM library) and **transport-clock wiring**
(application integration).

Test suite: **325 passing, 0 failures** (61 skipped, pre-existing).

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
  `LivePitchShifter` constructor + helpers + `computeLiveHopParameters` (the hopEnd parameter head).

Verification is by **executable re-derivation / property tests** (in `Firmware2FxParityTest`,
`TimeStretcherTest`, `SampleEngineTest`, `LiveInputBufferTest`, `LivePitchShifterPlayHeadTest`,
`LivePitchShifterTest`, etc.), since for most of these `firmware/` is either absent or non-faithful (see
the `firmware-nonfaithful-reference-spots` memory). 13 real fw2 bugs were found and fixed along the way.

## Remaining

### 1. LivePitchShifter render + hopEnd body  — the last DSP unit
The foundation (`LiveInputBuffer`, `LivePitchShifterPlayHead`) and the head (constructor, helpers,
`computeLiveHopParameters`) are ported + tested. What's left, in `processing/live/live_pitch_shifter.cpp`:

- **`hopEnd` body** (cpp:432-840, ~410 lines after the parameter head):
  - the per-moving-average length / crossfade-source / averages-region setup (cpp:432-473);
  - the **percussiveness beam-search** for the new-head position when pitching up (cpp:487-575), and the
    simpler pitch-down placement (cpp:578-598);
  - the **bidirectional crossfade-point search** over the ring buffer (cpp:600-840) — parallel to
    `TimeStretcher.searchForCrossfadeOffset`, but reading `LiveInputBuffer.getAveragesForCrossfade`
    (already ported) and using `getNoise()` for `randomElement`.
- **`render`** (cpp:71-306, ~235 lines): the goto-driven block loop — hop-shortening from
  `getEstimatedPlaytimeRemaining`, the percussiveness-cut (`percThresholdForCut`), then the two-head
  crossfade mix (cpp:240-291, structurally identical to `VoiceSample.renderTimeStretched`, already
  verified) calling `LivePitchShifterPlayHead.render` for each head.

**Recommended approach (mirrors how the time-stretcher was built):**
1. Port the `hopEnd` body as `TimeStretcher.searchForCrossfadeOffset` was — a focused method returning
   `{newHeadRawBufferReadPos, additionalOscPos, samplesTilHopEnd, nextCrossfadeLength, ...}` — with a
   full re-derivation test (deterministic where `randomElement == 0`, i.e. pitchLog in the all-zero
   `randomFine` region) + constant-signal + bounds properties.
2. Port `render` as the orchestration (reuse the verified crossfade-amplitude block from
   `VoiceSample.renderTimeStretched`), with `LiveInputBuffer`/`audioSampleTimer` injected as seams.
3. Property tests: deterministic at a no-random pitch, audible, no out-of-bounds.

**Seams** (same pattern as the sample-engine sync seam): the C reads `AudioEngine::getOrCreateLiveInputBuffer`,
`audioSampleTimer`, and `getNoise()`. Inject the `LiveInputBuffer` + the sample-timer; call `Functions.getNoise()`
at the hop (faithful — the C does too).

### 2. FFT (NOT a faithful-port task)
`dsp/fft/fft_config_manager.cpp` is a thin wrapper around the **NE10 ARM NEON FFT library**
(`src/NE10/...`). Faithfully "porting" it means porting NE10's int32 FFT — a third-party library, not a
Deluge C function. Needed by the vocoder. Out of scope for the faithful-C-port effort.

### 3. Transport-clock wiring (NOT a port — integration)
`SamplePlaybackGuide`'s sync methods and the time-stretch render already take the tempo-clock reads
(`currentInternalTickCount`, `timePerInternalTick`, `isExternalClockActive`, the live play position) as
**seams**. Supplying real values requires a transport/`playbackHandler` clock in fw2 — application
integration, not DSP.

### 4. Engine / bridge migration to firmware2 (deletion track)
What the Swing UI uses today: synth voice DSP + per-sound FX (SRR/EQ/sidechain/modFX) = **firmware2**
(`FirmwareSound.useFirmware2 = true`); **sample playback** and the **master FX bus** = still firmware/.

- ✅ `engine/dsp/Firmware{Delay,Reverb,Compressor}` (DSL UGen wrappers) now use firmware2.
- ✅ **E2E / release-silence bug FIXED** (commit 173b7ae7, merged 6b62085e, 2026-06-10). Root cause: the
  fw2 Patcher used the stored knob as the parameter-curve neutral, but the C uses `paramNeutralValues[p]`,
  which `functionsInit()` populates ONCE as `getParamNeutralValue(p)` — a static per-param constant
  (functions.cpp:175-181), NOT the knob (the knob is folded into the cable combination via
  `combineCablesLinear`, patcher.cpp:218). With the knob (`normToBipolarParamVolume(0.5)=0`) as neutral,
  `getFinalParameterValueVolume(0, …)=0` → silent voices. Fix: all three Patcher sites use
  `Functions.getParamNeutralValue(p)`. fw2 synth voices are now audible (E2E song2/song3/Dx7A-C produce
  real audio). Two calibration tests re-baselined to the correct behavior. Suite: 326 passing.
- ⏳ **Master FX bus** (`FirmwareAudioEngine` / `PureFirmwareEngine` reverb/delay/compressor) — **now
  UNBLOCKED.** The swap is straightforward and ready (delay parity-identical; reverb/compressor are
  faithful corrections). Do this next, then delete `firmware/dsp/{reverb,delay,compressor}`.
- ⏳ **Sample playback** — wire the fw2 sample engine (`Sample`/`SampleReader`/`VoiceSample`) into
  `FirmwareSound` (replacing `firmware.model.sample.*`), enabling deletion of the firmware/ sample model
  + the dead `FirmwareVoice` fallback.

**Deletion blockers** (why nothing in `firmware/dsp` is deletable yet): `FirmwareAudioEngine`,
`PureFirmwareEngine`, and the parity/fidelity tests still reference `firmware/dsp/{reverb,delay,compressor}`.

See task "Fix bridge bugs (Bucket A)".

## Pointers
- Memories: `firmware2-port-boundary`, `firmware-nonfaithful-reference-spots`, `deluge-firmware2-goal`.
- Verification tests live in `deluge/src/test/java/org/chuck/deluge/firmware2/`.
