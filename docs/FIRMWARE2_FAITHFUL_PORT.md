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

### Mandatory pre-edit protocol (the gate — do this BEFORE writing ANY firmware2 code)
1. Open the exact C function in `~/a/DelugeFirmware/src/deluge/` that this code corresponds to. Read it.
2. Mirror the C's **structure**: same control flow, loops, per-source/per-unison/per-block organization,
   function decomposition, names. The C is the skeleton; Java fills it in. Cite the C `file:line` in comments.
3. Do **not** let the existing chuckjava model shape the port. If you're about to reuse a chuckjava field/
   class/structure (e.g. a whole-voice flag, `firmware/` classes, `org.chuck.audio.*`), STOP and check: does
   the C do it that way? If the C structures it differently (e.g. DX7 is a per-source `OscType::DX7` in
   `voice.cpp`, not a whole-voice mode), follow the C — add enum values / per-source state / parser changes
   to match the C's model.
4. If the C model and the existing chuckjava model genuinely differ, that is a **deviation** → STOP, surface
   it, get explicit approval before writing. Never silently adapt to the existing model.
5. After writing, diff the Java against the C line-by-line.

Two incidents where this rule was broken (both = letting existing Java shape the port): a reconstructed
patcher flow instead of porting `patcher.cpp`; a whole-voice `dx7Active` mode instead of `voice.cpp`'s
per-source `OscType::DX7`. The cure is always: open the C, mirror its structure.

### Numeric-type mapping (the #1 error source)
- C `int32_t` → Java `int` (both 32-bit two's-complement; wraparound matches — rely on it).
- C `uint32_t` → Java `int` with **unsigned ops**: `>>>`, `Integer.compareUnsigned`, `& 0xFFFFFFFFL`.
- C `uint16_t` → Java `int` with `& 0xFFFF` at each step the C truncates (e.g. table values, log/exp sums).
- C `int64_t`/`uint64_t` → Java `long`; 64-bit products via `(long)a * b`.
- **Never** use `float`/`double` for Q31/Q32 fixed-point. Only where the C literally uses float/double
  (e.g. table generators, `log2f` for DX7 pitch).
- NEON SIMD → scalar per-lane: `vqdmulhq_s32(a,b)` = `(int)(((long)a*b) >> 31)`; `vld1q/vst1q` = unrolled
  per-sample loop; preserve exact shift counts + rounding (`*_rshift32_rounded` adds the round constant).
- C virtual dispatch (`obj->method()`) → a static conditional in Java is acceptable **iff** the method
  bodies are faithfully transcribed (e.g. `DxPatch.core->render` → `if (useMkI) EngineMkI.render else FmCore.render`).

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
- **DX7 — fully ported** (`dsp/dx/`):
  - `math_lut.cpp` → `Dx7Tables`: exp2 scale fixed (`1<<30`, was `1<<24` → 64× too small) + `SIN_TAB`/`TANH_TAB`/
    `FREQ_LUT` + `sin/tanh/freq` lookups; `Freqlut` wired.
  - `fm_op_kernel.cpp` + `fm_core.cpp` → `FmCore` (modern MSFA engine): `Sin::lookup` (Q24) → `(y*gain)>>24`,
    post-increment phase, feedback `(y0+y)>>(fb_shift+1)`; gain via the real `Exp2::lookup` (`Dx7Tables.exp2Lookup`).
  - `EngineMkI.cpp` → `EngineMkI` (Mark I engine): gain = ENV in the **log domain** via `mkiSin`
    (`sinLogTable`/`sinExpTable`), `compute`/`compute_pure`/`compute_fb` + `compute_fb2`(ALGO 6)/`compute_fb3`(ALGO 4),
    `render` override (inverted `gain<=ENV_MAX-100` threshold, algo 3/5+fb → `ops[0]=0xc4`).
  - **Integration**: DX7 is a per-source `OscType.DX7` rendered in `Voice`'s source loop (`voice.cpp:2371-2387`:
    `adjpitch = log2(phaseIncrement)*(1<<24) - 278023814`; `dxVoice.compute`; `out += mult_32x32_rshift32(uniBuf,
    sourceAmplitude) << 6`). Per-source `Dx7Voice`/`DxPatch`; `FirmwareSound.dx7Patch` maps source 0 → `OscType.DX7`.
  - **Engine selection** (`dx7note.cpp:66-79`): `DxPatch.updateEngineMode` sets `useMkI` (engineMode==2, or auto +
    feedback + algo∈{3,5}); `Dx7Voice.compute` dispatches `EngineMkI.render` vs `FmCore.render`.
  - Uses the C-port `Dx7Voice`/`FmCore`/`EngineMkI`, **not** the legacy `org.chuck.audio.util.Dx7Engine`.
    `Dx7ParityTest`/`Dx7VoiceTest` pass.
- `PhaseIncrementFineTuner` + `centAdjustTableSmall[257]`, `PatchSource` enum — verbatim.

## Sanctioned deviations (explicitly approved)

These are the *only* places firmware2 intentionally departs from a literal transcription. Each is
recorded here with the C it relates to and the path back to full faithfulness. Adding to this list
requires explicit user approval (per the absolute rule).

### SD-1 — Live sample interpolation forced to linear (`SampleReader.java`)
**What:** `SampleReader.readSamplesNative` branches on a global flag
`org.deluge.firmware.engine.FirmwareAudioEngine.realTimeMode`. When `true` (set by
`JavaAudioDriver` for all real-time playback) it uses 2-tap **linear** interpolation
(`oscPos >>> 9`, the int16 strength split); when `false` (offline WAV export, all fidelity/golden
tests) it uses the faithful 24-tap windowed-**sinc** `SincInterpolator.interpolateWide`.
Motivation: CPU headroom on large songs (~31 tracks) — the same draft-vs-render quality split
shipped by other DAWs (e.g. Ableton "Hi-Q").

**Why this is a *small* deviation, not an invention:** the Deluge firmware itself drops to linear
interpolation to save CPU. The pieces the Java fast path mirrors:
- `dsp/interpolate/interpolate.cpp:70` `Interpolator::interpolateLinear` — *byte-identical* to the
  Java fast path: `strength2 = phase >> 9`, `strength1 = INT16_MAX - strength2`, 2-tap mix. (The
  Java shift was corrected `>>>5 → >>>9` to match this exactly.)
- `model/sample/sample_low_level_reader.cpp:1024` (sinc) vs `:1081` (linear) — the hardware picks per
  read, branching on `if (interpolationBufferSize > 2)`.
- `model/sample/sample_controls.cpp:29` `getInterpolationBufferSize(phaseIncrement)` returns `2`
  (→ linear) when either (a) the source's `InterpolationMode::LINEAR` user setting is on, or
  (b) **CPU direness fallback**: `if (AudioEngine::cpuDireness)` and the note is pitched up enough
  (`octave >= 26 - (cpuDireness >> 2)`); otherwise `kInterpolationMaxNumSamples` (→ sinc).
- `processing/engines/audio_engine.cpp:472` `setDireness` — `cpuDireness` (0..14) is adaptive,
  measured from real DSP render time per block; it rises when a block overruns and decays (with
  hysteresis) when comfortable.

**How the deviation differs from the C (what makes it not yet faithful):** the hardware decision is
**per-source, per-block, adaptive, and pitch-aware** — under load it linearises *only* pitched-up
samples and keeps sinc for everything else; with no load (`cpuDireness == 0`, the desktop case) it
*always* uses sinc. The Java flag is a blunt global: live playback uses linear for **all** sample
voices at **all** pitches regardless of measured load.

**Scope of impact:** read in exactly one place (`SampleReader.java`, the `realTimeMode` branch).
Affects only sample-based voices (kits with WAV samples, sampled instruments, audio tracks,
time-stretch/granular). Pure synth oscillators (subtractive/FM/wavetable) never touch `SampleReader`
and are unaffected. Offline export and all golden/fidelity tests run with `realTimeMode = false`, so
parity coverage is unchanged. Audible side effects on the live monitor only: mild HF dulling
(linear ≈ −3.9 dB at Nyquist) and aliasing on samples pitched **up** (linear stopband rejection
~12–15 dB vs sinc's 60+).

**Path back to faithful (preferred future work):** port `SampleControls.getInterpolationBufferSize`
and the `cpuDireness` measurement loop (`setDireness`) into the Java audio engine, then branch on the
returned buffer size (`> 2` → sinc, `== 2` → linear) instead of `realTimeMode`. This yields the exact
hardware behaviour: sinc by default on desktop, automatic linear fallback only under genuine measured
load and only for pitched-up samples, plus support for the per-source `InterpolationMode::LINEAR`
user setting. Until then, `realTimeMode` is an approved CPU-saving approximation of that mechanism.

## Remaining work

> **The prioritized, test-mapped roadmap for the remaining subsystems lives in
> [`FIRMWARE2_PORT_ROADMAP.md`](FIRMWARE2_PORT_ROADMAP.md)** — each failing test mapped to its C source,
> split into faithful C ports (A), bridge fixes (B), and hardware-calibration (C). The summary below remains.

### Remaining work (each its own faithful pass)
1. **DX7 polish**: `OscType` enum order differs from `definitions_cxx.hpp:367` (SAW/SQUARE/ANALOG swapped) —
   name-based so functionally safe, reorder for full faithfulness. `dx7EngineType` (chuckjava -1/0/1) →
   `engineMode` (C 0/1/2) mapping for forced modern/MkI (auto-detect already faithful).
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
