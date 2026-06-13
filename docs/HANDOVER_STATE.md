# Handover — project state, verified gaps, and work plan (2026-06-11)

Single entry point for picking this project up. Read together with:
- `CLAUDE.md` — the ABSOLUTE faithful-port rule (firmware2 = line-for-line C translation).
- `docs/FIRMWARE2_FAITHFUL_PORT.md` — pre-edit protocol + numeric-type mapping.
- `docs/FIRMWARE2_PORT_ROADMAP.md` — full C→Java file mapping; **§5.5 is the verified gap list**.
- `docs/HANDOFF_AUDIO_BUGS.md` — history of the audio-bug investigations (FM, noise, kit, volume).

## Port baseline

fw2 mirrors `~/a/DelugeFirmware` at the community **`nightly` tag = commit `0d9cbf04`
(2026-05-13)**, post-1.2.0 mainline. Pulls up to `9a74e162` (2026-06-12) changed no DSP (only
MIDI-follow file naming in `param.cpp` — out of port scope), so all C citations remain valid at
current HEAD. **After every upstream pull**: `git diff --stat <old>..<new> -- src/deluge` — if
`dsp/`, `processing/sound/`, `model/voice/`, `modulation/`, or `util/functions.*` moved, re-check
the affected fw2 files against their citations.

## Current state (all suites green)

- **Default suite**: `mvn -pl deluge test` — 302 run / 0 failures (count dropped from 360 in the
  2026-06-12 legacy sweep: tests of deleted legacy classes went with them).
- **Slow suite**: `mvn -pl deluge test -Pslow-tests` — 0 failures. ⚠️ **Always run the
  slow suite after touching `firmware2/Voice.java` or the bridge** — three regressions from the
  unison/flat-buffer rewrite were invisible to the default suite (`c00e4d45`).
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

## Swing UI audit summary (2026-06-11)

- All menus/dialog controls have listeners; none empty.
- **Live-apply architecture**: dialogs write the track MODEL + the bridge `SynthData` arrays.
  `SynthData` is orphaned (read only by one test — it fed the deleted legacy DSL engine path).
  Model edits take effect on the next `loadProject` rebuild. The `PureFirmwareEngine` sync thread
  live-applies only transport/BPM/master-FX/arp-clock.
- **Dead controls** (no engine feature behind them — they come alive as the gap queue lands):
  oscillator sync (#1), retrigPhase (#2), waveIndex (#6), portamento (#7), `hpfFm`, `synthAlgo`,
  osc linear-interpolation toggles.
- Cleanup opportunity (not urgent): drop the orphaned `SynthData` fields/getters (now carries an
  authoritative ORPHANED-STATE javadoc, `7e5124c3` — do not add state there), and the sidebar's
  vestigial `g_sample_*`/`G_LOAD_TRIGGER` writes (legacy-DSL-only consumers).
- 2026-06-12: Settings → "Monitor Audio Input" toggle — continuous mic monitoring through
  inLeft/inRight/inStereo patches (monitor-only capture mode + LiveInput bus). Wavetable
  verbatim-pass disposition: scalar renderer tracks the C SIMD structure, spot checks clean;
  sample-exact audit deferred unless a wavetable fidelity issue surfaces.
- 2026-06-11 additions: SD CARD EXPLORER header has a 📂 fast directory changer (syncs both
  sidebar instances); unison spread/detune/num now passed in C user units with C clamps.

## Next steps (recommended order, 2026-06-12 — gap queue COMPLETE)

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

3. **Roadmap doc refresh.** `FIRMWARE2_PORT_ROADMAP.md`'s file-mapping tables are stale in the
   happy direction — they list as "not ported" several things that are DONE (timestretch, sample
   engine, wavetable, sidechain…). One pass so the mapping doesn't mislead.

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
