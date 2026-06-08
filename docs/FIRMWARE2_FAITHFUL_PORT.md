# firmware2: faithful line-for-line C port of the Deluge DSP

## The rule (absolute)
`org.chuck.deluge.firmware2` must be a **100% line-for-line translation of the Deluge C firmware**
(`~/a/DelugeFirmware/src/deluge/`). **Translate the C, do not reconstruct/paraphrase.** No approximations,
no float substitutes for fixed-point/table math, no invented control flow. If a deviation is ever needed,
stop and get explicit approval first.

**Why it matters (proven repeatedly):** every bug fixed this session was a place where firmware2 had
*paraphrased* the C instead of *transcribing* it — and those bugs do not exist in the real Deluge. Each
faithful transcription made a "bug" vanish. Parts that were genuinely copied (value-scaling curves,
`paramNeutralValues`, `cableTo*` leaf math, wavetables) had no such bugs.

### Numeric-type mapping (the #1 error source)
- C `int32_t` → Java `int` (both 32-bit two's-complement; wraparound matches — rely on it).
- C `uint32_t` → Java `int` with **unsigned ops**: `>>>`, `Integer.compareUnsigned`, `& 0xFFFFFFFFL`.
- C `int64_t`/`uint64_t` → Java `long`; 64-bit products via `(long)a * b`.
- **Never** use `float`/`double` for Q31/Q32 fixed-point. Only where the C literally uses float/double.
- NEON SIMD → scalar per-lane: `vqdmulhq_s32(a,b)` = `(int)(((long)a*b) >> 31)`; `vld1q/vst1q` = unrolled
  per-sample loop; preserve exact shift counts + rounding (`*_rshift32_rounded` adds the round constant).

## What is faithfully transcribed (done)
- **Oscillator** (`oscillator.cpp`/`basic_waves.cpp`): saw, square, triangle, **sine** (renderWave over
  `sineWaveSmall`), **analog saw/square** (40 `.bin` tables generated from the C int16 arrays +
  `AnalogSaw/SquareLookupTables`), and **`getTableNumber`** (the real phaseIncrement→band threshold table,
  magnitudes 13/12/11/10/9). `renderWave` is a scalar port of `waveRenderingFunctionGeneral`.
- **Voice gain staging** (`voice.cpp:984-1052`): `setConfig`→`filterGain` once; subtractive
  `sourceAmplitudes[s] = LOCAL_OSC_VOLUME>>4` (no filter) / `×filterGain`; `overallOscAmplitude` applied
  once **after** the filter. (Removed a double-volume application + a `>>3`-vs-`>>4` bug.)
- **FM** (`voice.cpp:1024-1037`, `533-553`): carrier amplitude fold (`volumeNeutralValueForUnison<<3` +
  `134217727` cap); **modulator increment** from the note table + `modulatorTranspose` (semitones) +
  `PhaseIncrementFineTuner` cents detune (verbatim; raw transpose/cents plumbed model→parser→factory→Sound).
- **Ringmod** (`voice.cpp:1309-1370`): fixed-amplitude oscs, `amplitudeForRingMod` with `filterGain` +
  per-osc-type compensation. (Exposed + fixed the fixed-amplitude SINE `sample<<1` overflow.)
- **Patcher** wiring: firmware2 `Sound` owns `patchedParamValues` + `patchCableSet`; per-block
  `performInitialPatching` (base) + `performPatching` (cables). Source formulas match `voice.cpp` noteOn.
- **Envelope**: render was already faithful; fixed the **release routing** (`releaseNote` now reaches fw2 voices).
- **DX7 math foundation** (`dsp/dx/math_lut.cpp`): `Dx7Tables` exp2 scale fixed (`1<<30`, was `1<<24` → 64×
  too small) + added `SIN_TAB`/`TANH_TAB`/`FREQ_LUT` + `sin/tanh/freq` lookups; `Freqlut` wired. DX7 now
  produces correct, distinct output (`Dx7ParityTest`/`Dx7VoiceTest` pass).
- `PhaseIncrementFineTuner` + `centAdjustTableSmall[257]`, `PatchSource` enum — verbatim.

## Remaining work (each its own faithful pass)
1. **DX7 finish**: operators still use `SineOsc.doFMNew` instead of `Dx7Tables.sinLookup` (`Sin::lookup`);
   engine switching (modern/MkI) is stubbed. Port the rest of `dx7note.cpp`/`engine.cpp` Sin usage.
2. **Filters**: firmware2 `FilterSet` SVF / HP-ladder — verify against `state_variable_filter` etc.; an HPF
   branch in `Voice.applyFilterAndGain`; `fw2HpfMode`. (Currently LPF ladder only.)
3. **Full patcher fidelity**: the C `performPatching(sourcesChanged, Sound&, ParamManager&)` uses a
   `sourcesChanged` bitmask + ordered destinations + `sourcesPatchedToAnything`; firmware2 uses a simplified
   per-block pass (correct result for static patches, not the C's exact structure/automation/smoothing).
4. **Golden / threshold re-baseline**: many tests were calibrated to the *non-faithful legacy* engine
   (2^31 unity, ~2× louder). The faithful engine is correctly quieter (2^29 unity + headroom), so audibility
   thresholds / golden signatures (`FirmwareGoldenSignatureTest`: `fm peak=1.0`, `dx7 brightness=0.561`, lfo
   tremolo, envelope decay) need a **hardware-verified** re-capture — NOT blind lowering (don't mask real
   silence). Done so far: `FirmwareSynthVoiceTest`, `FirmwareNativeFmTest`, `FirmwareRingModTest` audibility bars.
5. Remaining real bugs: arp (no notes), MPE, sidechain (65% drop), granular post-fx, `env2→cutoff` sweep
   (mod-env shape vs test premise), DelugeE2E song silence, `Firmware2IntegrationTest` flag-off voice cleanup.

## Status at this commit
`mvn -pl deluge test` → 20 failures / 275 (down from 32 this arc). `useFirmware2` defaults on. Detailed
per-item notes (with C file:line references) live in the session memory `deluge-firmware2-goal.md`.
