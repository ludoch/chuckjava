# ChucK-Java: Gap Analysis vs C++ Reference (`../chuck/`)

Last reassessed: 2026-04-05. Based on direct comparison of `../chuck/src/core/` against `src/main/java/org/chuck/`.

Legend: ✅ Done · ⚠️ Partial · ❌ Missing

---

## 1. Unit Generators (UGens)

### 1.1 Present in Java (not an issue)

All of these are registered in `UGenRegistry.java` and have concrete implementations:

Oscillators: `SinOsc`, `SawOsc`, `TriOsc`, `SqrOsc`, `PulseOsc`, `Phasor`, `Blit`, `BlitSaw`, `BlitSquare`, `Noise`, `SubNoise`, `CNoise`, `Impulse`, `Step`

Filters: `LPF`/`Lpf`, `HPF`, `BPF`, `BRF`, `ResonZ`, `BiQuad`, `OnePole`, `OneZero`, `TwoPole`, `TwoZero`, `PoleZero`

Effects: `Echo`, `Delay`, `DelayL`, `DelayA`, `Chorus`, `JCRev`, `NRev`, `PRCRev`, `GVerb`, `PitShift`, `Dyno`, `Modulate`, `FullRect`, `HalfRect`, `ZeroX`

Multichannel: `Pan2/4/8/16/N`, `Mix2/4/8/16/N`, `Identity2`, `UGen_Multi`, `UGen_Stereo`

Spatial & 3D: `Spatial3D`, `AmbisonicEncoder`, `AmbisonicDecoder`

Network & I/O: `Broadcaster`, `Adc` (multi-channel)

Envelopes: `Envelope`, `Adsr`

Instruments (STK): `Clarinet`, `Mandolin`, `Plucked`, `Rhodey`, `Wurley`, `TubeBell`, `BeeThree`, `FMVoices`, `HevyMetl`, `PercFlut`, `Moog`, `Saxofony`, `Flute`, `Brass`, `Sitar`, `StifKarp`, `Shakers`, `Bowed`

Tables / GenX: `Gen5`, `Gen7`, `Gen9`, `Gen10`, `Gen17`, `GenX`, `WarpTable`, `CurveTable`

Buffers: `SndBuf`, `SndBuf2`, `WvIn`, `WvOut`/`WvOut2`, `WaveLoop`, `LiSa`, `LiSa2`, `LiSa4`, `LiSa6`, `LiSa8`, `LiSa10`, `LiSa16`

STK (additional): `VoicForm`, `ModalBar`, `BandedWG`, `BlowBotl`, `BlowHole`, `HnkyTonk`, `FrencHrn`, `KrstlChr`, `JetTabl`

Filter bases: `FilterBasic`, `FilterStk`, `BiQuadStk`

Utilities: `Gain`, `GainDB`, `Blackhole`, `Adc`

### 1.2 Missing UGens — registered in C++ but absent in Java

| UGen | C++ File | Description |
|------|----------|-------------|
| `VoicForm` | `ugen_stk` | ✅ Implemented — Formant voice synthesizer (4-formant singing voice) |
| `ModalBar` | `ugen_stk` | ✅ Implemented — Modal-resonance bar physical model |
| `BandedWG` | `ugen_stk` | ✅ Implemented — Banded waveguide (bowed bar, tibetan bowl) |
| `BlowBotl` | `ugen_stk` | ✅ Implemented — Blown bottle physical model |
| `BlowHole` | `ugen_stk` | ✅ Implemented — Clarinet with tonehole and register vent |
| `HnkyTonk` | `ugen_stk` | ✅ Implemented — FM honky-tonk piano |
| `FrencHrn` | `ugen_stk` | ✅ Implemented — FM French horn |
| `KrstlChr` | `ugen_stk` | ✅ Implemented — FM crystal choir |
| `JetTabl` | `ugen_stk` | ✅ Implemented — jet saturation curve (used by Flute internally) |
| `Mesh2D` | `ugen_stk` | ✅ Implemented — 2D waveguide mesh physical model |
| `DelayP` | `ugen_xxx` | ✅ Implemented — pitch-shifting delay, `delay`/`shift(semitones)` |
| `FilterBasic` | `ugen_xxx` | ✅ Implemented — abstract base with `freq`, `Q`, `set` API |
| `FilterStk` | `ugen_stk` | ✅ Implemented — STK-compatible filter base with `gain`, `clear` |
| `BiQuadStk` | `ugen_stk` | ✅ Implemented — STK BiQuad with `b0/b1/b2/a1/a2` coefficient API |
| `Teabox` | `ugen_stk` | ✅ Implemented (stub) — Hardware sensor interface |
| `LiSa6`, `LiSa10` | `ugen_xxx` | ✅ Implemented — registered via `LiSaN(6/10, sr)` |

---

## 2. Unit Analyzers (UAna)

### 2.1 Present in Java

`FFT`, `IFFT`, `RMS`, `Centroid`, `Flux`, `ZeroX`, `ZCR`, `MFCC`, `SFM`, `Kurtosis`, `Rolloff`, `DCT`, `IDCT`, `AutoCorr`, `XCorr`, `Chroma`, `FeatureCollector`, `Flip`, `UnFlip`

### 2.2 Missing UAnas — in C++ but absent in Java

| UAna | Description |
|------|-------------|
| `DCT` | ✅ Implemented — Discrete Cosine Transform (forward) |
| `IDCT` | ✅ Implemented — Inverse DCT |
| `AutoCorr` | ✅ Implemented — Autocorrelation of input buffer |
| `XCorr` | ✅ Implemented — Cross-correlation between two UAna streams |
| `Chroma` | ✅ Implemented — 12-bin pitch-class profile |
| `FeatureCollector` | ✅ Implemented — Aggregates outputs from multiple UAnas |
| `Flip` | ✅ Implemented — Copies audio buffer into UAna blob |
| `UnFlip` | ✅ Implemented — Copies UAna blob back to audio buffer |

`FeatureCollector` is particularly important because it bridges the UAna graph to the AI/ML classes (`KNN`, `MLP`, etc.).

---

## 3. AI / ML Library (`ulib_ai.cpp`)

All six classes implemented in `org.chuck.core.ai`:

| Class | Description | Status |
|-------|-------------|--------|
| `KNN` | K-Nearest Neighbor (regression) | ✅ Implemented — `train`, `predict`, `k` |
| `KNN2` | K-Nearest Neighbor (classification) | ✅ Implemented — `train`, `predict`, `search`, `k` |
| `SVM` | Support Vector Machine (linear, one-vs-rest) | ✅ Implemented — `train`, `predict`, `save`, `load` |
| `HMM` | Hidden Markov Model (Baum-Welch EM) | ✅ Implemented — `train`, `generate`, `logLikelihood` |
| `MLP` | Multi-Layer Perceptron (backprop SGD) | ✅ Implemented — `input/hidden/output`, `train`, `predict`, `activation` |
| `PCA` | Principal Component Analysis (power iteration) | ✅ Implemented — `train`, `transform`, `explainedVariance`, `numComponents` |

The typical ChucK AI workflow is: `Flip → FeatureCollector → KNN/MLP` for real-time machine learning on audio.

---

## 4. Language / Grammar

### 4.1 Present in Java grammar (`ChuckANTLR.g4`)

`if/else`, `while`, `until`, `for`, `repeat`, `do-while`, `do-until`, `switch/case/default`, `break`, `continue`, `return`, `null`, `class extends`, `public/private/protected/static/global/const`, `@` references, `complex`/`polar`/`vec` literals, operator overloading, ternary `?:`, `spork`, `new`, `#include`/`@import`.

### 4.2 Missing language features — in C++ grammar but not in Java

| Feature | C++ grammar | Java status | Notes |
|---------|-------------|-------------|-------|
| `typeof` | `TYPEOF unary_expression` | ✅ Implemented | Returns the type name as a string |
| `instanceof` | Used in type checker | ✅ Implemented | Runtime type test (`x instanceof Foo`) |
| `abstract` class / method | `ABSTRACT` keyword | ✅ Implemented — `abstract class Foo { }` prevents instantiation |
| `interface` | `INTERFACE id_list LBRACE` | ✅ Implemented — parsed; prevents direct instantiation |
| `@construct` | Explicit constructor syntax | ✅ Implemented — `fun @construct(...)` parsed via `REFERENCE_TAG ID` in `functionName` |
| `@destruct` | Explicit destructor syntax | ✅ Implemented — called on shred cleanup for registered UserObjects |
| `loop` statement | `LOOP stmt` | ✅ Implemented — infinite loop, only `break` exits |
| `@operator` overload syntax | `fun @operator+(...)` | ✅ Implemented — `@operator+` maps to `__op__+` in visitor |
| `static` class variables | `STATIC` in class body | ✅ Implemented | Shared between instances and shreds; initialization persists |
| `public` class-level access | `PUBLIC` in class body | ✅ Implemented | Runtime enforcement added |
| `doc` comments (`/** */`) | `@doc` annotation | ✅ Implemented | Extracted and introspectable via Reflect.doc* |

---

## 5. Math Library

### 5.1 Present in Java (`MathInstrs.java` + emitter dispatch)

`sin`, `cos`, `tan`, `asin`, `acos`, `atan`, `atan2`, `sqrt`, `pow`, `exp`, `log`, `log2`, `log10`, `floor`, `ceil`, `round`, `trunc`, `abs`, `fabs`, `isinf`, `isnan`, `equal`, `euclidean`, `rtop`, `ptor`, `srandom`/`srand`, constants: `PI`, `TWO_PI`, `e`, `sqrt2`, `j`, `INFINITY`

### 5.2 Missing Math functions — in C++ but not wired in Java

All functions below are now implemented in `MathInstrs.MathFunc` (2026-04-06).

| Function | Description | Status |
|----------|-------------|--------|
| `Math.sinh`, `cosh`, `tanh` | Hyperbolic trig | ✅ Done |
| `Math.hypot(x,y)` | Hypotenuse (numerically stable `sqrt(x²+y²)`) | ✅ Done |
| `Math.fmod(x,y)` | Floating-point modulo | ✅ Done |
| `Math.remainder(x,y)` | IEEE remainder | ✅ Done |
| `Math.min(a,b)`, `max(a,b)` | Scalar min/max | ✅ Done |
| `Math.nextpow2(n)` | Next power of 2 ≥ n | ✅ Done |
| `Math.ensurePow2(n)` | Alias for nextpow2 | ✅ Done |
| `Math.exp2(x)` | 2^x | ✅ Done |
| `Math.random2f(a,b)` | Random float in [a,b] | ✅ Done |
| `Math.random2(lo,hi)` / `random2i` | Random int in [lo,hi] | ✅ Done |
| `Math.randomf()` | Random float in [0,1) | ✅ Done |
| `Math.gauss(mean, std)` | Gaussian random sample | ✅ Done |
| `Math.map(x, srcMin, srcMax, dstMin, dstMax)` | Same as `Std.scalef`; C++ exposes on Math | ✅ Done |
| `Math.map2(x, srcMin, srcMax, dstMin, dstMax, type)` | Mapped with curve type (0=linear, 1=cosine, 2=smoothstep) | ✅ Done |
| `Math.remap` | Alias for map2 | ✅ Done |
| `Math.clampi(x, lo, hi)` | Integer clamp | ✅ Done |
| `Math.clampf(x, lo, hi)` | Float clamp | ✅ Done |
| `Math.cossim(a[], b[])` | Cosine similarity of two float arrays | ✅ Done |
| `Math.ssin`, `scos`, `stan`, `ssinh`, `scosh`, `stanh`, `sexp`, `sinsqrt` | Fast scalar approx (delegate to std Math) | ✅ Done |

---

## 6. Std Library

### 6.1 Missing Std functions

| Function | Description | Status |
|----------|-------------|--------|
| `Std.rand()` | Random integer in [0, RAND_MAX] | ✅ Done |
| `Std.randf()` | Random float in [0, 1) | ✅ Done |
| `Std.system(cmd)` | Execute a shell command via `ProcessBuilder` | ✅ Done |

---

## 7. Machine API

### 7.1 Present in Java

`Machine.add()`, `replace()`, `remove()`, `removeAllShreds()`, `clearVM()`, `resetShredID()`, `numShreds()`, `shreds()`, `shredExists(id)`, `crash()`, `gc()`, `version()`, `platform()`, `loglevel()`, `setloglevel()`, `timeofday()`

### 7.2 Missing Machine functions

| Function | Description |
|----------|-------------|
| `Machine.eval(string)` | ✅ Implemented |
| `Machine.eval(string, args[])` | ✅ Implemented — `evalWithArgs` case |
| `Machine.removeLastShred()` | ✅ Implemented |
| `Machine.spork(string)` | ✅ Implemented |
| `Machine.intsize()` | ✅ Implemented |
| `Machine.silent()` | ✅ Implemented (returns 1) |
| `Machine.realtime()` | ✅ Implemented (returns 0) |
| `Machine.refcount(obj)` | ✅ Stub — returns 0 |
| `Machine.sp_reg`, `sp_mem` | ✅ Stubs — return 0 |
| `Machine.printStatus()` | ✅ Implemented |
| `Machine.printTimeCheck()` | ✅ Implemented (stub) |
| `Machine.operatorsPush()` / `operatorsPop()` / `operatorsStackLevel()` | ✅ Stubs |
| `Machine.os()` | ✅ Implemented (alias for platform()) |

`Machine.eval()` is the most impactful missing item — it enables live coding, on-the-fly code injection, and the ChucK REPL.

---

## 8. Stdlib Classes (non-I/O)

| Class | Status | Description |
|-------|--------|-------------|
| `ConsoleInput` | ✅ Done | `readline()`, `prompt(string)`, `ready()`, `can_wait()` |
| `KBHit` | ✅ Done | `kbhit()` / `hit()`, `getchar()`, `can_wait()` — background virtual thread queues keypresses |
| `StringTokenizer` | ✅ Done | `set(s)`, `more()`, `next()`, `reset()` implemented |
| `RegEx` | ✅ Done | Pattern matching via `java.util.regex` |
| `Type` introspection | ✅ Implemented — `ChuckTypeObj`: `name()`, `parent()`, `isa(string)`, `eq(Type)` |
| `Function` introspection | ✅ Implemented — `ChuckFunction`: `name()`, `numArgs()`, `returnType()` |

---

## 9. I/O (all confirmed present in Java)

| Class | Status |
|-------|--------|
| `FileIO` (ASCII + binary) | ✅ Done |
| `MidiIn` / `MidiOut` / `MidiMsg` | ✅ Done | Enhanced with native RtMidi bindings (Callback-driven input, Virtual ports, Native output) |
| `OscIn` / `OscOut` / `OscMsg` / `OscBundle` | ✅ Done | |
| `Hid` / `HidMsg` | ✅ Done | |
| `SerialIO` | ✅ Done | |
| `MidiFileIn` | ✅ Done — `open(string)`, `read(MidiMsg)`, `more()`, `rewind()`, `close()`, `size()`, `numTracks()`, `resolution()` | |
| `HidOut` | ✅ Done (stub) — `open(num)`, `send(HidMsg)`, `close()`, `name()`, `num()` — always returns 0 (native HID output requires platform libs) | |

---

## 10. Event System

| Feature | Status | Notes |
|---------|--------|-------|
| `Event` base | ✅ Done | `signal()`, `broadcast()`, `wait()`, `can_wait()` |
| `OscEvent` | ✅ Implemented — subclass of OscIn, same API |
| Conjunction `e1 && e2 => now` | ✅ Done | |
| Disjunction `e1 \|\| e2 => now` | ✅ Done | |
| Event timeout | ✅ Done | `e.timeout(dur) => now;` |

---

## 11. Type System

| Feature | Status |
|---------|--------|
| `complex` / `polar` first-class types | ✅ Done |
| `vec2` / `vec3` / `vec4` | ✅ Done |
| `null` literal | ✅ Done |
| `auto` type inference | ✅ Done — inferred from RHS expression at compile time |
| `typeof` operator | ✅ Implemented |
| `instanceof` operator | ✅ Implemented |
| `abstract` classes | ✅ Implemented |
| `interface` definitions | ✅ Implemented |
| `@construct` | ✅ Implemented |
| `@destruct` | ✅ Implemented |
| `static` member variables (per-class, not per-instance) | ✅ Done — shared between instances and shreds; initialization persists correctly |

---

## Priority Summary

### High — blocks real ChucK programs

| Item | Why it matters |
|------|---------------|
| ~~`Machine.eval(string, args[])`~~ | ✅ Implemented |
| ~~`typeof` / `instanceof`~~ | ✅ Implemented |
| ~~`AutoCorr`, `XCorr` UAnas~~ | ✅ Implemented |
| ~~`FeatureCollector` + AI/ML~~ | ✅ Implemented — KNN, KNN2, SVM, MLP, HMM, PCA |
| ~~`Chroma` UAna~~ | ✅ Implemented |
| `ConsoleInput` / `KBHit` | ✅ Done — interactive terminal programs now supported |
| Missing Math: `min`, `max`, `tanh`, `sinh`, `cosh` | ✅ Done — all Math section 5.2 functions implemented |
| ~~`DCT` / `IDCT`~~ | ✅ Implemented |

### Medium — affects completeness but workarounds exist

| Item | Why it matters |
|------|---------------|
| ~~`VoicForm`~~ | ✅ Implemented |
| ~~`ModalBar`~~ | ✅ Implemented |
| ~~`BandedWG`, `BlowBotl`, `BlowHole`~~ | ✅ Implemented |
| ~~`HnkyTonk`, `FrencHrn`, `KrstlChr`~~ | ✅ Implemented |
| ~~`Flip` / `UnFlip` UAnas~~ | ✅ Implemented |
| ~~`MidiFileIn`~~ | ✅ Implemented |
| ~~`abstract` / `interface`~~ | ✅ Implemented |
| ~~`@construct`~~ | ✅ Implemented |
| ~~`@destruct`~~ | ✅ Implemented — called on shred cleanup |
| ~~Missing Math: `gauss`, `nextpow2`, `map`, `fmod`~~ | ✅ All implemented |

### Lower — niche or debug use

| Item | |
|------|-|
| ~~`Mesh2D`~~ | ✅ Implemented — 2D waveguide mesh physical model |
| ~~`DelayP`~~ | ✅ Implemented |
| ~~`LiSa6`, `LiSa10`~~ | ✅ Implemented |
| ~~`JetTabl`, `FilterBasic`, `FilterStk`, `BiQuadStk`~~ | ✅ Implemented |
| ~~`Teabox`~~ | ✅ Implemented (stub) — Hardware sensor interface |
| ~~`Machine.refcount`, `sp_reg`, `sp_mem`~~ | ✅ Stubs returning 0 |
| ~~`Machine.operatorsPush/Pop`~~ | ✅ Stubs |
| ~~`HidOut`~~ | ✅ Implemented (stub) |
| ~~Std.system()~~ | ✅ Implemented |
| ~~Fast math approximations (`ssin`, `scos`, etc.)~~ | ✅ Implemented (delegate to java.lang.Math) |

---

## 12. Audio & MIDI Engine Improvements (RtAudio/RtMidi-Inspired)

Based on analysis of `../rtaudio/` and `../rtmidi/` (2026-04-14). Legend: ✅ Done · 🔄 In Progress · ❌ Planned

### Phase 1 — Native Drivers & Core API (RtMidi Done)

| Feature | Source | Status | Notes |
|---------|--------|--------|-------|
| Callback-driven MIDI Input | `rtmidi_set_callback` | ✅ Done | Replaced 1ms polling with native upcall stubs |
| Native MIDI Output | `rtmidi_out_send_message` | ✅ Done | Low-latency output via Panama; JavaSound fallback |
| Virtual MIDI Ports | `rtmidi_open_virtual_port` | ✅ Done | Supported on macOS (CoreMIDI) and Linux (ALSA/JACK) |
| MIDI Message Filtering | `rtmidi_in_ignore_types` | ✅ Done | `min.ignoreTypes(sysex, time, sense)` |
| Audio Sample Formats | `RtAudio::SampleFormat` | ✅ Done | INT16/INT24/INT32/FLOAT32 support in JavaSound |
| Audio Device Probing | `RtAudio::getDeviceInfo` | ✅ Done | Channel counts, sample rates, and native formats |

### Phase 2 — Advanced Audio (In Progress by Claude)

| Feature | Source | Status | Notes |
|---------|--------|--------|-------|
| `AudioBackend` interface | `RtApi` base | 🔄 In Progress | Swappable backends (WASAPI, CoreAudio, ALSA, JACK) |
| `setMinimizeLatency()` | `RTAUDIO_MINIMIZE_LATENCY` | ❌ Planned | Automatic buffer size optimization |
| Non-interleaved processing | `RTAUDIO_NONINTERLEAVED` | ❌ Planned | Block-based processing for better cache locality |
| `numberOfBuffers` control | `StreamOptions.numberOfBuffers` | ❌ Planned | `ChuckAudio.setNumBuffers(int)` |

### Phase 3 — Platform-Specific / FFM

| Feature | Source | Status | Notes |
|---------|--------|--------|-------|
| WASAPI Exclusive Mode | `RTAUDIO_HOG_DEVICE` | ❌ Planned | FFM binding to IAudioClient |
| Real-time thread priority | `RTAUDIO_SCHEDULE_REALTIME` | ❌ Planned | FFM → `pthread_setschedparam(SCHED_RR)` on Linux; `SetThreadPriority(TIME_CRITICAL)` on Windows |
| JACK audio backend | `RtApiJack` | ❌ Planned | Load `libjack` via FFM; ChucK appears as named JACK client |
| ALSA direct backend | `RtApiAlsa` | ❌ Planned | FFM → `snd_pcm_open()`; bypasses PulseAudio/PipeWire overhead |

---

## 13. Planned STK Extensions (Modern STK)

These features are from the latest upstream STK repository and represent "beyond classic ChucK" additions.

| Feature | Category | Description | Status |
|---------|----------|-------------|--------|
| `Guitar` | Instrument | 🎸 Advanced multi-string physical model with fret noise and coupling. | ✅ Done |
| `LentPitShift` | Effect | 🎤 Formant-preserving pitch shifter. | ✅ Done |
| `Granular` | Synthesis | 🌌 Dedicated granular synthesis engine with scatter/density control. | ✅ Done |
| `Distortion` | Effect | 🎛️ Saturation suite (Bitcrusher, Overdrive, Fuzz). | ✅ Done |
| `FreeVerb` | Reverb | ⛪ Lush Schroeder-Moorer algorithmic reverb. | ✅ Done |

