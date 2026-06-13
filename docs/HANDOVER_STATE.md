# Handover — project state, verified gaps, and work plan (updated 2026-06-13)

Single entry point for picking this project up. Read together with:
- `CLAUDE.md` — the ABSOLUTE faithful-port rule (firmware2 = line-for-line C translation).
- `docs/FIRMWARE2_FAITHFUL_PORT.md` — pre-edit protocol + numeric-type mapping.
- `docs/FIRMWARE2_PORT_ROADMAP.md` — full C→Java file mapping; **§5.5 is the verified gap list**.
- `docs/HANDOFF_AUDIO_BUGS.md` — history of the audio-bug investigations (FM, noise, kit, volume).
- `docs/XML_PARSER_AUDIT.md` — **the song/preset XML reader vs the firmware's deserializer** (the
  current active work area; see "Open work" below).
- `docs/HARDWARE_FIDELITY.md` — how to record reference WAVs on a real Deluge + the test-song format.

## TL;DR for a cold start

The engine (gap queue + legacy-deletion) is **complete and faithful**; the live work is now
**fidelity calibration against real hardware**. A real Deluge owner recorded 11 documented test
songs (in `deluge/src/test/resources/fidelity/hardware-recordings/`); `HardwareFidelityComparisonTest`
renders our side and prints a comparison. Every fidelity bug found so far traced to the **XML →
model bridge** mishandling raw Q31 values, NOT the DSP port. **Open work** is consolidated at the
bottom of this file; the gap-queue/Next-steps sections below are mostly historical (✅ DONE).

## Port baseline

fw2 mirrors `~/a/DelugeFirmware` at the community **`nightly` tag = commit `0d9cbf04`
(2026-05-13)**, post-1.2.0 mainline. Pulls up to `9a74e162` (2026-06-12) changed no DSP (only
MIDI-follow file naming in `param.cpp` — out of port scope), so all C citations remain valid at
current HEAD. **After every upstream pull**: `git diff --stat <old>..<new> -- src/deluge` — if
`dsp/`, `processing/sound/`, `model/voice/`, `modulation/`, or `util/functions.*` moved, re-check
the affected fw2 files against their citations.

## Current state (all suites green)

- **Default suite**: `mvn -pl deluge test` — 327 run / 0 failures (2026-06-13; dropped from 360 in
  the 2026-06-12 legacy sweep, then grew with the hardware-fidelity + parser tests).
- **Slow suite**: `mvn -pl deluge test -Pslow-tests` — 366 run / 0 failures. ⚠️ **Always run the
  slow suite after touching `firmware2/Voice.java` or the bridge** — three regressions from the
  unison/flat-buffer rewrite were invisible to the default suite (`c00e4d45`).
- `HardwareFidelityComparisonTest` runs by default against the committed hardware recordings (point
  elsewhere with `-Dhardware.recordings.dir=…`); it asserts non-silence and prints the comparison
  report. The synth-config dialogs **live-apply** edits to the running engine (200ms timer).
- Engine: subtractive/FM/ringmod/sample/DX7 voices with **unison** (per-part detuners, stereo
  spread), full filter set, master FX (delay/reverb/compressor), arpeggiator, MPE, sidechain.
- The Swing UI runs on the **pure engine** (`PureFirmwareEngine`) — the only engine since the
  legacy `DelugeEngineDSL` (--hifi) was deleted in the 2026-06-12 sweep (see Next steps §2).

## Verified C-port gaps (work queue, in order)

Audited 2026-06-11 directly against `~/a/DelugeFirmware/src/deluge/`. Full citations in
`FIRMWARE2_PORT_ROADMAP.md` §5.5.

| # | Gap | C reference | Status |
|---|-----|-------------|--------|
| 1 | Oscillator hard sync | voice.cpp:1100-1106/1171-1240/2400-2430, render_wave.h:25-90 | ✅ DONE `66211c12` (UI "Oscillator Sync" toggle now real; OscSyncRetrigPhaseTest) |
| 2 | Retrigger phase (osc + modulator) | sound.h:156-157, vups:79-82, voice.cpp:319-327 | ✅ DONE `66211c12` (raw uint32 units, 0xFFFFFFFF=off; trigger-path bridge propagation) |
| 3 | Wavefolder | dsp/util.hpp:66-80; voice.cpp:1499/1585 | ✅ DONE `b0b8fb66` (XML "waveFold" wired; WaveFoldTest) |
| 4 | Voice clipping/saturation | voice.cpp:1535-1565, sound.h:286-294 | ✅ DONE `9487fd12` (clippingAmount wired; SaturationTest) |
| 5 | Analog osc models | oscillator.cpp:70-77/459-466 | ✅ DONE `726de4df` (remap removed; "analogSaw"/"analogSquare" XML names fixed — silently played SINE before; AnalogOscTest) |
| 6 | Wavetable oscillator | oscillator.cpp WAVETABLE + storage/wave_table; voice.cpp:1092-1098 wave-index increments | ✅ DONE `d7489594` (WaveTable render + FFT WavetableGenerator band builder + wave-index increments; WavetableOscTest). Note: the renderer is a fresh implementation in firmware/storage, not a verbatim wave_table.cpp port — verbatim pass is optional follow-up. |
| 7 | Portamento/glide | voice.cpp:190/372-397/840-856, sound.h:141 lastNoteCode | ✅ DONE `2ae01523` (PortamentoTest: C3→C5 glide verified) |
| 8 | Arp rhythm patterns | arpeggiator_rhythms.h + value_scaling.cpp:18/60-62 | ✅ DONE `5aa204c0` (table was already ported but settings.rhythm was never wired; ArpRhythmMappingTest) |
| 9 | Live-input sources | voice.cpp:2232-2360 INPUT_L/R/STEREO | ✅ DONE `227c9970` + `64f00093`: pass-through, ratio increments (2^24-unity, voice.cpp:447-486), per-source LivePitchShifter lifecycle (voice.cpp:2236-2274), desktop mic routing (AudioInputCaptureLine monitor ring → JavaAudioDriver → LiveInput.currentBlock, active while the capture line is armed). Fixed a C UB faithfully transcribed into LivePitchShifter (readPos one-past-end, live_pitch_shifter.cpp:661 — documented deviation). |
| 10 | Sample niceties | voice.cpp sample cache / pendingSamplesLate / sampleZoneChanged | ✅ CLOSED: mid-note unmute DONE `d7489594`; sample CACHE is a hardware CPU/memory optimization with no audible effect (cache playback ≡ direct render) — N/A on desktop by decision; sampleZoneChanged has no trigger path (the UI has no live sample-marker editing; wire it with that feature). |
| 11 | Parity verification | Sidechain, AbsValueFollower, Delay, IR convolution | ✅ DONE `d7489594` (Delay/Sidechain/AbsValueFollower) + `64f00093` (ImpulseResponseParityTest: impulse + direct-form convolution cross-check) |

**Post-merge review 2026-06-12 (`45b4f0cb`):** `65f5d2a2` also fixed the long-standing drum-level
root cause — VoiceSample was missing the C's amplitude shifts (<<3 native, +<<1 pitch-adjusted,
+<<1 time-stretched; voice_sample.cpp:598-612, verified) — and unified the arp clock by aliasing
FirmwareSound's arpeggiator to fw2Sound's. One fidelity test (LFO saw vibrato) broke because the
refactor made noteOn see the real LFO config (more faithful: local LFOs start at their negative
extreme); rewritten as a pinned-phase character test.

⚠️ Test-stability rule learned twice: any fidelity assertion measured on a render with RANDOM
start phases (retrigPhase -1) is realization-dependent — adding/removing getNoise() draws
anywhere in noteOn shifts every later random phase. Pin `Voice.testStartPhaseOverrideOsc1/2 = 0`
(with finally-reset) in such tests; the override also pins per-part modulator phases.

## Architecture (current, 2026-06-13 — supersedes the old "Swing UI audit")

> ⚠️ Earlier handovers described dialogs writing bridge `SynthData` arrays with edits applying on
> the next `loadProject`. **That is gone.** `SynthData` and the legacy DSL engine were deleted; the
> description below is current.

- **One engine:** the Swing UI runs only on `PureFirmwareEngine` (fw2). The render path for a synth
  is `FirmwareAudioEngine.renderBlock` → `FirmwareSound.renderInternal` (sums fw2 `Voice`s, then its
  own FX chain: SRR → stutter → modFX → EQ → **per-sound delay** → sidechain). `fw2Sound` is used by
  `FirmwareSound` for voices/params/arp; note `fw2 Sound.renderInternal` itself is NOT on the active
  path (FirmwareSound reimplements the FX chain).
- **Song load:** `DelugeXmlParser.parseSong` → `ProjectModel` (per-track `SynthTrackModel` etc) →
  `FirmwareFactory.createSong` builds `FirmwareSound`s. Param values flow model → factory →
  `paramNeutralValues`/`paramKnobs` → (per block) `syncParamsToFw2` → `fw2Sound.patchedParamValues`.
- **Model-backed dialogs + live-apply:** synth-config panels read/write the `SynthTrackModel`
  (NOT bridge arrays); a 200ms timer in `SwingSynthConfigDialog`/`SwingKitConfigDialog` calls
  `FirmwareFactory.applyModelToLiveSound`, so edits are audible immediately.
- **`SynthData` removed** (2026-06-12): the 80-array bridge store + ~187 accessors are gone; do not
  reintroduce a parallel param store. The `g_sample_*` VM globals remain only as the pad-label store
  `SwingMatrixPanel` reads.
- **soundParams are raw Q31** (the systemic 2026-06-13 finding): the firmware reads every sound
  param verbatim as a raw Q31 knob; our parser used a lossy hex→float→knob round-trip. Song patched
  params now read raw via `SOUNDPARAMS_RAW_PATCHED` in the parser. See `docs/XML_PARSER_AUDIT.md`.
- Misc UI: Settings → "Monitor Audio Input" (mic monitor via inLeft/inRight/inStereo); SD-card
  explorer 📂 directory changer; unison spread/detune/num in C user units.

## Next steps — historical log (gap queue COMPLETE; for what's OPEN see "Open work" below)

> Items 1–2 are the detailed DONE record (live-apply, legacy sweep). Items 3–5 are superseded by
> the consolidated "Open work" section near the bottom.

1. **Live-apply for synth edits** — ✅ DONE 2026-06-12. While `SwingSynthConfigDialog` is open, a
   200ms Swing timer re-maps the edited model onto the RUNNING engine sound
   (`FirmwareFactory.applyModelToLiveSound`); the per-block `syncParamsToFw2` forwards everything
   to fw2, so edits reach even sustained notes within one block. Key pieces: idempotent
   `mapModelToSound` (clears the cable set), path-guarded `loadOscResources`,
   `synchronized syncParamsToFw2` vs the apply (cable-set race), and a change-guard on the
   `G_SP_*` song-param sync (it used to clobber per-track knobs every 20ms). `LiveApplyTest`
   guards it. The KIT dialog does not live-apply yet (drum mapping is inline in `createKitClip`
   — extract `mapDrumToSound` to extend it).

2. **Legacy-deletion sweep** — ✅ DONE 2026-06-12 (77 files). Deleted: the `DelugeEngineDSL`
   --hifi path (the 3.9k-line DSL engine, the whole `engine/dsp/` package incl. Native*/
   Switchable*/Firmware* UGens, the `hiFiMode`/`G_HI_FI_MODE` flag and its UI toggles — the
   Settings menu item, the MasterFx HI-FI checkbox, and the always-true guards in
   SwingGridPanel/SwingMatrixPanel with their dead `E_PREVIEW` else-branches), 21 superseded
   `firmware/` classes (oscillators, FilterSet/ladders/SVF, dx FmCore, IR convolution,
   AbsValueFollower, FFTConfigManager, sinc interpolators, old Arpeggiator, lookup-table classes,
   StereoFloatSample, WavetableLoader), and 33 legacy test files (29 were already
   `@Disabled("Legacy DelugeEngineDSL engine is unsupported")` or manual `main()` diagnostics).
   Coverage preserved: the 3 old-arp feature tests were ported to the fw2 arp as
   `firmware2/ArpFeaturesTest` (step repeats, spreads/swap, MPE velocity);
   `Firmware2FxParityTest`'s two cross-checks against deleted firmware/ classes were converted to
   C-contract assertions (the divergences they proved are documented in place). Still present by
   design: `firmware/util/Q31` + `modulation/Envelope` + `params/ParamCurves` +
   `dsp/filter/BasicFilterComponent` (live bridge deps), and the `g_sample_*` VM globals (they
   are the pad-label store SwingMatrixPanel reads — removing the writes needs a model-backed
   label refactor first).

   **SynthData physically removed** (follow-up, 2026-06-12): the 80-array class, its ~187 bridge
   accessors, the dead `NativeMidiInputRouter`/`RtMidiInputRouter`/`VoiceAllocator` chain, and the
   orphan `G_ENV`/`G_LFO_*` VM arrays are gone. The synth-dialog panels are now **model-backed**
   (they used to read AND write only the orphan arrays — i.e. several tabs' edits never reached
   the pure engine at all). In the process the ARP tab became real: `configureArp` now maps the
   FULL ArpModel (noteMode, seqLength, spreads, all probabilities, ratchet, chord polyphony, MPE
   velocity, syncType — raw uint32 menu scaling ×85899345) plus a free-rate multiplier on the
   BPM-derived arp clock; the LFO tab's depth/target now synthesize real patch cables
   (LFO_GLOBAL_1/LOCAL_1/GLOBAL_2/LOCAL_2 → param) and rate edits drive the firmware exp-curve
   knob via `FirmwareFactory.lfoRateKnobFromHz` (binary-search inverse). `LiveApplyTest` covers
   the new mappings.

3. **Roadmap doc refresh.** ✅ DONE 2026-06-13. Updated `FIRMWARE2_PORT_ROADMAP.md`'s file-mapping
   tables, test status, order of attack, and verified gap lists to accurately reflect all completed
   subsystems (timestretch, sample engine, wavetable, sidechain, delay, etc.).

4. **True hardware calibration** — IN PROGRESS (2026-06-12/13). 11 documented test songs recorded
   on a real Deluge (c1.2.0) live in `deluge/src/test/resources/fidelity/hardware-recordings/`;
   `HardwareFidelityComparisonTest` runs them by default. Hardware comparison has already found +
   fixed real bugs: parser dropped clip `<soundParams>` (real songs played instrument defaults),
   LFO double-curve, real-format note-pitch mapping (`rowYNote`), ladder-LPF gross distortion
   (min-resonance float round-trip; raw-Q31 overlay), and the missing per-sound delay
   (now ported into the Sound FX chain). **Remaining finer calibration** (hardware records are
   reference-grade; these are engine-side): (a) filter cutoff + resonance-Q — `TestFilterFidelity`
   lacks the hardware's resonant peak and sits at a lower cutoff; (b) delay feedback level — HW
   echoes grow (near-unity feedback) where ours decay at the song's 0.25; (c) FM brightness/depth
   spectral match. Tooling note: measure rate/pitch at the engine (probes), not via Goertzel/
   zero-crossing on vibrato'd or harmonically-rich tones (they lie).

5. **Small items:** file the upstream `LivePitchShifter` OOB bug report (full ready-to-paste
   text in `docs/UPSTREAM_BUG_live_pitch_shifter_oob.md`); **kit-dialog live-apply** — ✅ DONE
   2026-06-12 (the KIT dialog now uses the extracted helper `FirmwareFactory.applyModelToLiveSound`
   on a 200ms Swing Timer, enabling live parameter updates);
   `sampleZoneChanged` whenever live sample-marker editing comes to the UI; input-device
   selection for the Monitor Audio Input toggle.

## Open work (what's actually left, 2026-06-13)

Everything in "Verified C-port gaps" and "Next steps" above is ✅ DONE (historical). The live work:

1. **DSP fidelity calibration** (highest value; needs the hardware recordings, which are committed).
   These survive correct raw param input, so they're genuine DSP/curve gaps, not parser bugs:
   - **Ladder filter cutoff curve + resonance strength** — `TestFilterFidelity` (saw C3) on hardware
     has a strong resonant peak ~H12 and energy to ~H14; ours rolls off by ~H6 with no peak. Compare
     `curveFrequency` (knob→Hz) and the ladder feedback/`processedResonance` scaling vs `lpladder.cpp`
     for the same raw knob.
   - **Delay feedback level** — timing is correct (1.0s); HW echoes grow (near-unity feedback) where
     ours decay at the song's knob. The feedback-amount → repeats/gain mapping.
   - **FM brightness/depth** spectral match.
2. **XML parser follow-ups** (`docs/XML_PARSER_AUDIT.md` has the per-param table):
    - env **sustain** raw Q31 loading: ✅ DONE (preset + song formats load raw sustain directly).
    - apply the **raw-Q31 reader to preset `<defaultParams>`**: ✅ DONE (restored raw `<defaultParams>` overlay, fixed arpeggiator test voice summation saturation via volume override).
    - audit the **unpatched FX scalar conversions** (modFX/delay/bitcrush/srr/eq/sidechain) vs the
      firmware curves (not yet done value-by-value).
3. **Small items:** file the upstream `LivePitchShifter` OOB bug (ready-to-paste in
   `docs/UPSTREAM_BUG_live_pitch_shifter_oob.md`); `sampleZoneChanged` when live sample-marker
   editing lands; input-device selection for the Monitor Audio Input toggle.

## Known deviations from the C (documented, user-approved)

- `getFinalParameterValueVolume/Linear` clamp `positivePatchedValue` to [0, 2^30] (`be0d8193`);
  the C deliberately allows overflow (functions.cpp:215).
- The driver monitor gain in `JavaAudioDriver` (12×) is app-side listening convenience, not port.
- The fidelity tests assert physically-verifiable character (FM brightness, subharmonic
  periodicity, envelope/pitch parity) where waveform correlation is provably unattainable —
  forensic rationale in comments in `PhysicalHardwareFidelityTest`.

## Working conventions (hard-learned)

- Every firmware2 edit cites the C file:line it ports. Open the C function FIRST.
- The fidelity reference WAVs are **24-bit** — use the 24-bit-aware loaders, never a 16-bit read.
- FM/unison waveforms are phase-realization-dependent: pin `Voice.testStartPhaseOverrideOsc1/2`
  (with a finally-reset) in any test that measures FM character.
- `firmware/` is NOT a parity oracle (see roadmap note) — verify against the C.
- Commit + push to main after each green item; small faithful steps.
