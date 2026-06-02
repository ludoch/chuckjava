# Deluge C++ vs. Java Port: Precise Logic & DSP Audit

This document provides a rigorous mathematical and structural comparison between the original C++ codebase (`../DelugeFirmware/src/deluge/`) and our high-fidelity Java port (`chuckjava/deluge/src/main/java/org/chuck/deluge/`).

---

## 1. Core Mathematical Paradigm & Q31 Fixed-Point Arithmetic

Both engines use a **32-bit signed fixed-point (Q31) format** to represent signal paths and parameters. The mathematical representations are aligned as follows:

- **Full scale (1.0)** is represented by `2147483647` (`ONE` in Q31).
- **Negative scale (-1.0)** is represented by `-2147483648` (`NEGATIVE_ONE` in Q31).

### Side-by-Side Arithmetic Functions

| Math Utility | C++ (ARM Assembly or Generic Fallback) | Java (`Q31.java`) | Logic Identity / Discrepancy |
|--------------|-----------------------------------------|-------------------|-----------------------------|
| `multiply_32x32_rshift32(a, b)` | `(int32_t)(((int64_t)a * b) >> 32)` or `smmul` assembly | `(int) (((long) a * b) >> 32)` | **Identical**. Both extract the high 32 bits of the 64-bit product (equivalent to scaling by $1/2^{32}$). |
| `multiply_32x32_rshift32_rounded(a, b)` | `(int32_t)(((int64_t)a * b + 0x80000000) >> 32)` or `smmulr` | `(int) (((long) a * b + 0x80000000L) >> 32)` | **Identical**. Standard round-to-nearest scaling. |
| `multiply_accumulate_32x32_rshift32(sum, a, b)` | `(int32_t)(((((int64_t)sum) << 32) + ((int64_t)a * b)) >> 32)` or `smmla` | `sum + (int) (((long) a * b) >> 32)` | **Identical**. The C++ version shifts the 32-bit sum to the high half of the 64-bit register before adding the 64-bit product. Since the lower 32 bits have zero-addition, no carry occurs, making it mathematically identical to standard Java summation. |
| `q31_mult(a, b)` | `(smmul(a, b) * 2)` | `(int) (((long) a * b) >> 31)` | **Identical**. Standard Q31 multiplication with standard floor truncation. |
| `signedSaturate(val, bits)` | `ssat` instruction | Custom bounds checking (saturating at $\pm 2^{\text{bits}-1}$) | **Identical**. Saturation logic matches perfectly. |

### ⚠️ LCG Pseudo-Random Noise Discrepancy (Fixed!)
An audit of the linear congruential generators (LCG) revealed a critical constant error:
- **C++ LCG Constant**: Uses the classic Marsaglia addition constant `1234567`:
  $$\text{jcong} = 69069 \times \text{jcong} + 1234567$$
- **Java LCG Constant (Previous)**: Was mistakenly set to a standard generic LCG constant `12345`:
  $$\text{jcong} = 69069 \times \text{jcong} + 12345$$

> [!TIP]
> **Fidelity Correction Applied**: I have corrected the constant in [FirmwareUtils.java](file:///Users/ludo/a/chuckjava/deluge/src/main/java/org/chuck/deluge/firmware/util/FirmwareUtils.java#L54) to `1234567` so that our analog ladder-filter random walk and sample random noise distributions are mathematically identical to the original physical hardware.

---

## 2. Virtual Analog (VA) Oscillator Rendering

In the original C++ firmware, the voice oscillator engine (`oscillator.cpp`) employs a dual-strategy approach to balance CPU utilization and anti-aliasing:

### C++ Band-Limiting Strategy vs. Java Naive Fallback

1. **High-Frequency Anti-Aliasing (C++)**:
   - For fundamental note frequencies below **1420Hz** (or when CPU direness is critical), the C++ engine uses light **crude mathematical wave generators** (`getTriangleSmall`, raw step phase integration, etc.).
   - For fundamental note frequencies **above 1420Hz**, it automatically shifts to **anti-aliased multi-sampled lookup tables** (`saw.cpp`/`square.cpp` tables and `triangleWaveAntiAliasing1` through `triangleWaveAntiAliasing31` tables) to eliminate digital foldback noise (aliasing).

2. **Current Java Port Status (Audio Quality Gap)**:
   - **Naive Generators Only**: The current Java port ALWAYS renders standard virtual analog oscillators (SAW, SQUARE, TRIANGLE) using the basic/crude math routines (e.g. `renderCrudeTriangle`, simple step saw increments), bypassing the multi-sampled tables entirely.
   - **Fidelity Impact**: Medium/high notes rendered in the Java port will exhibit significant digital aliasing sidebands, whereas the original hardware produces a smooth, band-limited analog timbre.

### Triangle Wave Waveform Calculation Comparison
To ensure the naive algorithms were at least functionally correct, a side-by-side math review was performed:

- **C++ Crude Triangle (`getTriangleSmall`)**:
  ```c
  if (phase >= 2147483648u) phase = -phase;
  return phase - 1073741824; // Returns half-scale [-0.5, 0.5] in Q31
  // Shifted left by 1 at the render terminal for full-scale [-1.0, 1.0].
  ```
- **Java Crude Triangle (`renderCrudeTriangle`)**:
  ```java
  int val = (currentPhase < 0) ? -currentPhase : currentPhase;
  val = (val - 1073741824) << 1; // Returns full-scale [-1.0, 1.0] in Q31
  ```

> [!NOTE]
> **Logic Equivalence Proof**: 
> In Java's signed system, `currentPhase < 0` is equivalent to C++'s unsigned `phase >= 2147483648u`. 
> Negation of negative integers in two's complement behavior is identical to unsigned negation, so both generate the exact same symmetric triangle shape. The Java port applies the scaling shift (`<< 1`) inside the loop, while C++ applies it after the generator returns, arriving at the same exact samples.

---

## 3. Filters & Non-linear Saturation (State-Variable Filter ZDF)

The state-variable filter (ZDF topology) is highly accurate to the original zero-delay feedback (ZDF) loops. However, a significant gap was discovered in the feedback saturation code:

### ⚠️ Missing 2D State-Space Anti-Aliased Saturation

In the C++ firmware, the SVF and other saturated modules use a high-quality **2-dimensional anti-aliased hyperbolic tangent lookup** (`getTanHAntialiased`) to prevent high-frequency distortion products from folding back into the feedback paths:

```c
// C++ Anti-Aliased Saturation
int32_t toReturn = interpolateTableSigned2d(workingValue, *lastWorkingValue, 32, 32, &tanH2d[0][0], 7, 6)
                   >> (saturationAmount + 1);
```

In the Java port:
- **No 2D Table**: The `tanH2d` 2D table array is completely absent from `LookupTables.java`.
- **1D Saturation Fallback**: `getTanHAntialiased` inside [FirmwareUtils.java](file:///Users/ludo/a/chuckjava/deluge/src/main/java/org/chuck/deluge/firmware/util/FirmwareUtils.java#L160-L163) has been written as a simple fallback that returns non-anti-aliased standard 1D tanh values:
  ```java
  public static int getTanHAntialiased(int input, int[] lastWorkingValue, int saturationAmount) {
    lastWorkingValue[0] = (int) (lshiftAndSaturateUnknown(input, saturationAmount) + 2147483648L);
    return getTanHUnknown(input, saturationAmount) << 1;
  }
  ```
- **Fidelity Impact**: In high-gain settings (such as high filter drive or saturation), the feedback path in the SVF filter will generate digital aliasing sidebands, reducing the warmth and smooth clipping character of the original physical filter model.

---

## 4. Parameter Management & Modulations

The C++ core tracks modulations through a structured and heavily optimized object hierarchy, whereas Java maps them through simple flat tables.

- **C++ Parameter System**:
  - Uses `ParamManager` which stores dynamic `ParamCollectionSummary` slots for live modulation, tracking each parameter node independently.
  - Patch cables are applied directly using 32-bit operations, leveraging compiler-optimized structures.
- **Java Parameter System**:
  - Uses simple flat arrays (such as `paramNeutralValues` and `paramFinalValues`) read and overwritten on every loop tick by `FirmwareVoice.java`.
  - While this flat model successfully replicates correct routing, it lacks the formal object-oriented encapsulation of individual automated parameters, complicating future modular extensions.

---

## 5. Summary of Identified Gaps

To maintain strict parity with community firmware commit community **c1.3.0**, the following technical debt should be addressed in subsequent cycles:

| Gap ID | Description | Severity | Target Location | Action Required |
|--------|-------------|----------|-----------------|-----------------|
| **GAP-01** | Missing 2D Anti-Aliased Saturation | ✅ Resolved | `TanHLookupTable.java`, `FirmwareUtils.java` | Ported the 2D state-space table `tanH2d` and implemented bilinear 2D interpolation `interpolateTableSigned2d`. |
| **GAP-02** | Naive basic VA Waveforms (No Band-limiting) | ✅ Resolved | `WavetableLoader.java`, `Oscillator.java` | Ported multi-sampled Saw/Square wave tables to binary resource paths and ported Triangle multi-sampled tables to Java classes. |
| **GAP-03** | Convoluted sign mask in `renderSineSync` | ✅ Resolved | `Oscillator.java` | Refactored sign-extend mask loops back to a direct standard `interpolateTableSigned` library call. |
| **GAP-04** | RINGMOD synth mode not rendered (degraded to a plain oscillator sum) | ✅ Resolved | `FirmwareVoice.java` | Ported `voice.cpp`'s `SynthMode::RINGMOD` path: render osc A and B at fixed unit amplitude, then output `round(mult(mult(A,B), amplitudeForRingMod))` with the firmware's per-wave-type compensation (saw `<<1`, wavetable `<<2`). Verified by `RingModParityTest` (frequency doubling for equal-frequency sines). |
| **GAP-05** | Non-firmware `buffer >>= 1\|2` multi-source clipping hack | ✅ Resolved | `FirmwareVoice.java` | Removed; it attenuated multi-oscillator patches by 6–12 dB vs hardware. The firmware sums sources straight (volumes are pre-capped; unison uses `1/sqrt(numUnison)`, which the port already matches) and relies on the master limiter for peaks. |
| **GAP-06** | Mod FX inert in the pure engine | ✅ Resolved | `FirmwareFactory.java`, `FirmwareSound.java` | `FirmwareSound.modFXType` was never set and `processModFX` was called with hardcoded `rate=100, depth=100` (≈ DC LFO, ≈ zero depth), so chorus/flanger/phaser patches produced no effect. `mapModelToSound` (and the kit/drum path) now map the model's mod-FX type plus rate (Hz→Q32 LFO increment), depth, offset and feedback (0..1→Q31) into the sound; `renderInternal` passes them through. Verified by `ModFxParityTest`. |
| **GAP-07** | Reverb silent / unconfigured in the pure engine | ✅ Resolved | `GlobalEffectable.java`, `FirmwareFactory.java`, `PureFirmwareEngine.java` | `GlobalEffectable.renderOutput` hardcoded `reverbSendAmount = 0`, so no sound fed the master reverb bus — reverb was entirely silent. And `masterReverb`'s model/room/damping/width were never set, so even with send it ignored the song's reverb settings. Added a per-sound `reverbSendAmount` (mapped from the model's reverb send) and a per-block sync of the master reverb model + room/damping/width from the song globals. Verified by `ReverbSendParityTest`. |
| **GAP-08** | Master delay produces no echo in the pure engine | ✅ Resolved | `DelayBuffer.java`, `FirmwareAudioEngine.java`, `PureFirmwareEngine.java` | **Root cause:** `DelayBuffer.advance` used signed `int` for the 8-bit position diff (the firmware uses `uint8_t`). At every high-byte wrap the diff went negative, so `while (shortPosDiff > 0)` skipped the callback that advances the write head and decrements `sizeLeftUntilBufferSwap` — the swap counter froze, `primaryBuffer` never activated, and only the dry signal passed. Fixed by masking the position and diff to 8 bits. **Wiring:** call `setupWorkingState` before `masterDelay.process`; drive the delay time externally (`syncLevel = NONE`) with `userDelayRate = kNeutralSize·kMaxSampleValue / (delaySeconds·44100)` (inverse of `getIdealBufferSizeFromRate`), computing `delaySeconds` from the tempo when synced or `G_DELAY_TIME` when free-running, plus feedback/ping-pong/analog from the song. Verified by `DelayParityTest` (impulse → decaying echo tail; silent at feedback 0). |
| **GAP-09** | Bitcrush / sample-rate reduction not implemented in the pure engine | ✅ Resolved | `SrrBitcrushProcessor.java`, `FirmwareSound.java`, `FirmwareFactory.java` | Ported `ModControllableAudio::processSRRAndBitcrushing` (bit-depth masking + decimation with linear up/down interpolation) into `SrrBitcrushProcessor`, inserted it into the sound FX chain before mod FX (matching the firmware order), and wired the bitcrush/SRR amounts (0..1 → bipolar Q31) from the synth & drum models. Verified by `SrrBitcrushParityTest` (bitcrush clears low bits; SRR produces sample-hold staircases). |
| **GAP-10** | Bass/treble EQ not implemented in the pure engine | ✅ Resolved | `EqProcessor.java`, `FirmwareSound.java`, `FirmwareFactory.java` | Ported `ModControllableAudio::doEQ` (stereo bass/treble shelving via two one-pole splits with persistent state) into `EqProcessor`, ran it in the sound FX chain after mod FX, and wired the bass/treble amounts (model dB in [-12,12] → bipolar Q31, 0 dB = flat) from the synth & drum models. EQ frequencies use the firmware defaults (`getExp(120000000,0)` / `getExp(700000000,0)`) since the model doesn't carry the freq knobs. Verified by `EqParityTest` (flat is transparent; treble boost raises HF energy; bass boost raises LF energy). |
| **GAP-11** | Sample playback used linear interpolation, not windowed-sinc | ✅ Resolved | `WindowedSincKernel.java`, `SincInterpolator.java`, `VoiceSample.java` | `VoiceSample` interpolated samples linearly, so pitched/transposed samples aliased far more than hardware (the Deluge default is windowed-sinc). Extracted the `windowedSincKernel[7][17][16]` table from `interpolate.cpp` and ported the 16-tap sinc + `getWhichKernel` rate→kernel selection (sharper kernels resist aliasing as pitch rises), adapted to the float sample pipeline. Verified by `SincInterpolatorTest` (kernel 0 phase 0 = impulse; all kernels unity DC gain; phase-aligned read is passthrough; higher rate → stronger kernel). |
| **GAP-12** | Arpeggiator not driven in the pure engine | ✅ Resolved | `FirmwareSound.java`, `Arpeggiator.java`, `FirmwareFactory.java`, `PureFirmwareEngine.java` | The arp was instantiated per voice but never ticked. Moved it to the **sound** level: `triggerNote`/`releaseNote` now feed held notes into the arp when it's enabled (instead of triggering voices directly); `renderInternal` advances `arpeggiator.render` each block (before the silent-bypass) and actions the emitted note-on/off via new `triggerVoice`/`releaseVoice` helpers. `FirmwareFactory.configureArp` maps the `ArpModel` (mode, octave mode, octaves, gate, step-repeat, note-division) for synths & drums; `PureFirmwareEngine` derives the arp clock (`arpPhaseIncrement`) from the tempo and note division each block. Verified by `ArpParityTest` (a held chord steps through its notes one at a time on the arp clock, not as a sustained chord). |
| **GAP-13a** | Granular mod-FX (GRAIN) not wired | ✅ Resolved | `FirmwareSound.java`, `PureFirmwareEngine.java` | `ModFXType.GRAIN` was routed to the LFO-based `ModFXProcessor` and did nothing. `renderInternal` now routes GRAIN to the per-sound `GranularProcessor.processGrainFX` (rate/mix/density/pitch-randomness from the mod-FX params + tempo). Verified by `GranularParityTest` (bounded, non-silent grain texture that differs from dry). Grain-rate calibration is approximate — the firmware feeds the raw mod-FX rate param domain (`quickLog`), so high rates clamp to a fixed grain rate; the effect is functional but not bit-matched. |
| **GAP-13b** | Sample time-stretch (STRETCH mode) not wired | ✅ Resolved | `VoiceSample.java`, `Sample.java` | `VoiceSample` now uses the `TimeStretcher` when a sample is in pitch-and-speed-independent mode (`settings.timestretch`): the duration advance (`tsRatio`) is fixed per note while the note pitch is the read rate, so a sample plays over its natural duration regardless of pitch (firmware `getSpeedParamForNoSyncing`'s pitch-independent case). `Sample.getMonoIntData()` lazily provides the mono Q31 buffer the stretcher needs. Verified by `TimeStretchParityTest` (octave-up keeps ~the same duration with stretch; ~half without). Note: clip-length-synced STRETCH (ratio = sampleLength/noteLength) still uses the natural-duration ratio rather than the sequencer sync length. |
| **GAP-14** | DX7 patches not rendered by the DX7 engine in the pure engine | ✅ Resolved | `FirmwareSound.java`, `FirmwareFactory.java`, `FirmwareVoice.java` | A DX7 track's 156-byte patch was parsed into the model but never wired into the pure engine — it played the Deluge's native FM (hardcoded algorithm 0, `fmRatio1/2`), so DX7 songs sounded completely different. `FirmwareFactory` now parses the patch hex onto the sound (`dx7Patch`, engine type, random detune); `FirmwareVoice` renders DX7 sounds through the real Dexed `Dx7Engine` (its per-operator EGs are the amplitude env, then the per-voice filter — matching the legacy DX7 chain) instead of the native-FM mapping. Verified by `Dx7ParityTest` (a real patch produces bounded audio that differs substantially from the FM fallback). Loudness scaling (raw DX7 is ±~2.3) is approximate; clip-synced nuances unchanged. |

> [!NOTE]
> The prose in §2 and §3 above documents the **pre-fix** state at the time of the original audit.
> Per the summary table, GAP-01 (2D anti-aliased tanh) and GAP-02 (band-limited oscillator tables)
> are now implemented in code (`TanHLookupTable.java`, `SawLookupTables`/`SquareLookupTables`/
> `TriangleLookupTables`, `Oscillator.renderOsc`); the default app engine (`PureFirmwareEngine`,
> pure mode) uses this high-fidelity path — the legacy ChucK-DSL path only runs with `--hifi`.

---

*Prepared by Antigravity; GAP-04/05 and reconciliation by Claude.*
