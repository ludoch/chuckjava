# Deluge faithful-port — continuation handoff (updated 2026-06-05)

Self-contained handoff so any session (incl. Gemini, which has no access to the prior
chat or Claude's memory files) can continue. **The goal is a bit-faithful Java port of the
Deluge firmware DSP — match the real hardware, not "close enough".** The firmware C++ source
is the ground truth.

---

## 0. Ground rules

- **Firmware source = ground truth.** Reference C++ lives at `~/a/DelugeFirmware/src/deluge/`.
  Port the exact integer/fixed-point math + lookup tables; do NOT substitute float approximations
  (`Math.pow/sin/exp/...`) for the firmware's fixed-point tables.
- **Verify every change with a pinning unit test** that asserts hand-traced firmware values
  (see `FirmwareParamCurvesTest`, `FirmwareEnvRateTest` for the pattern). Hardware A/B only
  *confirms* your reading of the firmware; it does not define correctness.
- **Supported engine = the PURE firmware engine** (`org.chuck.deluge.firmware.*`). The legacy
  `DelugeEngineDSL` ("--hifi") is UNSUPPORTED — do not use it in tests (48 test classes are
  `@Disabled`).
- **Formatting:** before committing, run `mvn -pl deluge spotless:apply`. The project enforces
  Google Java Format via Spotless.
- **Exclude noise:** `comparison/*.wav`, `.claude/settings.local.json`, `jdk25/`.

---

## 1. Build / test / run

```
# build + test the deluge module (JDK 25 + preview + vector incubator)
mvn -pl chuck-core,deluge -am test            # full suite (deluge ~245 tests, 48 disabled)
mvn -pl deluge test -Dtest=FirmwareEnvRateTest # one test class

# Format code before commit:
mvn -pl deluge spotless:apply

# NOTE: chuck-core's ChuckMachineApiTest#testMachineEval is a FLAKY timing test under parallel
# load (passes when run alone). A single failure there is not your change.

# Render a patch to WAV (A/B harness):
mvn -pl deluge -am test-compile
DEPS=$(cat deluge/deluge-deps-classpath)
CP="chuck-core/target/classes:deluge/target/classes:deluge/target/test-classes:$DEPS"
java --enable-preview --add-modules=jdk.incubator.vector -cp "$CP" \
    org.chuck.deluge.reproduce.RenderPatchToWav "<patch.XML>" <midiNote> <out.wav> [seconds] [velocity]
```

Hardware patches live at `/home/ludo/ludocard/SYNTHS/*.XML` (196 presets) — **this is the mounted SD
card, machine-local; a fresh session only has it if `ludocard` is mounted.** The three A/B patches:
`049 Basic FM.XML` (FM, C3), `009 Hoover Bass.XML` (filter/velocity/pan, C2),
`128_SYNTH_DUAL_MOD_C5.XML` (LFO+env, C5).

Three hardware recordings from the 2026-06-05 session: `deluge/src/test/resources/fidelity/REC00010.WAV`
thru `REC00012.WAV`. Reference WAVs: `deluge/src/test/resources/fidelity/reference_*.wav` (generated
from committed programmatic test .XML fixtures).

---

## 2. Render pipeline (how a patch becomes audio)

`DelugeXmlParser.parseSynth(File) -> SynthTrackModel` → `ProjectModel.addTrack` →
`FirmwareFactory.createSong(project) -> Song` → `(InstrumentClip) song.clips.get(0)).sound` is a
`FirmwareSound` → `sound.triggerNote(note,vel)` / `sound.releaseNote(note,-1)` +
`sound.renderOutput(StereoSample[128], 128, reverbBufferOrNull)` per block.
Per-voice DSP is in `FirmwareVoice.render`; sound-level FX in `FirmwareSound.renderInternal`;
master mix/reverb in `FirmwareAudioEngine`.

**Gotcha:** the factory installs `AutoParam`s that override `paramNeutralValues`. To force a param
in a test you must also set `sound.paramManager.getAutomatedParam(paramId).currentValue`.

---

## 3. DONE — all on `main` (HEAD 65dc4e12) — do NOT redo

### Faithful DSP fixes (2026-06-04 session)

| commit | fix |
|---|---|
| 879b8dab | **Native 2-op FM**: faithful port of `voice.cpp` `renderSineWaveWithFeedback`/`renderFMWithFeedback`/`renderFMWithFeedbackAdd` on `SineOsc.doFMNew` (2 modulators + feedback + mod1→mod0; carriers FM'd by the modulator buffer). Modulator amount via the Deluge volume parabola from the raw knob. **Also fixed a cable-routing bug**: `destStr.contains("VOLUME")` sent `modulator1/2Volume` cables to master `LOCAL_VOLUME`. |
| 3ca21c0d | **Param-curve foundation** (`ParamCurves` = `getParamNeutralValue`/`getParamRange`; `FirmwareUtils.getFinalParameterValueVolume/Linear/Hybrid/Exp`). **Fixed a real `getExp` bug**: `increaseMagnitudeAndSaturate` used `>= 0` (so magnitude 0 hit `1<<31` overflow → force-saturate) + unsigned `>>>`; firmware uses `> 0` + arithmetic `>>`. |
| 5f34c9ce | **Filter cutoff range**: removed non-firmware caps (`BasicFilterComponent` moveability `min(1073741823)`, the `min(67108864)` freq clamps). Open cutoff 6.3 kHz → 19.9 kHz. |
| 3b47c06a | **LFO rate**: dropped the ad-hoc `200+pow(2,...)*500`; the unsynced LFO phase increment is the exp-curved rate param directly (`getExp(121739, combineExp(knob))`). Preserved raw rate knob through the parser. |
| 0b021092 | **Envelope rates**: per-stage firmware curves — attack `getExp(4096,-combo)`, decay/release `neutral*lookupReleaseRate` (ported `releaseRateTable64` in `LookupTables`). Raw env knobs preserved; programmatic time-in-seconds falls back to `190.2/time`. |
| de64741d | **Pan law**: linear `shouldDoPanning` (centre = full both channels) + fixed mis-centering (`LOCAL_PAN` is now bipolar, centre 0). Was constant-power cos/sin on a mis-encoded param (centred sound rendered hard-right & −3 dB). |
| 70b69ca1 | **Filter makeup gain**: `FirmwareVoice` discarded `filterSet.setConfig(...)`'s return (`filterGain`). Capturing+applying it fixes ~2.4× hot output / clipping. |
| f350e07a | A/B harness `RenderPatchToWav` + `docs/HARDWARE_AB_PLAN.md`. |

### #8 Patcher rewrite + structural alignment (2026-06-05 session, commit 65dc4e12)

| file | what changed |
|---|---|
| `Patcher.java` | **Complete rewrite**: loops all 55 params (not just cabled ones); folds the stored knob through `combineCablesLinear`/`combineCablesExp` (port of the firmware patcher); uses `ParamCurves` static neutrals + range; dispatches the correct curve per param type (volume parabola / linear / hybrid / exp / `finalEnvRateParam` for envelope stages). Cable polarity, range adjustment, pitch/delay cable amount squaring, and wave-index <<1 hack all ported. |
| `FirmwareFactory.java` | `normToBipolarParamVolume(float norm)` — maps 0→1 onto the full firmware bipolar knob range (-2³¹..+2³¹), matching `getParamFromUserValue`'s convention. `normToBipolarParam(float norm)` — same for non-volume params. `cutoffKnobFromHz` — recovers the raw cutoff knob from Hz. LFO rate now feeds the raw knob to `getExp` directly. |
| `FirmwareSound.java` | Constructor now initializes all `paramNeutralValues[i]` from `ParamCurves.getParamNeutralValue(i)` as a baseline, then overrides with per-patch values. |
| `FirmwareVoice.java` | Envelope attack now uses `finalEnvRateParam`; LFO rate now feeds param directly; filter-gain ordering uses filterGain; pan uses `shouldDoPanning`. |
| `GlobalEffectable.java` | Minor postFXVolume/pan wiring updates. |
| `SideChain.java` | Substantial port: faithful `render` with stereo-ducking, attack/release envelopes per the firmware. |
| `DigitalAudioFidelityTest.java` | Updated to match new volume/gain curves. |
| `FirmwareGoldenSignatureTest.java` | **New**: 6 golden-signature regression tests (saw+filter, FM, LFO tremolo, envelope shape, ring-mod+DX7, 049 Basic FM XML), all programmatic. Wide tolerances (30% relative / 0.05 absolute) — legitimate shift from the volume-knob fix; tighten after hardware A/B. |
| `FirmwareSynthVoiceTest.java` / `RingModParityTest.java` | Re-baselined volume levels for the new firmware-style volume curve path. |
| `PhysicalHardwareFidelityTest.java` | **New**: 36 programmatic golden-signature tests covering dry saw, filtered saw, detuned saw, filter mod, PWM, FM, DX7, unison, resonant LPF/HPF, LFO vibrato/tremolo/LPF/AutoPan/variants, noise, triangle, sine, pitch env sweep, FM feedback, filter morph, noise LPF, high LFO rate, saturated delay, arpeggiator, hard sync, dual mod, FM glide. Each has a committed XML fixture in `deluge/src/test/resources/fidelity/`. |

### Other parity fixes (interleaved commits)

| commit | fix |
|---|---|
| be4d96bb | **Stutter playback** parity fix |
| 75b0fe98 | Advance Deluge firmware parity fixes |

### Also added this session

- `FirmwareParamCurvesTest` (5 tests pinning tables/curves to hand-traced firmware values)
- `FirmwareEnvRateTest` (3 tests pinning `lookupReleaseRate` + envelope rate neutrals + knob direction)
- `PostFxVolumeParityTest`, `SidechainRoutingParityTest`, `SrrBitcrushParityTest`, `ReverbSendParityTest`, `EqParityTest`, `ModFxParityTest`, `DelayParityTest`, `GranularParityTest`, `ArpParityTest`, `TimeStretchParityTest`, `SincInterpolatorTest` — per-FX parity coverage
- `FirmwareFilterModeTest` (HPF + LPF resonance), `FirmwareRingModTest`, `FirmwareTuningTest`, `FirmwarePolyphonyTest`, `FirmwareLfoModulationTest`, `FirmwareNativeFmTest`, `FirmwarePatchCableTest`, `FirmwareFactorySyncTest`, `FirmwareSoundTest`, `FirmwareFactoryTest`, `RingModParityTest`
- `StuttererTest`, `ModFXProcessorTest`, `BasicWavesTest`, `SineOscTest`, `DigitalReverbParityTest`
- `AudioIntegrityTest`, `DelugeE2ETest` (migrated to pure engine), `AudioFileReader24BitTest`, `LfoSampleHoldWrapTest`
- `FirmwareGoldenSignatureTest` (6) + `PhysicalHardwareFidelityTest` (36) = 42 golden-signature tests total

## 4. VERIFIED FAITHFUL — do NOT investigate (already checked vs firmware)

- **Pitch** (`pow(2,n/12)` is exact ET; firmware `noteFrequencyTable` differs by ~0.007 cent).
- **Oscillators** (`renderCrude*` for `tableNumber < 6` matches the firmware at normal CPU load
  `tableNumber < cpuDireness+6`).
- **`fastPythag`** — dead code, unused.
- **Reverb** — wired & functional (factory sets `reverbSendAmount`; `FirmwareAudioEngine.masterReverb.process` mixes back).
- **Velocity** — works for cabled patches (009 Hoover Bass vel30→120 RMS ratio 2.29).
- **Master/patch volume** — `<volume>` is applied (as `LOCAL_VOLUME`); patches differ in level.

## 5. PENDING WORK (prioritised)

### P1 — Tighten golden-signature tolerances (needs hardware A/B)

The golden-signature suite (42 tests total: 6 in `FirmwareGoldenSignatureTest` + 36 in
`PhysicalHardwareFidelityTest`) currently uses wide tolerances (30% relative / 0.05 absolute)
because the volume-knob alignment fix legitimately shifted all output levels. These tolerances
correctly gate that nothing breaks catastrophically, but they don't catch subtle regressions.

**When hardware access returns:** render each golden-test fixture to WAV via
`RenderPatchToWav`, record the same patch on the real Deluge, compare spectra/level, and
tighten the tolerances to the hardware-confirmed reference. The committed `reference_*.wav`
files in `deluge/src/test/resources/fidelity/` were generated from the test fixtures and can
serve as regression checkpoints against their own commit — use them as coarse gates, not as
"hardware matches."

### P2 — Master compressor — DONE (commit 2f6e23d0, pending merge)

Verified the `RMSFeedbackCompressor` port against the firmware's `rms_feedback.cpp` — the render
flow (RMS→log→threshold→envelope→exp gain→amplitude increment→saturation) matches. Added 4 unit
tests (`RMSFeedbackCompressorTest`). Enabled in `FirmwareAudioEngine` by uncommenting
`masterCompressor.renderVolNeutral(...)`. Re-baselined 2 level-sensitive tests.
Constructor default threshold=0 (matches prior Java behavior; can be set to firmware default in a
follow-up UI-knob pass).

### P3 — postFXVolume application (small, verifiable)

`FirmwareSound.renderInternal` hardcodes `postFXVolume = {2147483647}` (full) and the SRR/modFX
processors modify it but their return is **discarded** (never multiplied into the output buffer).
The firmware applies it: `processReverbSendAndVolume(buf, reverbBuf, postFXVolume, ...)`. Fix:
pass `postFXVolume` through `GlobalEffectable.renderOutput` → `processReverbSendAndVolume`.
Small, but verify the application formula matches the firmware's `<< 5` scaling in
`mod_controllable_audio.cpp:219-258`.

### P4 — Consumer-side volume alignment (diminishing returns without hardware)

The faithful Patcher outputs volume values in the firmware's 2²⁹-unity convention (neutral ≈ 0.5,
headroom to 2.0). Some consumers (osc amplitude, filter gain) expect 2³¹-unity. The FM modulator
amount and filter cutoff avoid this by computing values directly. For a cleaner architecture:
audit every `paramFinalValues[LOCAL_VOLUME/OSC_*_VOLUME/...]` usage and ensure it interprets
the firmware convention correctly. Low priority — no functional bug, just less headroom.

### P5 — Hardware A/B (when access returns)

Follow `docs/HARDWARE_AB_PLAN.md`: record 049 Basic FM (C3), 009 Hoover Bass (C2, two velocities),
128_SYNTH_DUAL_MOD_C5 (C5); render the Java side with `RenderPatchToWav`; compare spectra/level.
**Leave the gold/cutoff knobs at the patch's saved positions** — physical knob moves override stored
values and won't match.

### P6 — DX7 path verification

The DX7 path (`sound.isDx7()`, `Dx7Engine`) is separate from native FM. It was verified functional
earlier (commit 38584514). The `dx7` golden test in `FirmwareGoldenSignatureTest` + the DX7 fixture
in `PhysicalHardwareFidelityTest` cover it. If DX7 ever sounds wrong, check:
- `XML` element format (older patches use attribute-style `<envelope attack="0x...">` vs newer
  child-element `<envelope><attack>0x...</attack>`).
- The `Dx7Engine` pitch-EG and transpose (both fixed in 38584514).

---

## 6. KEY GOTCHAS / domain notes

- **Volume convention:** firmware volume params output 2²⁹ = unity (headroom to 2³¹ = "4.0").
  The faithful Patcher uses this. Java oscillators treat 2³¹ = unity. This mismatch is documented
  as P4 — it only reduces headroom, doesn't break anything.
- **Param curves by index:** `getParamType` in `Patcher.java` classifies params:
  - `p < FIRST_LOCAL_NON_VOLUME(7)` → **VOLUME** → `getFinalParameterValueVolume` (parabola)
  - `p < FIRST_LOCAL_HYBRID(19)` → **LINEAR** → `getFinalParameterValueLinear`
  - `p < FIRST_LOCAL_EXP(24)` → **HYBRID** → `getFinalParameterValueHybrid` (pan, phase width)
  - else → **EXP** → `getFinalParameterValueExp`, or `finalEnvRateParam` for envelope stages
  - Same pattern for global params (`FIRST_GLOBAL_NON_VOLUME/HYBRID/EXP`)
- **Filter:** the per-voice ladder/SVF returns a makeup `filterGain` from `setConfig` that MUST
  be applied (now is). `ONE_Q16 = 134217728` (misnamed; it's 2²⁷).
- **DIAG debug spam:** `FirmwareVoice.noteOff`/`FirmwareSound.releaseNote` print `[DIAG ...]` to
  stdout on every note. Harmless but noisy — gate behind a static flag or remove.
- **DX7** path (`sound.isDx7()`, `Dx7Engine`) is separate from native FM. Verified 2026-06-03.

## 7. File map

- **Per-voice DSP:** `firmware/engine/FirmwareVoice.java` (osc render, FM `renderFm` with
  `renderSineWaveWithFeedback`/`renderFMWithFeedbackAdd` helpers, env/LFO, pan, filter).
- **Sound/FX chain + global LFO:** `firmware/engine/FirmwareSound.java`.
- **Master mix/reverb/delay:** `firmware/engine/FirmwareAudioEngine.java` (masterReverb,
  masterDelay, masterCompressor — compressor commented out), `firmware/engine/GlobalEffectable.java`
  (global filterSet, processReverbSendAndVolume).
- **Param curves:** `firmware/modulation/params/ParamCurves.java` (static neutral + range tables),
  `firmware/modulation/params/Param.java` (all 55 param IDs).
- **Patcher (FAITHFUL, rewritten):** `firmware/modulation/patch/Patcher.java` (combineCablesLinear/Exp,
  cableToLinear/ExpParam, range adjustment, pitch/delay square, wave-index hack).
- **PatchCable:** `firmware/modulation/patch/PatchCable.java` (polarity, range adjustment).
- **Math:** `firmware/util/FirmwareUtils.java` (getExp, lookupReleaseRate, patchCombine*Step,
  getFinalParameterValue*, instantTan, getTanH*, signed_saturate, lshiftAndSaturate),
  `firmware/util/Q31.java` (multiply, addSaturate, etc.), `firmware/util/LookupTables.java`
  (tanTable, decayTableSmall8, expTableSmall, releaseRateTable64, SawLookupTables,
  SquareLookupTables, resonanceThresholdsForOversampling, resonanceLimitTable).
- **Filters:** `firmware/dsp/filter/{FirmwareFilter,LpLadderFilter,HpLadderFilter,SVFilter,
  BasicFilterComponent,FilterSet}.java`.
- **Oscillators:** `firmware/dsp/oscillators/{Oscillator,SineOsc,BasicWaves}.java`,
  `firmware/dsp/oscillators/{SawLookupTables,SquareLookupTables,PulseLookupTables}.java`,
  `firmware/storage/wave_table/WaveTable.java`.
- **Envelope:** `firmware/modulation/Envelope.java` (render with getDecay4/8, releaseTable).
  **LFO:** `firmware/modulation/LFO.java` (S&H/Random-Walk fixed).
- **Sidechain:** `firmware/modulation/sidechain/SideChain.java` (faithful port).
- **DSP FX:** `firmware/dsp/fx/{ModFXProcessor,SrrBitcrushProcessor,EqProcessor,ModFXType}.java`,
  `firmware/dsp/reverb/{MutableReverb,DigitalReverb,ReverbContainer}.java`,
  `firmware/dsp/compressor/RMSFeedbackCompressor.java`,
  `firmware/dsp/granular/GranularProcessor.java`,
  `firmware/dsp/envelope_follower/AbsValueFollower.java`,
  `firmware/dsp/fx/SrrBitcrushProcessor.java`.
- **Factory (model→engine):** `firmware/engine/FirmwareFactory.java` (mapModelToSound, cutoffKnobFromHz,
  normToBipolarParam/Volume, stringToPatchSource, cable destination mapping).
- **Parser/Model:** `xml/DelugeXmlParser.java` (parseSynth, parseModulator1/2, parseEnvelopes,
  parseSynthLfo, parsePatchCables), `xml/DelugeHexMapper.java` (hexToQ31, hexToFloat, hexToHz,
  hexToLfoHz, hexToEnvTime), `model/SynthTrackModel.java` (envRateKnobsQ31, lfoRateKnobQ31,
  fmModulatorAmountBaseQ31, fmRatio, modulator1/2Amount, envKnobSet).
- **A/B harness:** `deluge/src/test/.../reproduce/RenderPatchToWav.java`. Plan: `docs/HARDWARE_AB_PLAN.md`.
- **Golden tests:** `FirmwareGoldenSignatureTest.java` (6), `PhysicalHardwareFidelityTest.java` (36).
- **Test fixtures:** `deluge/src/test/resources/fidelity/*.XML` + `reference_*.wav` + `REC00010-12.WAV`.
- **Memory (Claude-only):** `~/.claude/projects/.../memory/deluge-remaining-approximations.md`,
  `deluge-nondx7-port-bugs.md`, `deluge-dx7-gap.md`.

## 8. Methodology checklist for each fix

1. Find the Java code; find the firmware equivalent in `~/a/DelugeFirmware/src/deluge/`.
2. Port the exact integer math + tables. No float shortcuts for what the firmware does in fixed point.
3. Add a unit test pinning hand-traced firmware values (compute by hand from the C++).
4. `mvn -pl deluge spotless:apply` + `mvn -pl deluge test` green.
5. Re-baseline any characterization tests whose values legitimately shift (explain why in the commit).
6. Branch off `main`, commit, and merge to `main`.
