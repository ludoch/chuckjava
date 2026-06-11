# Handover — project state, verified gaps, and work plan (2026-06-11)

Single entry point for picking this project up. Read together with:
- `CLAUDE.md` — the ABSOLUTE faithful-port rule (firmware2 = line-for-line C translation).
- `docs/FIRMWARE2_FAITHFUL_PORT.md` — pre-edit protocol + numeric-type mapping.
- `docs/FIRMWARE2_PORT_ROADMAP.md` — full C→Java file mapping; **§5.5 is the verified gap list**.
- `docs/HANDOFF_AUDIO_BUGS.md` — history of the audio-bug investigations (FM, noise, kit, volume).

## Current state (all suites green)

- **Default suite**: `mvn -pl deluge test` — 337 run / 0 failures.
- **Slow suite**: `mvn -pl deluge test -Pslow-tests` — 414 run / 0 failures. ⚠️ **Always run the
  slow suite after touching `firmware2/Voice.java` or the bridge** — three regressions from the
  unison/flat-buffer rewrite were invisible to the default suite (`c00e4d45`).
- Engine: subtractive/FM/ringmod/sample/DX7 voices with **unison** (per-part detuners, stereo
  spread), full filter set, master FX (delay/reverb/compressor), arpeggiator, MPE, sidechain.
- The Swing UI runs on the **pure engine** (`PureFirmwareEngine`); the legacy `DelugeEngineDSL`
  (--hifi) still exists but is not the default path.

## Verified C-port gaps (work queue, in order)

Audited 2026-06-11 directly against `~/a/DelugeFirmware/src/deluge/`. Full citations in
`FIRMWARE2_PORT_ROADMAP.md` §5.5.

| # | Gap | C reference | Also fixes UI |
|---|-----|-------------|---------------|
| 1 | Oscillator hard sync | voice.cpp:1100-1106, 1171-1240 (per-unison oscSyncPos) | "Oscillator Sync" toggle (currently no-op) |
| 2 | Retrigger phase (osc + modulator) | voice_unison_part_source.cpp noteOn; renderOsc retriggerPhase arg | "Retrig Phase" control (no-op) |
| 3 | Wavefolder | dsp util foldBufferPolyApproximation; voice.cpp:1499/1585 | FOLD param |
| 4 | Voice clipping/saturation | voice.cpp:1535-1565 (sound.clippingAmount → saturate) | Saturation/clipping menu |
| 5 | Analog osc models | oscillator.cpp ANALOG_SAW_2/ANALOG_SQUARE paths (fw2 tables already exist; renderOsc remaps them to digital) | Analog osc types |
| 6 | Wavetable oscillator | oscillator.cpp WAVETABLE + storage/wave_table; voice.cpp:1092-1098 wave-index increments | Wavetable dialog, waveIndex slider (no-op) |
| 7 | Portamento/glide | sound.cpp portamento | Portamento knob (no-op) |
| 8 | Arp rhythm patterns | modulation/arpeggiator_rhythms.h | Arp rhythm selector (simplified) |
| 9 | Live-input sources | voice.cpp:2232-2360 INPUT_L/R/STEREO (LiveInputBuffer/LivePitchShifter already ported, unreachable) | input osc types |
| 10 | Sample niceties | voice.cpp sample cache / pendingSamplesLate / sampleZoneChanged | — |
| 11 | Parity verification | Sidechain, AbsValueFollower, Delay, IR convolution (ported, unverified) | — |

## Swing UI audit summary (2026-06-11)

- All menus/dialog controls have listeners; none empty.
- **Live-apply architecture**: dialogs write the track MODEL + the bridge `SynthData` arrays.
  `SynthData` is orphaned (read only by one test — it fed the deleted legacy DSL engine path).
  Model edits take effect on the next `loadProject` rebuild. The `PureFirmwareEngine` sync thread
  live-applies only transport/BPM/master-FX/arp-clock.
- **Dead controls** (no engine feature behind them — they come alive as the gap queue lands):
  oscillator sync (#1), retrigPhase (#2), waveIndex (#6), portamento (#7), `hpfFm`, `synthAlgo`,
  osc linear-interpolation toggles.
- Cleanup opportunity (not urgent): drop the orphaned `SynthData` fields/getters, and the
  sidebar's vestigial `g_sample_*`/`G_LOAD_TRIGGER` writes (legacy-DSL-only consumers).
- 2026-06-11 additions: SD CARD EXPLORER header has a 📂 fast directory changer (syncs both
  sidebar instances); unison spread/detune/num now passed in C user units with C clamps.

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
