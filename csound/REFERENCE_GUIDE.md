# ChucK-Java Csound Reference Guide

This guide provides technical reference for the ported Csound opcodes in ChucK-Java. Each entry follows a standardized template derived from the original Csound manual, adapted for ChucK's UGen architecture.

---

## Documentation Template

Every ported UGen should document the following:

1.  **Description:** A clear explanation of the mathematical/physical model.
2.  **ChucK Syntax:** How to connect and parameterize the UGen.
3.  **Parameters:** Detail each `.parameter` with its range and default value.
4.  **JDK 25 Optimization:** Notes on whether it uses Vector API or Virtual Threads.
5.  **Technical Note:** Specific implementation details (e.g., delay line lengths, feedback matrix type).
6.  **Usage Example:** A concise code snippet.

---

## Ported Opcodes Reference

### `ReverbSC`
**Description:** 
A high-quality 8-delay line stereo feedback delay network reverb, originally by Sean Costello. It provides a dense, lush tail suitable for professional production. Based on Julius O. Smith III's scattering junction of 8 lossless waveguides of equal characteristic impedance.

**Csound Source:** `Opcodes/reverbsc.c` (414 lines)

**ChucK Syntax:**
```chuck
adc => ReverbSC rev => dac;
```

**Parameters:**
*   `.feedback` (float): Range [0, 1]. Controls the decay time. Default: `0.5`.
*   `.lpFreq` (float): Range [20, 20000]. Low-pass filter frequency in the feedback loop. Default: `10000`.

**Delay Line Characteristics (at 44.1kHz):**

| Line | Length (samples) | Mod Depth (s) | Mod Freq (Hz) | Seed |
|------|-----------------|---------------|---------------|------|
| 0    | 2473            | 0.0010        | 3.100         | 1966 |
| 1    | 2767            | 0.0011        | 3.500         | 29491|
| 2    | 3217            | 0.0017        | 1.110         | 22937|
| 3    | 3557            | 0.0006        | 3.973         | 9830 |
| 4    | 3907            | 0.0010        | 2.341         | 20643|
| 5    | 4127            | 0.0011        | 1.897         | 22937|
| 6    | 2143            | 0.0017        | 0.891         | 29491|
| 7    | 1933            | 0.0006        | 3.221         | 14417|

**Technical Note:**
- 8 modulated delay lines in parallel, each with its own first-order lowpass filter in the feedback path.
- Feedback matrix is a scattering junction: `outputGain = 0.35`, `jpScale = 0.25`.
- Modulation uses 28.4 fixed-point read position (`DELAYPOS_SHIFT = 28`).
- Each delay line's filter: `filterState += dampFact * (in - filterState)`.
- `dampFact` derived from `kLPFreq` parameter.
- Port path: direct struct import to Java, all math in `double`. No JNI.

**JDK 25 Optimization:**
*   Uses the **Vector API** to process the 8 delay lines in parallel (SIMD), reducing CPU overhead compared to the C original on modern processors.

**Usage Example:**
```chuck
adc => ReverbSC rev => dac;
0.8 => rev.feedback; // Long ambient tail
5000 => rev.lpFreq;  // Damp high frequencies
```

---

### `MoogLadder`
**Planned — not yet ported.**

**Description:** 
4-pole 24dB/octave lowpass resonant filter based on Robert Moog's transistor ladder design, with zero-delay feedback (ZDF) topology to preserve resonance at high frequencies.

**Csound Source:** Extract from `Opcodes/newfils.c` (3769 lines total, the Moog section is ~500 lines)

**ChucK Syntax:**
```chuck
saw => MoogLadder flt => dac;
```

**Parameters:**
*   `.freq` (float): Range [20, 20000]. Cutoff frequency. Default: `1000`.
*   `.res` (float): Range [0, 1]. Resonance (emphasis at cutoff). Default: `0`.
*   `.saturation` (float): Range [0, 1]. Input drive / transistor saturation. Default: `0`.

**Technical Note:**
- newfils.c is a monolith containing multiple filter types (SVF, Korg35, Moog, etc.). Only the MoogVCF section will be ported.
- Zero-delay feedback (ZDF) topology: uses prewarped bilinear transform to avoid frequency warping at high resonance.
- The C code uses 4 cascaded 1-pole sections with feedback.
- Dependencies: optional reuse of existing Biquad filter infrastructure in `audio/analysis/`.

---

### `Gendy`
**✅ Fully Implemented**

**Description:** 
Dynamic Stochastic Synthesis oscillator based on Iannis Xenakis' algorithm. It creates waveforms by randomly varying the time and amplitude of a set of breakpoints.

**Csound Source:** `Opcodes/gendy.c` (552 lines)

**ChucK Syntax:**
```chuck
Gendy gen => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.ampDist` | int | 0-5 | 0 | Amplitude distribution type (0=Linear, 1=Cauchy, 2=Exponential, 3=Beta, 4=Logistic, 5=Gauss) |
| `.durDist` | int | 0-5 | 0 | Duration distribution type (same enum) |
| `.numPoints` | int | 2-100 | 12 | Number of breakpoints |
| `.minFreq` | float | 0-20000 | 0 | Minimum frequency |
| `.maxFreq` | float | 0-20000 | 1000 | Maximum frequency |
| `.amp` | float | 0-1 | 0.5 | Amplitude scaling |
| `.distributionParam` | float | 0-1 | 0.5 | Distribution skew parameter |

**Technical Note:**
- Breakpoints are (time, amplitude) pairs. At each sample, the current segment is interpolated.
- When a segment ends, a new breakpoint is generated using the selected distribution function.
- Uses exponential random variate generation (inversion method) for duration distributions.
- Kahan summation may be needed for phase accumulation to prevent drift.
- Self-contained: no dependencies on other opcodes or external libraries.

---

### `PVSAnal` (Phase Vocoder Analysis)
**Planned — not yet ported.**

**Description:** 
Standard FFT-based spectral analysis. Converts time-domain signals into a streaming spectral format (PVS). Builds on existing `FFT.java` infrastructure at `org.chuck.audio.analysis.FFT`.

**Csound Source:** `Opcodes/pvs_ops.c` (pvsanal section) + `pvs_ops.h`

**ChucK Syntax:**
```chuck
adc => PVSAnal anal => PVSynth synth => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.fftSize` | int | 256-16384 | 1024 | FFT window size (power of two) |
| `.hopSize` | int | 1-fftSize | fftSize/4 | Overlap / hop size in samples |
| `.winType` | int | 0-5 | 0 | Window type (0=Hanning, 1=Hamming, 2=Blackman, 3=Kaiser, 4=Rectangular, 5=Blackman-Harris) |
| `.freeze` | int | 0-1 | 0 | Freeze: stops analysis update, latches last spectrum |

**Technical Note:**
- Uses PVS streaming format: per-bin (magnitude + frequency) pairs, unlike DFT's (real + imaginary).
- Existing `FFT.java` produces complex pairs. PVSAnal must convert complex → (mag, freq) format.
- Existing infrastructure: `FFT.java` (ring buffer accumulation), `Windowing.java`, `IFFT.java` all exist.
- The spectral PVS blob is carried via `UAnaBlob.fvals` between UGens.
- Total PVS pkg: 13 files in Csound (`pvs_ops.c` + `pvs_ops.h` + `pvsband.c` + `pvsbasic.c` + etc.)

**JDK 25 Optimization:**
*   **Vector API:** FFT kernels are vectorized for high-throughput analysis using existing FFTAccess infrastructure.
*   **Off-Heap Memory:** Large FFT buffers can be allocated using the FFM API for reduced GC pressure.

**Usage Example:**
```chuck
adc => PVSAnal anal => PVSynth synth => dac;
// Freeze the current spectrum:
1 => anal.freeze;
```

---

### `Partikkel` (Granular Synthesis)
**✅ Fully Implemented**

**Description:** 
A highly configurable granular synthesis UGen. Generates a cloud of grain events, each being a sine oscillator with independent envelope, pan, and pitch.

**Csound Source:** `Opcodes/partikkel.c` (1030 lines)

**ChucK Syntax:**
```chuck
Partikkel g => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.grainRate` | float | 0-10000 | 10 | Grains per second |
| `.grainDuration` | float | 0.001-1.0 | 0.1 | Grain envelope duration (seconds) |
| `.grainFreq` | float | 20-20000 | 440 | Center frequency of grain pitch |
| `.grainSpread` | float | 0-1 | 0 | Random pitch spread across grains |
| `.grainPan` | float | 0-1 | 0.5 | Center pan position |
| `.grainPanSpread` | float | 0-1 | 0 | Random pan spread |
| `.grainAmplitude` | float | 0-1 | 0.5 | Envelope peak amplitude |
| `.grainAttack` | float | 0-1 | 0.1 | Fraction of duration spent in attack |
| `.grainDecay` | float | 0-1 | 0.3 | Fraction of duration spent in decay |
| `.maxGrains` | int | 1-1000 | 64 | Maximum simultaneous grains |
| `.envelopeSelect` | int | 0-2 | 0 | Envelope shape: 0=triangle, 1=exp, 2=gaussian |
| `.randomSeed` | int | 0-65535 | 0 | Seed for random distributions |

**Technical Note:**
- Grain pool architecture: pre-allocated array of grain objects, active-count cursor (ring buffer).
- **Virtual threads are NOT appropriate** — grain rendering is microsecond-scale, thread overhead would dominate.
- Each grain is a sine oscillator with: frequency, amplitude envelope, pan envelope.
- Grain allocation: when a grain's duration expires, deactivate it. When room exists, activate next grain from pool.
- Csound's partikkel also supports wavetable grain source (not just sine) — defer this to a later iteration.

---

### `crossfm` (Cross-Modulated FM)
**Planned — not yet ported.**

**Description:** 
Two oscillators that FM each other via a configurable matrix. Leverages the Dx7Engine operator infrastructure that already exists.

**Csound Source:** `Opcodes/crossfm.c` (507 lines)

**ChucK Syntax:**
```chuck
crossfm xfm => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.freq1` | float | 20-20000 | 220 | Carrier oscillator frequency |
| `.freq2` | float | 20-20000 | 220 | Modulator oscillator frequency |
| `.index1` | float | 0-100 | 1 | Amount osc1 modulates osc2 |
| `.index2` | float | 0-100 | 1 | Amount osc2 modulates osc1 |
| `.feedback` | float | 0-1 | 0 | Self-feedback for each oscillator |
| `.ratio` | float | 0.125-8 | 1 | Fixed frequency ratio (overrides freq2) |

**Technical Note:**
- Each oscillator is a sine (Dx7Engine's `Sin::lookup`). Phase modulation via `phase += freq + modulation`.
- The modulation matrix is 2×2: `phase_inc[n] = freq[n] + index[n][m] * sin(phase[m])`.
- Cross-modulation can produce chaotic/nonlinear spectra not possible in standard FM (single carrier→modulator).
- Reuses Dx7Engine's lookup tables (Sin, Exp2, Freqlut).

---

### `freeverb`
**✅ Fully Implemented**

**Description:** 
Classic Schroeder/Moorer reverb: 8 comb filters in parallel → 4 allpass filters in series. A simpler, cheaper alternative to ReverbSC, useful for "spring" or "plate" reverb styles.

**Csound Source:** `Opcodes/freeverb.c` (284 lines)

**ChucK Syntax:**
```chuck
adc => Freeverb rev => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.roomSize` | float | 0-1 | 0.5 | Feedback gain in comb filters (decay time) |
| `.damp` | float | 0-1 | 0.5 | High-frequency damping per comb filter |
| `.width` | float | 0-1 | 0.5 | Stereo width (diffusion between channels) |

**Comb filter delay lengths (samples, left/right stereo pairs):**
- Comb 0: 1617 / 1557
- Comb 1: 1409 / 1349
- Comb 2: 1221 / 1161
- Comb 3: 1053 / 993
- Allpass 0: 225 / 201
- Allpass 1: 341 / 317
- Allpass 2: 441 / 417
- Allpass 3: 563 / 539

**Technical Note:**
- Cheaper than ReverbSC (fewer multiplications per sample, no modulation).
- Good default for "classic" reverb sounds; ReverbSC recommended for production.
- Self-contained: no dependencies.

---

### `modal4`
**✅ Fully Implemented**

**Description:** 
Modal synthesis with 4 parallel 2nd-order resonant filters. Models the resonant modes of struck objects (metal, glass, wood, ceramic).

**Csound Source:** `Opcodes/modal4.c` (534 lines)

**ChucK Syntax:**
```chuck
impulse => modal4 m => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.freq1..4` | float | 20-20000 | Mode frequencies for each resonator |
| `.decay1..4` | float | 0.001-10 | Decay time (seconds) for each mode |
| `.amp1..4` | float | 0-1 | Relative amplitude of each mode |
| `.strikePosition` | float | 0-1 | Where on the object the strike occurs |
| `.stiffness` | float | 0-1 | Material stiffness (affects mode ratios) |

**Technical Note:**
- Each mode is a 2nd-order IIR resonator: `y[n] = b0*x[n] - a1*y[n-1] - a2*y[n-2]`.
- Frequencies are the partials (not necessarily harmonic), decays control the pole radius.
- Material presets: metal (high Q, inharmonic), glass (high Q, near-harmonic), wood (low Q, inharmonic), drum (low Q, harmonic).
- Self-contained: resonator is a biquad, no external filter dependencies.

---

### `exciter`
**Planned — not yet ported.**

**Description:** 
Harmonic exciter / aural exciter. Adds even- and odd-order harmonics to dull audio signals, restoring "presence" and "air" without increasing perceived loudness.

**Csound Source:** `Opcodes/exciter.c` (~200 lines)

**ChucK Syntax:**
```chuck
adc => Exciter ex => dac;
```

**Parameters:**
| Parameter | Type | Range | Default | Description |
|-----------|------|-------|---------|-------------|
| `.drive` | float | 0-1 | 0.3 | Amount of harmonic generation |
| `.tone` | float | 0-1 | 0.5 | High-pass filter before distortion |
| `.mix` | float | 0-1 | 0.5 | Wet/dry mix |

**Technical Note:**
- Drive signal is high-pass filtered, then soft-clipped (tanh or cubic), then mixed back with dry.
- Simple and effective: standalone, no external dependencies.

---

### `ScannedSynth`
**✅ Fully Implemented**

**Description:**
Scanned Synthesis oscillator based on Bill Verplank, Max Mathews, and Rob Shaw's scanned synthesis algorithm. It simulates a 1D string/mass-spring system under force, whose state variables are scanned (periodically sampled) at audio rate to produce dynamic, organic timbres.

**Csound Source:** `Opcodes/scansyn.c` (857 lines)

**ChucK Syntax:**
```chuck
ScannedSynth scan => dac;
```

**Parameters:**
*   `.spring` (float): Mass-spring model stiffness constant. Default: `0.5`.
*   `.damping` (float): String state energy loss scaling factor. Default: `0.1`.
*   `.mass` (float): Mass weight of nodes in the ODE equation. Default: `1.0`.
*   `.frequency` (float): Rate at which the mass-spring grid positions are scanned to form audio cycles. Default: `100.0`.
*   `.nodes` (int): Number of masses in the string state vector. Range: [8, 128]. Default: `64`.

**Technical Note:**
- String state ODE: solved using Euler or Leapfrog integration steps.
- Audio lookup scanner reads interpolated positions of masses along string paths.
- Mass/Spring state updates are separated from the faster audio scan frequency to prevent phase artifacts.
- Self-contained: no external dependencies.

---

### Future Porting Candidates (Research-Grade)
- **babo** (935 lines) — Banded waveguide physical model: gourd/guitar body resonance. Reuses FDN delay structure like ReverbSC but as a resonant body.
- **wave-terrain** (305 lines) — Wave terrain synthesis: orbit paths over a 2D terrain surface. Unique sound, small code footprint.
- **pvsdemix** — Source separation from PVS analysis (demixing drums, bass, vocals). High value, high complexity.

---

## Reusable Csound Documentation Patterns

When iterating on these docs, reuse the following "wisdom" from the original Csound manual:

1.  **Mathematical Clarity:** Csound docs always specify the exact formula for the transfer function. Keep this in the `Technical Note` section.
2.  **Range Constraints:** Clearly mark parameters that can cause instability if exceeded (e.g., feedback > 1.0).
3.  **Historical Context:** If an algorithm is a classic (e.g., Moog, Chowning FM, Xenakis), mention its origin.
4.  **Delay Line Constants:** Always list exact delay line lengths. They are critical for accurate reproduction.
5.  **Dependencies:** Document per-opcode dependencies (e.g., "depends on FFT.java") so port ordering is clear.

---

## Porting Progress Checklist

- [x] ReverbSC — 8-delay FDN, standalone. Delay line lengths documented above.
- [ ] MoogLadder — Extract from newfils.c (3769 lines → ~500 for Moog section).
- [x] freeverb — 284 lines, simpler reverb (implemented as `FreeVerb`).
- [ ] exciter — ~200 lines, harmonic exciter.
- [ ] PVSAnal/PVSynth — Build on existing FFT.java/IFFT.java.
- [ ] PVS ops (blur, scale, shift) — Spectral processing.
- [ ] crossfm — Reuse Dx7Engine infra, 507 lines.
- [ ] fm4op — 4-op FM, 1151 lines.
- [x] Gendy — Xenakis stochastic, 552 lines (implemented as `Gendy`).
- [x] ScannedSynth — Mass-spring ODE, 857 lines (implemented as `ScannedSynth`).
- [x] Partikkel — Grain pool, 1030 lines (implemented as `Partikkel`).
- [x] modal4 — Modal synthesis, 534 lines (implemented as `Modal4`).
- [ ] sndwarp — Sound warping, 390 lines.
- [ ] babo — Waveguide physical model (deferred).
- [ ] wave-terrain — Wave terrain synthesis (deferred).
