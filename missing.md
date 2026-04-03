# ChucK-Java: Missing Features & Gaps

This document tracks the features from the original [Stanford ChucK](https://chuck.stanford.edu/) implementation that are currently missing, incomplete, or divergent in this JDK 25 Java port.

## 1. Core Language & VM Architecture

| Feature | Status | Description |
|:---|:---|:---|
| **Shred References** | Done | `spork ~` now returns a `Shred` object reference. Supports `.id()`, `.exit()`, and `.done()`. |
| **Event Conjunction** | Done | `e1 && e2 => now` is implemented. Shred waits until all events trigger since the wait began. |
| **Event Disjunction** | Done | `e1 || e2 => now` is implemented. Shred waits until any event triggers. |
| **Chugins System** | Done | Dynamic plugin architecture implemented via `ChuginLoader`. Loads `.jar` files from the `chugins/` directory. |
| **Machine Control** | Done | `Machine.add()`, `Machine.remove()`, `Machine.replace()`, `Machine.status()`, `Machine.eval()`, and `Machine.clear()` are fully implemented. |
| **me.dir(n)** | Done | Multi-level directory access implemented in `ChuckShred` and `ChuckEmitter`. |
| **Duration Arithmetic** | Done | Fixed `dur * float`, `dur / float`, and `dur / dur` operations to work correctly with sub-sample precision (double). |
| **Vectorized Audio** | Done | SIMD-accelerated mixing and oscillators (Sin, Saw, Tri, Pulse, Sqr) using Java Vector API. |
| **Off-heap Audio** | Done | Off-heap DAC buffers via Project Panama (FFM) to eliminate GC jitter. |
| **Java Fluent DSL** | Done | Fluent `.chuck(target)` API for UGen chaining in Java. |
| **Scoped Value Time** | Done | Shred-local logical time context via Scoped Values (JEP 481). |
| **Java Machine** | Done | Hot-reloading runner for dynamic Java sporking in IDE and CLI. |
| **Shred Interface** | Done | Standard interface for Java-based shreds. |

## 2. Unit Generators (UGens)

| UGen | Status | Description |
|:---|:---|:---|
| **Dyno** | Done | Fully implemented with Envelope Follower and Gain Computer supporting Compressor, Limiter, Expander, and Gate modes. |
| **Chugraph** | Done | Implemented as a base class with `inlet` and `outlet` support. |
| **ChuGen** | Done | Implemented with synchronous `tick()` logic execution within the audio thread. |
| **WvIn / WaveLoop** | Done | Fully implemented with support for loading and looping WAV/AIFF files. |
| **WvOut** | Done | Implemented as `WvOut` UGen for recording audio to WAV files from within ChucK. |
| **NRev / PRCRev** | Done | Classic STK reverbs implemented. |
| **GVerb** | Done | High-quality studio reverb implemented using a multi-delay network. |
| **PitShift** | Done | Functional time-domain pitch shifter implemented using dual cross-fading delay lines. |
| **Gen9 / Gen17** | Done | Implemented sum-of-sinusoids (`Gen9`) and Chebyshev polynomials (`Gen17`) for table synthesis and wavefolding. |
| **Mix2** | Done | 2-channel stereo mixer implemented. |
| **HalfRect / FullRect** | Done | Signal rectification UGens implemented. |
| **ZeroX** | Done | Zero-crossing detector implemented. |
| **SubNoise** | Done | Sub-sampled noise generator implemented. |
| **Modulate** | Done | Vibrato/Tremolo modulation UGen implemented. |
| **WarpTable / CurveTable** | Done | Advanced lookup table UGens implemented. |
| **Pan4 / Pan8 / Pan16** | Done | Multi-channel panners implemented (4, 8, 16 channels). |
| **Mix4 / Mix8 / Mix16** | Done | Multi-channel mixers implemented (4, 8, 16 channels). |
| **MultiChannelUGen** | Done | Robust base class for N-channel UGens implemented. |

## 3. I/O & Networking

| Feature | Status | Description |
|:---|:---|:---|
| **FileIO** | Done | Fully implemented ASCII and Binary modes, including support for the `<=` streaming operator. |
| **MidiOut** | Done | Implemented with support for sending MIDI messages using `javax.sound.midi`. |
| **MidiIn** | Done | Implemented with `RtMidi` binding via FFM API. Full support for `recv(MidiMsg)` and `Event` waiting (`min => now`). |
| **SerialIO** | Done | Functional stub implemented for debugging. |
| **OscBundle** | Done | Implemented support for grouping and sending OSC messages. |

## 4. Standard Libraries

| Library | Status | Description |
|:---|:---|:---|
| **Std Methods** | Done | Implemented `atoi`, `atof`, `rand2`, `rand2f`, `fabs`, `srand`, `systemTime`, `getenv`, `setenv`, and `range`. |
| **RegEx** | Done | Pattern matching using `java.util.regex`. |
| **Reflect** | Done | Shred and object introspection implemented. |
| **UAna (Flux/Rolloff)** | Done | Spectral feature extractors implemented. |

## 5. IDE & UI

| Feature | Status | Description |
|:---|:---|:---|
| **Visualizer Config** | Done | Added UI controls to adjust FFT size and oscilloscope window size. |
| **Project View** | Done | Added file system management (new file, delete, refresh) and project directory support. |

---

## 6. Gap Analysis vs C++ Reference (`../chuck/`)

### 6.1 Language / Parser / Emitter

| Feature | Status | Notes |
|:---|:---|:---|
| `switch`/`case` | ✅ Implemented | AST + ANTLR grammar + emitter (equality-chain) |
| `goto` / label | ❌ Missing | Not in AST or parser |
| Ternary `? :` | ✅ Implemented | AST + ANTLR grammar + emitter; precedence above `=>` |
| `complex` / `polar` types | ✅ Implemented | Field accessors `.re/.im/.mag/.phase`; built-in `+`,`-`,`*`,`/` with correct complex/polar math |
| `vec2` / `vec3` / `vec4` types | ✅ Implemented | Field accessors `.x/.y/.z/.w`; element-wise `+`,`-`; scalar `*`; dot product |
| Operator overloading (user classes) | ✅ Implemented | Arithmetic + comparison (`<`,`<=`,`>`,`>=`,`==`,`!=`) + unary + `++`/`--` |
| `public`/`private`/`protected` on class members | ❌ Missing | No access modifiers |
| `static` variables in classes | ⚠️ Partial | Not fully initialized |
| Doc comments | ❌ Missing | Not in AST or parser |
| **ANTLR4 Parser** | ✅ Done | Now the default and only parser (replacing the hand-written one). |


### 6.2 UGens — Missing

**Filters:** ~~`TwoPole`, `TwoZero`, `PoleZero`, `HPF`, `BPF`, `BRF`~~ ✅, `DelayA` (allpass-interp), `DelayP`

**Oscillators / Generators:** ~~`Blit`, `BlitSaw`, `BlitSquare`~~ ✅ (band-limited PolyBLEP), `CNoise` (colored noise)

**FM Instruments (STK):** ~~`BeeThree`, `FMVoices`, `HevyMetl`, `PercFlut`, `TubeBell`, `Wurley`~~ ✅, `HnkyTonk`, `FrencHrn`, `KrstlChr`, `BlowBotl`, `BlowHole`, `BandedWG`

**Multichannel:** ~~`LiSa2`, `WvOut2`, `SndBuf2`, `PanN`, `MixN`~~ ✅, `LiSa4/6/8/10/16`, `WvOut4/8/16`, `SndBuf4/8/16`, `Identity2`

**Spatial:** `Mesh2D` (2D mesh physical model)

### 6.3 Unit Analyzers (UAna) — Missing

Java has: `FFT`, `IFFT`, `RMS`, `Centroid`, `Flux`, `ZeroX`, `ZCR`, `MFCC`, `SFM`, `Kurtosis`, `Rolloff`

| UAna | Status |
|:---|:---|
| `MFCC` — Mel-Frequency Cepstral Coefficients | ✅ Implemented (mel filterbank + DCT-II, 13 coeffs) |
| `Kurtosis` | ✅ Implemented (4th central moment of spectrum) |
| `SFM` — Spectral Flatness Measure | ✅ Implemented (geometric/arithmetic mean ratio) |
| `ZCR` — Zero Crossing Rate | ✅ Implemented (frame-based, configurable window) |

### 6.4 Machine API — Missing Functions

| Function | Status |
|:---|:---|
| `Machine.crash()` | ✅ Implemented |
| `Machine.removeAll()` | ✅ Implemented |
| `Machine.resetID()` | ✅ Implemented |
| `Machine.clearVM()` | ✅ Implemented |
| `Machine.shreds()` / `numShreds()` | ✅ Implemented |
| `Machine.shredExists(id)` | ✅ Implemented |
| `Machine.setloglevel()` / `getloglevel()` | ✅ Implemented |
| `Machine.gc()` | ✅ Implemented |
| `Type` / `Function` introspection classes | ❌ Missing |

### 6.5 AI / ML Library — Completely Absent

From C++ `ulib_ai.cpp`:

| Feature | Status |
|:---|:---|
| `SVM` — Support Vector Machine (train, predict, save, load) | ❌ Missing |
| `KNN` / `KNN2` — K-Nearest Neighbor | ❌ Missing |
| `HMM` — Hidden Markov Model (train, viterbi, forward) | ❌ Missing |

### 6.6 Event System

| Feature | Status |
|:---|:---|
| `Event.can_wait()` | ❌ Missing |
| `OscEvent` — event triggered by incoming OSC | ❌ Missing |

### 6.7 Type System

| Feature | Status |
|:---|:---|
| `complex` as first-class type (`#(re, im)`) | ✅ Implemented — literals, field accessors, arithmetic |
| `polar` as first-class type (`%(mag, phase)`) | ✅ Implemented — literals, field accessors, arithmetic |
| `vec2`/`vec3`/`vec4` with `.x/.y/.z/.w` | ✅ Implemented — literals, field read/write, arithmetic |
| Arrays of complex / vec types | ✅ Implemented — full support for allocation, sorting, and `Math.rtop/ptor` conversions |

---

### Priority

**High** — common in real ChucK programs:
1. ~~`switch`/`case`~~ ✅ Done
2. ~~Ternary `? :`~~ ✅ Done
3. ~~`BPF`, `HPF`, `BRF`~~ ✅ Done
4. ~~`BlitSaw`, `BlitSquare`~~ ✅ Done
5. ~~Full `Machine` shred API (`shreds()`, `removeAll()`, etc.)~~ ✅ Done

**Medium** — used in analysis / spatial audio:
- ~~`vec2`/`vec3`/`vec4`~~ ✅ Done (field accessors + arithmetic)
- ~~`MFCC`~~ ✅ Done
- ~~FM instrument variants~~ ✅ Done (`Wurley`, `BeeThree`, `HevyMetl`, `PercFlut`, `TubeBell`, `FMVoices`)
- ~~`ZCR`~~ ✅ Done
- ~~Operator overloading~~ ✅ Done (arithmetic + comparison + unary)
- ~~`SFM`, `Kurtosis`~~ ✅ Done

**Lower** — niche or advanced:
- AI/ML library (`SVM`, `KNN`, `HMM`)
- `goto`/label
- `HnkyTonk`, `FrencHrn`, `KrstlChr` FM instruments
- `Machine.resetID()`, `Machine.gc()`
