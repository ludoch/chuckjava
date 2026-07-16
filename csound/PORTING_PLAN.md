# Csound to ChucK-Java Porting Plan

This document outlines the strategic roadmap for porting high-value algorithms and features from the Csound C repository to the ChucK-Java and Deluge-Java ecosystem.

## 1. Architectural Strategy

We will leverage **JDK 25** advanced features to ensure the Java port is as efficient as (or more efficient than) the original C implementation, particularly focusing on parallelism and SIMD.

### JDK 25 Integration Points
*   **Project Loom (Virtual Threads):** 
    *   *Where:* Used in **Granular Synthesis (`partikkel`)** and **Asynchronous Disk I/O (`diskin2`)**.
    *   *Why:* Each grain in a high-density cloud can be managed by a virtual thread if it involves complex parameter modulation, or more likely, we use virtual threads to handle the orchestration of grain pools without blocking the main audio thread.
*   **Vector API (`jdk.incubator.vector`):** 
    *   *Where:* **FFT/IFFT kernels**, **Spectral Processing (PVS)**, and **Multi-tap Delay lines**.
    *   *Why:* To achieve C-like performance for bin-by-bin multiplications in the spectral domain and parallel filter coefficient calculations.
*   **Foreign Function & Memory API (FFM):**
    *   *Where:* Large **Spectral Buffers** and **Delay Lines**.
    *   *Why:* To allocate memory outside the JVM heap, avoiding GC pauses for multi-gigabyte sample buffers or long delay lines used in physical modeling.

---

## 2. Architectural Strategy: The "Pure Java" Native Path

A critical decision for the ChucK-Java port is the move away from traditional JNI (Java Native Interface) in favor of the **JDK 25 Foreign Function & Memory (FFM) API**.

### Native Interop Strategy
As demonstrated in our sibling project `rtmidijava`, we can achieve high-performance interaction with native system libraries (like `winmm.dll`, `libasound.so`, or `CoreMIDI`) without writing a single line of C/C++ wrapper code.

*   **JNI vs. FFM:** We will **NOT** use JNI or the latest JNI support in JDK 25 for new porting efforts. JNI adds significant complexity to the build system and introduces "brittle" boundaries.
*   **The FFM Advantage:**
    *   **In-Java Declarations:** We define native struct layouts (like `MIDIHDR` or Csound's `PVSDAT`) and function signatures (using `Linker` and `SymbolLookup`) directly in Java code.
    *   **Performance:** FFM is designed to be as fast as JNI while providing better safety and off-heap memory management via `Arena`.
    *   **Zero-Overhead Bindings:** We can bind to Csound's internal C functions or system audio APIs directly from `chuck-core`.

---

## 3. Feature Mapping & Implementation Sites

| Feature Class | Csound Source | ChucK-Java Implementation Site | Target Use Case |
| :--- | :--- | :--- | :--- |
| **Spectral (PVS)** | `Opcodes/pvs*.c` | `chuck-core/src/main/java/org/chuck/core/spectral/` | Advanced sound design, time-stretching |
| **Reverb SC** | `Opcodes/reverbsc.c` | `chuck-core/src/main/java/org/chuck/ugens/ReverbSC.java` | Global master effect for Deluge & ChucK |
| **Partikkel** | `Opcodes/partikkel.c` | `chuck-core/src/main/java/org/chuck/ugens/Partikkel.java` | Professional granular synthesis |
| **Scanned Synth** | `Opcodes/scansyn.c` | `chuck-core/src/main/java/org/chuck/ugens/ScannedSynth.java` | Physical modeling synthesis |
| **Moog Ladder** | `Opcodes/newfils.c` | `chuck-core/src/main/java/org/chuck/ugens/MoogLadder.java` | VA Synth engine in Deluge |
| **Gendy** | `Opcodes/gendy.c` | `chuck-core/src/main/java/org/chuck/ugens/Gendy.java` | Algorithmic/Noise synthesis |

---

## 4. Analysis: Gaps and Findings

This section documents findings from an audit of the Csound source tree (149 opcode files at `../csound/Opcodes/`), existing ChucK-Java infrastructure, and the current plan.

### 4.1 Missing High-Value Opcodes

The current plan lists 6 targets. Many high-value opcodes are omitted:

| Opcode | Csound File | Lines | Why Port | Complexity |
|--------|-------------|-------|----------|------------|
| **babo** | `babo.c` | 935 | Banded waveguide physical model — gourd/guitar body resonance. Reuses FDN delay lines like reverbsc but as a resonant body. | Medium-High |
| **modal4** | `modal4.c` | 534 | Modal synthesis (4 modes) — struck metal, glass, wooden objects. Practical and well-understood model. | Medium |
| **crossfm** | `crossfm.c` | 507 | Cross-modulated FM — two oscillators FM each other via configurable matrix. Leverages our Dx7Engine infrastructure. | Low (Dx7 reuse) |
| **fm4op** | `fm4op.c` | 1151 | Yamaha DX-style 4-operator FM. Not yet available. Smaller than Dx7Engine but complementary for "lite" FM. | Medium |
| **wave-terrain** | `wave-terrain.c` | 305 | Wave terrain synthesis — orbit paths over 2D terrain. Unique sound, small code footprint. | Low-Medium |
| **sndwarp** | `sndwarp.c` | 390 | Sound warping — pitch-shifting + time-stretching via granular crossfade. Practical utility. | Low |
| **freeverb** | `freeverb.c` | 284 | Classic Schroeder/Moorer reverb (8 comb + 4 allpass). Simpler alternative to ReverbSC, good for "spring" or "plate" style. | Low |
| **exciter** | `exciter.c` | ~200 | Harmonic exciter / aural exciter — adds harmonics to dull signals. | Low |

**Recommendation:** Add `crossfm`, `fm4op`, `modal4`, `freeverb`, and `exciter` to the plan. `babo` and `wave-terrain` are research-grade — defer to a later phase.

### 4.2 Existing Infrastructure Already Available

The plan assumes we need new infrastructure but some already exists in `chuck-core`:

- **FFT/IFFT**: Already at `chuck-core/src/main/java/org/chuck/audio/analysis/FFT.java` and `IFFT.java`. These are ChuckUGen-compatible UGens with `upchuck()` triggering transforms on accumulated ring buffers. PVS porting should build on these rather than re-implementing FFT.
- **UAna chain**: `UAna.java` at `chuck-core/src/main/java/org/chuck/audio/UAna.java` provides the `upchuck()` → `UAnaBlob` pattern that maps to Csound's PVS bus. No new `spectral/` package needed — existing `audio/analysis/` is the right home.
- **Analysis features**: 19 analysis UGens already exist in `audio/analysis/` (AutoCorr, Centroid, Chroma, DCT, MFCC, RMS, Rolloff, SFM, ZCR, etc.) — these can serve as spectral feature extraction without PVS.
- **No `ugens/` directory yet**: The plan references `chuck-core/src/main/java/org/chuck/ugens/` but this directory does not exist. Must be created. Effects UGens (ReverbSC, MoogLadder) should live there.

**Recommendation:** 
- PVS UGens should go in `audio/analysis/` (alongside existing FFT/IFFT), NOT a new `spectral/` package.
- Create `audio/ugen/` (not `ugens/`) for effects UGens.
- Phase 1 should prioritize wiring the existing FFT/IFFT into a `PVSAnal`/`PVSynth` chain before attempting new spectral ops.

### 4.3 Algorithm-Specific Technical Details Missing

The plan lacks algorithmic specifics needed for accurate porting:

**ReverbSC** (`reverbsc.c`, 414 lines):
- 8 modulated delay lines (FDN) with specific base lengths at 44.1kHz: **2473, 2767, 3217, 3557, 3907, 4127, 2143, 1933 samples**
- Each delay line has: random variation (0.6-1.7ms), modulation frequency (0.891-3.973 Hz), seed
- Feedback matrix based on **scattering junction** of 8 lossless waveguides (Julius O. Smith III)
- Lowpass filter per delay line: `filterState += dampFact * (in - filterState)` where `dampFact` is derived from `kLPFreq`
- Output gain: 0.35, JP scale: 0.25
- `DELAYPOS_SHIFT = 28`, `DELAYPOS_SCALE = 0x10000000` — fractional read position as 28.4 fixed-point
- Port path: direct structure import to Java. All math is in `double`. No JNI needed.

**MoogLadder / newfils** (`newfils.c`, 3769 lines — much larger than expected):
- Not just a Moog ladder filter — contains multiple filter types (SVF, Korg35, etc.)
- The "newfils" name means this is the entire Csound filter suite, not a single opcode
- If we only want a Moog ladder 4-pole, we should extract the specific section, not port the whole file

**Recommendation:** Break Moog ladder into its own small file for porting. The 3769-line newfils.c is a monolith; extract just `MoogVCF` section (~500 lines).

### 4.4 PVS Infrastructure Gap

PVS porting requires more than just FFT/IFFT. The PVS data types are:

- **PVS buffer**: streaming spectral frames (magnitude + frequency per bin, or complex pairs). This maps to `UAnaBlob.fvals`.
- **PVS bus**: signal routing between PVS analysis → processing → synthesis UGens. Maps to ChucK's existing UGen chain but needs a typed spectral blob.
- **PVS ops to target** (13 files in Opcodes/):
  - `pvs_ops.c`/`.h` — core analysis/synthesis + 12 processing ops (blur, scale, shift, etc.)
  - `pvsband.c` — band-limited processing
  - `pvsbasic.c`/`.h` — basic PVS operations  
  - `pvsbuffer.c` — spectral buffer/freeze
  - `pvscent.c` — spectral centroid
  - `pvsdemix.c`/`.h` — source separation
  - `pvsgendy.c` — stochastic spectrum generation
  - `pvsops.cpp` — additional processing ops

**Recommendation:** Phase 2 should focus on `pvs_ops.c` core (analysis/synthesis + blur/scale/shift) which gives the most value. Defer pvsdemix, pvsgendy to later.

### 4.5 Partikkel Complexity

The plan lists "Partikkel" but understates its complexity:
- `partikkel.c` is 1030 lines, but its parameter count is ~30 (not 40+)
- Grain pool architecture: each grain is a sine oscillator with independent envelope, pan, pitch
- Core challenge: grain allocation/deallocation in real-time (circular buffer of active grains, pre-allocated pool)
- This maps well to Java: pre-allocated array of grain objects, virtual threads NOT appropriate (grain rendering is microsecond-scale, thread overhead dominates)

**Recommendation:** Remove Virtual Threads from Partikkel design. Use a fixed pool of grain objects with an active-count cursor, like a ring buffer. Virtual threads are wrong for sample-level granularity.

### 4.6 Testing Strategy Issues

The plan proposes a 3-tier testing strategy with `.csd` oracle files and native csound binary:

**Problems:**
1. **Native csound binary not guaranteed**: The test suite assumes `csound` is on PATH. We should not depend on external native binaries for CI.
2. **Alternative approach**: Generate reference WAV files once, store them in test resources (`src/test/resources/reference/`), and compare against Java output. This is the approach already used by `SynthFmAccuracyTest` and `KitAccuracyTest` (commit messages reference these working with WAV comparison).
3. **`.csd` files as documentation only**: Keep `.csd` files for documentation, but use static WAV orrycles for automated testing.

**Updated test strategy:**
- Tier 1 (unit): Java-side algorithmic correctness tests (e.g., `Dx7EngineUnitTest`)
- Tier 2 (accuracy): Compare Java output to pre-computed reference WAVs (RMS error < -90dB)
- Tier 3 (E2E): `DelugeE2ETest` song playback tests already exist — extend to cover new opcodes

**Recommendation:** Remove `.csd` oracle dependency from the plan. Reference the existing working pattern from `SynthFmAccuracyTest` and `KitAccuracyTest`.

### 4.7 Build / JDK Version Gap

The plan assumes JDK 25 with incubator modules (Vector API, FFM) but doesn't address:

- **Module system**: `jdk.incubator.vector` requires `--add-modules jdk.incubator.vector` in Maven compiler config
- **FFM**: `java.lang.foreign` is in preview/preliminary in many JDK 25 builds
- **Fallback path**: What happens when these modules aren't available? Need a pure-Java fallback.
- **Maven profile**: Create a `-Pvector` / `-Pffm` profile, not unconditional dependency

**Recommendation:** Add a Maven profile section to the plan. Pure-Java fallback for each feature should be the default, with JDK 25 optimizations enabled by opt-in profile.

### 4.8 Csound OPDS / perf / init Dispatch Pattern

The plan doesn't address Csound's runtime architecture:

- Csound opcodes implement `OPDS` header with `init()` and `perf()` function pointers
- `init()` runs once at note-on, `perf()` runs every ksmps block
- Our UGen architecture has no equivalent to `init()` — constructor acts as init, `compute()` acts as perf
- Some opcodes (especially partikkel, pvs) allocate internal state in `init()` that must transition cleanly to Java constructor

**Recommendation:** For each ported opcode, document which Csound `init()` logic goes in the Java constructor and which state is per-voice vs per-UGen.

### 4.9 REFERENCE_GUIDE.md Gaps

The reference guide currently has 3 incomplete stubs (ReverbSC, Gendy, PVSAnal) with minimal parameter documentation. It needs:

- Full parameter tables with ranges, defaults, and units for all target opcodes
- Technical notes section filled in (not just "Uses Zero-Delay Feedback" placeholder)
- Usage examples that work with actual ChucK syntax (not hypothetical)
- New entries for every opcode added to the porting plan

**Recommendation:** Expand REFERENCE_GUIDE.md as a living document that grows with each ported opcode. Each opcode should be documented WITH the port, not after.

### 4.10 Cross-Opcode Dependencies

The plan lists opcodes in isolation but doesn't model their dependencies:

| Opcode | Depends On | Notes |
|--------|-----------|-------|
| ReverbSC | Nothing (standalone FDN) | Can be first — no dependencies |
| MoogLadder | Biquad (already in analysis/) | Reuse `Butterworth` or `Biquad` filter code |
| PVS ops | FFT/IFFT (already exist) | Build on existing infrastructure |
| Partikkel | Nothing (self-contained) | Independent — just a sine grain pool |
| ScannedSynth | Mass-spring ODE solver | Needs ODE solver port from scansyn.c (Euler or RK4) |
| Gendy | Exponential random distribution, kahan summation | Self-contained aside from math |
| crossfm | Dx7Engine (already exists) | Direct reuse of Dx7 operator model |
| fm4op | Nothing (self-contained) | Simpler than Dx7Engine, different parameter set |
| modal4 | Nothing (2nd-order resonant filters) | Self-contained |

**Recommendation:** Port in dependency order. Start with standalone opcodes (ReverbSC, MoogLadder, Gendy), then PVS (builds on FFT), then ScannedSynth (has ODE dependency).

---

## 5. Updated Porting Roadmap

### Phase 1: Standalone Effects & Synths (no dependencies)
1. **ReverbSC** — 8-delay FDN reverb, standalone
2. **MoogLadder** — extract Moog section from newfils.c (~500 lines); reuse existing Biquad if needed
3. **freeverb** — simpler reverb option, 284 lines, standalone
4. **exciter** — harmonic exciter, ~200 lines

### Phase 2: PVS Spectral Foundation (builds on existing FFT/IFFT)
5. **PVSAnal/PVSynth** — wire existing FFT.java/IFFT.java into UAna chain (core PVS bus)
6. **pvsblur, pvscale, pvshift** — simplest spectral processing ops from pvs_ops.c
7. **PVS freeze** — spectral freezing (pvsbuffer.c style)

### Phase 3: Complex Synths & FM Family
8. **crossfm** — reuse Dx7Engine operator infrastructure
9. **fm4op** — 4-op FM, complementary to Dx7, self-contained
10. **Gendy** — Xenakis stochastic synthesis, standalone
11. **ScannedSynth** — ODE mass-spring mesh, requires ODE solver port

### Phase 4: Granular & Advanced
12. **Partikkel** — grain pool, needs careful allocation design
13. **modal4** — modal synthesis
14. **sndwarp** — sound warping, time-stretch/pitch-shift

### Phase 5: Research-Grade
15. **babo** — banded waveguide (gourd body model, deferred)
16. **wave-terrain** — wave terrain synthesis (deferred)

---

## 6. Integration with Maven Lifecycle

| Goal | Maven Command | Purpose |
| :--- | :--- | :--- |
| **Unit Tests** | `mvn test` | Rapid validation of logic and mathematical transforms. |
| **Integration** | `mvn verify` | Reference WAV comparison against pre-computed oracles (not native csound). |
| **Vector API** | `mvn test -Pvector` | Enable JDK 25 Vector API tests (opt-in, not default). |
| **FFM Tests** | `mvn test -Pffm` | Enable Foreign Memory API tests (opt-in, not default). |
| **Formatting** | `mvn spotless:apply` | Enforce code style consistency before porting. |

### Maven Profile Structure (to create)
```xml
<profiles>
  <profile>
    <id>vector</id>
    <properties>
      <jdk.incubator.vector>--add-modules jdk.incubator.vector</jdk.incubator.vector>
    </properties>
  </profile>
</profiles>
```

Fallback classes (e.g. `SinLookup` with pure-Java vs `SinLookupVector`) can coexist under a build-time flag.

---

## 7. User Experience (UX) & UI Integration

Ported features should not only sound good but be easy to control and visualize.

### ChucK IDE Enhancements (`chuck-ide`)
*   **Spectral Monitor Panel:** A new panel in the IDE to visualize the "PVS Bus" content (magnitudes/phases) in real-time. This helps users understand what `pvsblur` or `pvscale` is actually doing to the spectrum.
*   **UGen Browser Integration:** Add a "Csound Heritage" category to the `UGenBrowser` with auto-generated documentation for the ported opcodes.
*   **Live Parameter Control:** Integration with `ControlSurface.java` to allow mapping external MIDI controllers to `ReverbSC` or `Partikkel` parameters with zero configuration.

### Deluge Java Emulator Enhancements (`deluge`)
*   **Advanced FX Tab:** In `SwingMasterFxPanel.java`, add a toggle to switch between "Classic Deluge Reverb" and "Csound ReverbSC".
*   **Synth Engine Visualizer:** Enhance `SwingVisualizerPanel.java` to show the excitation state of a `ScannedSynth` oscillator.
*   **New Modal Dialogs:** Create a `PartikkelConfigDialog.java` to handle the 30+ parameters of the `partikkel` opcode, which are too many for a standard sidebar.

---

## 8. Extensive Testing Strategy (Maven Native)

To ensure the Java port is faithful to the C original and stable under load, we use a pure Java testing architecture integrated directly into the Maven lifecycle. **No native csound binary is required.**

### Tier 1: Unit Correctness (`mvn test`)
*   Pure Java algorithmic tests for each ported UGen (e.g., `ReverbSCTest.java` tests delay line math in isolation).
*   Boundary tests: zero input, max feedback, DC input, sample rate changes.

### Tier 2: Reference WAV Accuracy (`mvn verify`)
*   **Static Reference WAVs:** Pre-computed WAV files stored in `src/test/resources/reference/`.
*   **Java Comparison Engine:** Each test runs the UGen through a headless `ChuckVM` instance, captures output, and compares sample-by-sample against the reference.
*   **Automated Metrics:**
    *   *RMS Error:* Measured in Java using `double[]` arrays.
    *   *Tolerance:* Error must be < -90dB for deterministic filters.
    *   *Pattern:* Already established by `SynthFmAccuracyTest` and `KitAccuracyTest`.

### Tier 3: Performance & Vectorization (`mvn test -Pbenchmark`)
*   **JMH Suite:** Using the Maven JMH plugin to track the efficiency of `jdk.incubator.vector` code paths.
*   **Stress Testing:** A "Shred Bomb" test where 1000+ instances of `Partikkel` are spawned, ensuring the scheduler handles high-concurrency audio without jitter.

### Tier 4: Memory & Lifecycle (`mvn test -Paudit`)
*   **FFM Leak Detection:** Utilizing a custom `MemoryArenaTracker` to ensure all `Arena` allocations from FFM are correctly closed.
*   **GC Pressure Analysis:** Integrated with Maven to fail the build if GC pauses exceed 1ms during a 30-second stress test (using JFR).
