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

Envelopes: `Envelope`, `Adsr`

Instruments (STK): `Clarinet`, `Mandolin`, `Plucked`, `Rhodey`, `Wurley`, `TubeBell`, `BeeThree`, `FMVoices`, `HevyMetl`, `PercFlut`, `Moog`, `Saxofony`, `Flute`, `Brass`, `Sitar`, `StifKarp`, `Shakers`, `Bowed`

Tables / GenX: `Gen5`, `Gen7`, `Gen9`, `Gen10`, `Gen17`, `GenX`, `WarpTable`, `CurveTable`

Buffers: `SndBuf`, `SndBuf2`, `WvIn`, `WvOut`/`WvOut2`, `WaveLoop`, `LiSa`, `LiSa2`, `LiSa4`, `LiSa8`, `LiSa16`

Utilities: `Gain`, `GainDB`, `Blackhole`, `Adc`

### 1.2 Missing UGens — registered in C++ but absent in Java

| UGen | C++ File | Description |
|------|----------|-------------|
| `VoicForm` | `ugen_stk` | Formant voice synthesizer (4-formant singing voice, different from FMVoices) |
| `ModalBar` | `ugen_stk` | Modal-resonance bar physical model (xylophone, vibraphone, etc.) |
| `BandedWG` | `ugen_stk` | Banded waveguide (bowed bar, tibetan bowl, etc.) |
| `BlowBotl` | `ugen_stk` | Blown bottle physical model |
| `BlowHole` | `ugen_stk` | Clarinet with tonehole and register vent |
| `HnkyTonk` | `ugen_stk` | FM honky-tonk piano |
| `FrencHrn` | `ugen_stk` | FM French horn |
| `KrstlChr` | `ugen_stk` | FM crystal choir |
| `JetTabl` | `ugen_stk` | Jet table (lookup table used by `Flute`; exposed as standalone UGen in C++) |
| `Mesh2D` | `ugen_stk` | 2D waveguide mesh (spatial physical model) |
| `DelayP` | `ugen_xxx` | Pitch-shifting delay (different from `DelayA`/`DelayL`) |
| `FilterBasic` | `ugen_xxx` | Abstract base exposing `freq`, `Q`, `set` — parent of LPF/HPF/BPF/BRF |
| `FilterStk` | `ugen_stk` | STK-compatible filter base |
| `BiQuadStk` | `ugen_stk` | STK BiQuad (distinct from `BiQuad` — different coefficient style) |
| `Teabox` | `ugen_stk` | Sensor input box (hardware-specific, low priority) |
| `LiSa6`, `LiSa10` | `ugen_xxx` | 6- and 10-voice LiSa variants (Java has 2/4/8/16 only) |

---

## 2. Unit Analyzers (UAna)

### 2.1 Present in Java

`FFT`, `IFFT`, `RMS`, `Centroid`, `Flux`, `ZeroX`, `ZCR`, `MFCC`, `SFM`, `Kurtosis`, `Rolloff`

### 2.2 Missing UAnas — in C++ but absent in Java

| UAna | Description |
|------|-------------|
| `DCT` | Discrete Cosine Transform (forward) — useful for audio compression / feature extraction |
| `IDCT` | Inverse DCT |
| `AutoCorr` | Autocorrelation of input buffer — pitch detection, periodicity analysis |
| `XCorr` | Cross-correlation between two UAna streams |
| `Chroma` | 12-bin pitch-class profile (chroma features) — key detection |
| `FeatureCollector` | Aggregates outputs from multiple UAnas into a single feature vector (used with AI/ML) |
| `Flip` | Copies audio buffer into UAna blob without analysis — enables custom analysis |
| `UnFlip` | Inverse of Flip: copies UAna blob back to audio buffer |

`FeatureCollector` is particularly important because it bridges the UAna graph to the AI/ML classes (`KNN`, `MLP`, etc.).

---

## 3. AI / ML Library (`ulib_ai.cpp`)

Completely absent in Java. All five classes need implementation:

| Class | Description | Key Methods |
|-------|-------------|-------------|
| `KNN` | K-Nearest Neighbor (regression) | `train(float[][], float[][])`, `predict(float[], float[])` |
| `KNN2` | K-Nearest Neighbor (classification) | `train(float[][], int[])`, `predict(float[], int[])`, `search(float[])` |
| `SVM` | Support Vector Machine | `train(float[][], int[])`, `predict(float[])`, `save(string)`, `load(string)` |
| `HMM` | Hidden Markov Model | `train(float[][])`, `generate(float[])`, `viterbi(float[][], int[])`, `forward(float[][])` |
| `MLP` | Multi-Layer Perceptron | `input/hidden/output(int)`, `train(float[][], float[][])`, `predict(float[])`, activation functions |
| `PCA` | Principal Component Analysis | `train(float[][])`, `transform(float[], float[])`, `explainedVariance(float[])` |

The typical ChucK AI workflow is: `Flip → FeatureCollector → KNN/MLP` for real-time machine learning on audio.

---

## 4. Language / Grammar

### 4.1 Present in Java grammar (`ChuckANTLR.g4`)

`if/else`, `while`, `until`, `for`, `repeat`, `do-while`, `do-until`, `switch/case/default`, `break`, `continue`, `return`, `null`, `class extends`, `public/private/protected/static/global/const`, `@` references, `complex`/`polar`/`vec` literals, operator overloading, ternary `?:`, `spork`, `new`, `#include`/`@import`.

### 4.2 Missing language features — in C++ grammar but not in Java

| Feature | C++ grammar | Java status | Notes |
|---------|-------------|-------------|-------|
| `typeof` | `TYPEOF unary_expression` | ❌ Not in grammar or emitter | Returns the type name as a string |
| `instanceof` | Used in type checker | ❌ Not in grammar or emitter | Runtime type test (`x instanceof Foo`) |
| `abstract` class / method | `ABSTRACT` keyword | ❌ Not in grammar | Cannot declare abstract base classes |
| `interface` | `INTERFACE id_list LBRACE` | ❌ Not in emitter | Grammar has the token but emitter ignores interface bodies |
| `@construct` | Explicit constructor syntax | ❌ Not in grammar | C++ allows `fun @construct(...)` for custom constructors |
| `@destruct` | Explicit destructor syntax | ❌ Not in grammar | C++ calls `@destruct` when a shred-owned object is freed |
| `loop` statement | `LOOP LPAREN stmt RPAREN` | ❌ Not in grammar | Explicit named infinite loop (different from `while(true)`) |
| `@operator` overload syntax | `fun @operator+(...)` | ⚠️ Partial | Java uses `__op__+` naming convention; C++ uses `@operator+` |
| `static` class variables | `STATIC` in class body | ⚠️ Partial | Declared but not fully initialized between instances |
| `public` class-level access | `PUBLIC` in class body | ⚠️ Partial | Parsed but not enforced at runtime |
| `doc` comments (`/** */`) | `@doc` annotation | ❌ Not in grammar | ChucK doc comment extraction |

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
| `Machine.eval(string)` | ✅ Already implemented — calls `vm.eval()` → `vm.run()` → ANTLR pipeline; works in `chuck.exe` |
| `Machine.eval(string, args[])` | ❌ Missing — eval with argument list passed to the sporked shred |
| `Machine.removeLastShred()` | Remove the most recently sporked shred |
| `Machine.spork(string)` | Spork a file by path from Machine API |
| `Machine.intsize()` | Platform integer size in bits |
| `Machine.silent()` | Check/set silent mode |
| `Machine.realtime()` | Check if running in real-time mode |
| `Machine.refcount(obj)` | Debug: reference count of an object |
| `Machine.sp_reg`, `sp_mem` | Debug: current register / memory stack pointer |
| `Machine.printStatus()` | Print all active shreds to console |
| `Machine.printTimeCheck()` | Print timing diagnostics |
| `Machine.operatorsPush()` / `operatorsPop()` / `operatorsStackLevel()` | Operator overload namespace stack |
| `Machine.os()` | OS name (Java has `platform()` but C++ also exposes `os()` as alias) |

`Machine.eval()` is the most impactful missing item — it enables live coding, on-the-fly code injection, and the ChucK REPL.

---

## 8. Stdlib Classes (non-I/O)

| Class | Status | Description |
|-------|--------|-------------|
| `ConsoleInput` | ✅ Done | `readline()`, `prompt(string)`, `ready()`, `can_wait()` |
| `KBHit` | ✅ Done | `kbhit()` / `hit()`, `getchar()`, `can_wait()` — background virtual thread queues keypresses |
| `StringTokenizer` | ✅ Done | `set(s)`, `more()`, `next()`, `reset()` implemented |
| `RegEx` | ✅ Done | Pattern matching via `java.util.regex` |
| `Type` / `Function` introspection | ❌ Missing | Reflect on type names, method signatures at runtime |

---

## 9. I/O (all confirmed present in Java)

| Class | Status |
|-------|--------|
| `FileIO` (ASCII + binary) | ✅ Done |
| `MidiIn` / `MidiOut` / `MidiMsg` | ✅ Done |
| `OscIn` / `OscOut` / `OscMsg` / `OscBundle` | ✅ Done |
| `Hid` / `HidMsg` | ✅ Done |
| `SerialIO` | ⚠️ Stub (functional for basic use; no hardware serial port) |
| `MidiFileIn` | ✅ Done — `open(string)`, `read(MidiMsg)`, `more()`, `rewind()`, `close()`, `size()`, `numTracks()`, `resolution()` |
| `HidOut` | ✅ Done (stub) — `open(num)`, `send(HidMsg)`, `close()`, `name()`, `num()` — always returns 0 (native HID output requires platform libs) |

---

## 10. Event System

| Feature | Status | Notes |
|---------|--------|-------|
| `Event` base | ✅ Done | `signal()`, `broadcast()`, `wait()`, `can_wait()` |
| `OscEvent` | ⚠️ Partial | OscIn fires events; standalone `OscEvent` class not exposed |
| Conjunction `e1 && e2 => now` | ✅ Done | |
| Disjunction `e1 \|\| e2 => now` | ✅ Done | |
| Event timeout | ❌ Missing | C++ supports `timeout => now` on event waits |

---

## 11. Type System

| Feature | Status |
|---------|--------|
| `complex` / `polar` first-class types | ✅ Done |
| `vec2` / `vec3` / `vec4` | ✅ Done |
| `null` literal | ✅ Done |
| `auto` type inference | ⚠️ Grammar has token; emitter does not fully resolve inferred types |
| `typeof` operator | ❌ Missing |
| `instanceof` operator | ❌ Missing |
| `abstract` classes | ❌ Missing |
| `interface` definitions | ❌ Missing (token exists in grammar, emitter ignores body) |
| `@construct` / `@destruct` | ❌ Missing |
| `static` member variables (per-class, not per-instance) | ⚠️ Partial — declared but shared initialization not reliable |

---

## Priority Summary

### High — blocks real ChucK programs

| Item | Why it matters |
|------|---------------|
| `Machine.eval(string, args[])` | `eval(string)` is done; the `args[]` overload for passing arguments to the eval'd shred is missing |
| `typeof` / `instanceof` | Type-safe polymorphism patterns common in larger programs |
| `AutoCorr`, `XCorr` UAnas | Used in pitch detection and audio fingerprinting examples |
| `FeatureCollector` + AI/ML | The entire `examples/ai/` directory depends on these |
| `Chroma` UAna | Used in key/chord detection examples |
| `ConsoleInput` / `KBHit` | ✅ Done — interactive terminal programs now supported |
| Missing Math: `min`, `max`, `tanh`, `sinh`, `cosh` | ✅ Done — all Math section 5.2 functions implemented |
| `DCT` / `IDCT` | Used in MFCC-adjacent analysis and compression examples |

### Medium — affects completeness but workarounds exist

| Item | Why it matters |
|------|---------------|
| `VoicForm` | Formant synthesis — distinct from FM-based FMVoices |
| `ModalBar` | Modal synthesis for pitched percussion |
| `BandedWG`, `BlowBotl`, `BlowHole` | STK physical models in `examples/stk/` |
| `HnkyTonk`, `FrencHrn`, `KrstlChr` | FM instrument variants |
| `Flip` / `UnFlip` UAnas | Needed for custom UAna pipeline construction |
| `MidiFileIn` | MIDI file playback |
| `abstract` / `interface` | Class hierarchy patterns |
| `@construct` / `@destruct` | Explicit lifecycle management |
| Missing Math: `gauss`, `nextpow2`, `map`, `fmod` | Common in synthesis algorithms |

### Lower — niche or debug use

| Item | |
|------|-|
| `Mesh2D` | Spatial physical model; complex to implement |
| `DelayP` | Pitch-shifting delay; similar to `DelayL` |
| `LiSa6`, `LiSa10` | Only 4-variant gap |
| `Teabox` | Hardware sensor box; very platform-specific |
| `Machine.refcount`, `sp_reg`, `sp_mem` | Debug/introspection |
| `Machine.operatorsPush/Pop` | Namespace management for operator overloads |
| `HidOut` | Output HID (rarely used) |
| `Std.system()` | Shell execution (security risk; low demand) |
| Fast math approximations (`ssin`, `scos`, etc.) | Performance hints; Java JIT handles this differently |
