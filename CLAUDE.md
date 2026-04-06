# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Java implementation of the [ChucK](https://chuck.stanford.edu/) strongly-timed music programming language. It compiles `.ck` source files and executes them with real-time audio via a virtual machine. Built with JDK 25 and uses Java Virtual Threads for concurrency.

## Build & Run Commands

```bash
# Build
mvn compile

# Run all tests (130 JVM tests)
mvn test

# Run a single test class
mvn test -Dtest=ChuckCompilerTest

# Run the application (pass a .ck file)
mvn exec:java -Dexec.args="path/to/script.ck"

# Launch the JavaFX IDE
mvn javafx:run

# Package (fat JAR)
mvn package

# Build GraalVM native executable (all platforms — requires JAVA_HOME → GraalVM JDK 25)
#   Windows: set JAVA_HOME=C:\path\to\graalvm-jdk-25
#   Mac/Linux: export JAVA_HOME=/path/to/graalvm-jdk-25
mvn -Pnative package -DskipTests
#   Output: target/chuck  (Linux/Mac)  or  target/chuck.exe  (Windows)

# Run native tests (108 tests, excludes DslExamplesTest + PolyphonyDSLTest)
mvn -Pnative -DskipNativeTests=false test

# Build self-contained JavaFX IDE bundle (all platforms)
# Includes AOT cache training run (JEP 483) for faster IDE startup. Add -DskipAot=true to skip.
mvn -Pide-bundle package -DskipTests                        # app-image (default, all OSes)
mvn -Pide-bundle package -DskipTests -Djpackage.type=dmg    # macOS disk image
mvn -Pide-bundle package -DskipTests -Djpackage.type=deb    # Linux .deb (needs dpkg-deb)
#   Output: target/chuck-ide-bundle/chuck-ide/      (Linux/Windows)
#           target/chuck-ide-bundle/chuck-ide.app/  (macOS app-image)
#           target/chuck-ide-bundle/*.dmg            (macOS dmg)

# Build Linux binary via Docker (from any platform with Docker installed)
docker build --output dist/linux .                          # just the chuck binary
docker build --target full --output dist/linux .            # binary + IDE bundle
```

**JDK upgrade roadmap:** See [JDK_ROADMAP.md](JDK_ROADMAP.md) for analysis of JDK 26/27 features
(Valhalla value classes, Vector API graduation timeline, Project Leyden AOT, JavaFX 26 Metal,
Structured Concurrency finalization) and their specific impact on this project.

**Important:** Preview features (`--enable-preview`) and `jdk.incubator.vector` are required — the Maven plugins are already configured for this, but if running `java` directly you must pass these flags.

## Architecture

The pipeline: `.ck` source → `ChuckANTLRLexer` → `ChuckANTLRParser` → `ChuckASTVisitor` → AST → `ChuckEmitter` → `ChuckCode` (bytecode) → `ChuckVM` executes via `ChuckShred`s.

### Packages

- **`org.chuck`** — Entry point (`Main.java`): reads a `.ck` file, wires together the compile pipeline, and runs it.
- **`org.chuck.compiler`** — Compiler pipeline:
  - `ChuckANTLR.g4` — ANTLR4 grammar for the ChucK language
  - `ChuckASTVisitor` — maps ANTLR parse tree to `ChuckAST`
  - `ChuckEmitter` — walks the AST and emits `ChuckInstr` instances into a `ChuckCode` object
- **`org.chuck.core`** — VM runtime:
  - `ChuckVM` — manages the shreduler (priority queue by wake time), global variables (`dac`, `blackhole`, ints, objects), and drives the UGen audio graph sample-by-sample
  - `ChuckShred` — a concurrent execution unit backed by a Java Virtual Thread; uses `ReentrantLock`/`Condition` to yield/resume at sample-accurate times
  - `ChuckCode` / `ChuckInstr` — bytecode container and instruction interface
  - `ChuckStack` — register (`reg`) and memory (`mem`) stacks per shred
  - Individual instruction classes (`PushInt`, `PushFloat`, `AdvanceTime`, `ChuckTo`, `CallFunc`, etc.)
- **`org.chuck.audio`** — Unit Generator (UGen) graph:
  - `ChuckUGen` (abstract base) — pull-based tick mechanism; `tick(long systemTime)` is idempotent per sample
  - Concrete UGens: oscillators (`SinOsc`, `SawOsc`, `TriOsc`, `SqrOsc`, `PulseOsc`, `Phasor`, `BlitSaw`, `BlitSquare`), filters (`LPF`, `HPF`, `BPF`, `BRF`, `ResonZ`), effects (`Echo`, `Delay`, `Chorus`, `JCRev`, `AllPass`), envelopes (`Adsr`, `Envelope`), instruments (`Clarinet`, `Mandolin`, `Plucked`, `Rhodey`, `Wurley`, `BeeThree`, `HevyMetl`, `PercFlut`, `TubeBell`, `FMVoices`), analyzers (`ZCR`, `MFCC`, `SFM`, `Kurtosis`, `RMS`, `Centroid`, `FFT`, `IFFT`), utilities (`Gain`, `Pan2`, `Noise`, `Impulse`, `Step`, `Blackhole`, `SndBuf`, `WvOut`)
  - `ChuckAudio` — Java `SourceDataLine` audio output; calls `vm.advanceTime(bufferSize)` per buffer
- **`org.chuck.ide`** — `ChuckIDE`: a JavaFX-based code editor with syntax highlighting (RichTextFX)
- **`org.chuck.midi`** — `MidiIn`/`ChuckMidi`/`MidiFileIn` for MIDI input and file playback
- **`org.chuck.core.ai`** — AI/ML library: `KNN`, `KNN2`, `SVM`, `MLP`, `HMM`, `PCA`
- **`org.chuck.core.instr`** — Modular instruction set organized by category (`ArithmeticInstrs`, `ControlInstrs`, `TypeInstrs`, etc.)

### Recently Fixed Issues (2026-03-10)
- `SetMemberIntByName` was missing — now created, uses reflection + `setData` fallback
- `++`/`--`, `!`, `&&`, `||`, `%`, `=` (ASSIGN), string literals added to lexer+parser
- Emitter now handles: `UnaryExp`, `StringExp`, `ReturnStmt`, `ASSIGN`, `CallMethod`, `MathFunc`, `StdFtom`, all 22 UGen types in `InstantiateAndSetGlobal`
- IDE: replaced `TextArea` with RichTextFX `CodeArea` (syntax highlighting, line numbers, error line highlighting)
- Concurrency model was audited and confirmed correct — Virtual Thread yield works via `ReentrantLock`

### Recently Fixed Issues (2026-03-22)
- `emitChuckTarget(DeclExp)` bug: `StackSwap + Pop + StoreLocal` stored default 0 instead of source; replaced with `Pop + StoreLocal`
- ANTLR grammar precedence: `conditionalOp` (ternary `?:`) was listed below `chuckOp` (`=>`), giving it lower priority; swapped to fix `a ? b : c => x` parsing
- Added `BlitSaw` and `BlitSquare` PolyBLEP band-limited oscillators
- Added `HPF`, `BPF`, `BRF` Butterworth filters
- Added `switch`/`case`/`default` statement support (ANTLR grammar + emitter)
- Full Machine shred API: `numShreds()`, `shreds()`, `shredExists(id)`, `crash()`, `removeAll()`
- Full `me.*` shred API: `path()`, `source()`, `sourcePath()`, `running()`, `done()`; new instruction classes `MeId`, `MePath`, `MeRunning`

### Recently Fixed Issues (2026-03-23)
- `SwapLocal` type-tag corruption: `setRef(idx, null)` was marking int slots as objects; fixed by copying all four parallel arrays respecting source-slot type flags
- Root-scope array declarations now use global storage (`InstantiateSetAndPushGlobal`) so `vm.getGlobalObject(name)` returns them correctly
- Added `vec2` type (2-element vector, stored as `ChuckArray(2)`)
- Vector field accessors `.x/.y/.z/.w`, `.re/.im`, `.mag/.phase` wired in emitter for both read (`DotExp`) and write (`emitChuckTarget` DotExp path)
- Added `ZCR` (Zero Crossing Rate) UAna — frame-based, configurable `frameSize`, `upchuck()`-compatible
- Added `MFCC` UAna — mel filterbank (26 bands) + log + DCT-II; `computeFromSpectrum()` for external FFT input
- Added FM instruments: `Wurley`, `BeeThree`, `HevyMetl`, `PercFlut`, `TubeBell`, `FMVoices` — all with `noteOn`/`noteOff`/`setFreq`
- Comparison operator overloading (`<`, `<=`, `>`, `>=`, `==`, `!=`) dispatches to user-defined `__pub_op__<:2` functions (same dispatch path as arithmetic)
- Built-in `complex`/`polar` arithmetic: `ComplexAdd/Sub/Mul/Div`, `PolarAdd/Sub/Mul/Div` inner instruction classes; polar `*`/`/` use magnitude×/÷ and phase+/−
- Built-in `vec2`/`vec3`/`vec4` arithmetic: `VecAdd`, `VecSub`, `VecScale`, `VecDot` instructions; emitter detects vec LHS type via `varTypes` map
- Added `SFM` (Spectral Flatness Measure) and `Kurtosis` UAnas — both chain after `FFT`, pull `getFvals()` from upstream blob

### Std Library & Machine API Additions (2026-03-30)
- `Std.itoa(long)` — int to string
- `Std.ftoa(double, long)` — float to string with decimal places
- `Std.ftoi(double)` — float to int (truncate)
- `Std.sgn(double)` — sign: returns -1.0, 0.0, or 1.0
- `Std.scalef(val, srcMin, srcMax, dstMin, dstMax)` — linear rescale
- `Std.abs(long)` — absolute value of int
- `ChuckEvent.can_wait()` — always returns true
- `Machine.resetID` — resets shred ID counter via `ChuckShred.resetIdCounter()`
- `Machine.gc` — calls System.gc()
- `Machine.version` — returns version string "1.5.4.0 (java)"
- `Machine.platform` — returns OS name
- `Machine.loglevel` / `Machine.setloglevel` — get/set log level
- `Machine.timeofday` — current time in seconds since epoch
- `Machine.clearVM` — alias for `Machine.clear` in MachineCall switch
- `StringTokenizer` already existed with `set(s)`, `more()`, `next()`, `reset()` — registered as `"StringTokenizer"` in emitter
- Tests: 11 tests in `ChuckStdTest` (all passing)

### Recently Fixed Issues (2026-03-30)
- `TwoPole`, `TwoZero`, `PoleZero`, `DelayA`, `Blit`, `CNoise` UGens added and tested.
- `Machine` API expanded with `version`, `platform`, `timeofday`, etc.
- `Std` library expanded with `itoa`, `ftoa`, `scalef`, `abs`.

### Recently Fixed Issues (2026-04-03)
- **Complex/Polar Logic**: Implemented `Math.rtop()` and `Math.ptor()` for conversion; fixed `scalar * complex/polar` dispatch in emitter.
- **`ChuckArray.sort()`**: Full implementation for all types (int, float, complex, polar, vec, string); numeric arrays sort by value, others by magnitude/alphabetically.
- **Math Constants**: Added `Math.j` (#(0,1)) and supported lowercase `pi`, `e`, `sqrt2` for ChucK compatibility.
- **Assignment Chaining**: Fixed `SetArrayInt` to push the object back onto the stack, enabling `val => arr[i] => ...` chaining.
- **Reflection & Durations**: `CallMethod` and `SetMemberIntByName` now implicitly convert `ChuckDuration` to double (samples) when passed to numeric parameters.
- **Built-in Shadowing**: Fixed bug where variables named `e` or `pi` were shadowed by `Math.E` and `Math.PI` constants.
- **Missing Instantiations**: Added `Hid` and `MidiIn` to `ChuckFactory` to prevent NPEs on declaration.
- **Test Normalization**: Enhanced `ChucKIntegrationTest` to handle minor precision differences and multiple polar output formats (pi-multiples vs radians).
- **Console Verbosity**: Removed sample-rate debug prints from `DacChannel` that caused massive log files.

### Recently Fixed Issues (2026-04-04)
- **Forward References**: Implemented multi-pass emission in `ChuckEmitter` to support calling functions and classes before they are defined in the source.
- **Variable Shadowing**: Corrected variable resolution order to strictly follow Local > Class/Inherited > Global hierarchy, ensuring correct shadowing semantics.
- **Nested Classes**: Implemented recursive class registration and emission, supporting experimental nested class definitions.
- **Auto-Instantiation**: Objects declared as fields (e.g. `SinOsc s;` inside a class) are now automatically instantiated in the pre-constructor, matching ChucK behavior.
- **Machine.add Path Resolution**: Fixed `Machine.add` to resolve relative paths relative to the calling script's directory instead of the current working directory.
- **Std.range**: Implemented full `Std.range()` suite (stop, start/stop, start/stop/step) with automatic step sign adjustment.
- **Mutable Strings**: Implemented `ChuckString.replace`, `insert`, `erase`, `set` methods and ensured strings are copied on assignment (`=>`).
- **Calling Convention**: Simplified `CallFunc`/`CallMethod` to push return info and jump, leaving argument moving to the standard `MoveArgs` instruction.
- **Field Access**: Fixed `SetFieldByName` and `GetFieldByName` to correctly handle built-in vector/complex/polar fields and fallback to UGen `setData` for test mocks.
- **VM Stability**: Optimized the interpreter loop with a safety yield threshold (10,000 instructions) to prevent infinite tight loops from hanging the VM.
- **Test Infrastructure**: Enhanced integration tests to automatically abort hanging shreds and cap captured output to 10KB.

### Java DSL Examples & Tests (2026-04-05)

**`examples_dsl/` directory** — 19 ready-to-run Java DSL shreds (each implements `Shred`, no `package` declaration):

| File | Source `.ck` | Demonstrates |
|------|-------------|--------------|
| `SineDSL.java` | `basic/adsr.ck` | Basic SinOsc |
| `FmDSL.java` | `basic/fm.ck` | FM with ADSR |
| `AdsrDSL.java` | `basic/adsr.ck` | ADSR envelope, MIDI-to-freq |
| `EnvelopeDSL.java` | `basic/envelope.ck` | Linear Envelope on Noise |
| `LfoDSL.java` | `basic/lfo.ck` | LFO to blackhole, printed values |
| `PhasorDSL.java` | `basic/phasor.ck` | Phasor modulating PulseOsc width |
| `CombDSL.java` | `basic/comb.ck` | Feedback comb filter (Noise source) |
| `ChirpDSL.java` | `basic/chirp.ck` | Frequency sweep with helper method |
| `BlitDSL.java` | `basic/blit.ck` | BLIT + JCRev, pentatonic scale |
| `HarmonicsDSL.java` | `basic/harmonics.ck` | Harmonic series sweep |
| `WindDSL.java` | `basic/wind.ck` | Noise → BiQuad resonance sweep |
| `OscillatorsDSL.java` | `basic/oscillatronx.ck` | All 6 oscillator types + FM pair |
| `Fm2DSL.java` | `basic/fm2.ck` | SinOsc FM with `sync(2)` |
| `Blit2DSL.java` | `basic/blit2.ck` | BLIT + ADSR articulation + JCRev |
| `LarryDSL.java` | `basic/larry.ck` | Impulse → BiQuad frequency sweep |
| `LpfDSL.java` | `filter/lpf.ck` | Noise → LPF cutoff sweep |
| `BpfDSL.java` | `filter/bpf.ck` | Noise → BPF center sweep |
| `ResonZDSL.java` | `filter/resonz.ck` | Noise → ResonZ sweep |
| `ChorusDSL.java` | `effects/chorus.ck` | 4-voice Chorus (Dm7 chord) |
| `ClarDSL.java` | `stk/clarinet.ck` | Clarinet physical model melody |
| `WurleyDSL.java` | `stk/wurley.ck` | Wurley FM piano, pentatonic |
| `PolyphonyDSL.java` | — | Concurrent shreds via `vm.spork()` |
| `DslDemo.java` | — | Hot-reload demo, random notes |

**Tests:** `DslExamplesTest` (20 `@Test` methods) covers every example above except the infinite-loop demo. Each test compiles the `.java` file at runtime via `ChuckDSL.load()`, sporks it into a headless VM, and asserts `maxRms > 0.001`.

### Recently Fixed Issues (2026-04-05)
- **Complex/Polar Integration**: Fully implemented `complex` and `polar` types, including arithmetic, array initialization, and special formatting in `ChuckPrint`.
- **Method Call Resolution**: Improved implicit instance method resolution for bare calls within classes.
- **executeSynchronous Refactor**: Fixed nested method calls in constructors by tracking `framePointer` instead of stack pointer.
- **Machine.add Arguments**: Implemented support for ChucK-style arguments in `Machine.add("file.ck:arg1:arg2")`.
- **Std Library**: Implemented `Math.rtop`, `Math.ptor`, `Std.getenv`, and `Std.setenv`.
- **Unit Test Stability**: Fixed shred ID generator reset on VM initialization to ensure predictable test environments.
- **Deadlock & Silent UGens**: Fixed `ChuckShred.cleanup()` deadlock and silent `Delay`/`Clarinet` UGens.

### Stdlib Additions (2026-04-06)

**Std library (`Std.java`):**
- `Std.rand()` — random int in [0, RAND_MAX]
- `Std.randf()` — random float in [0.0, 1.0)
- `Std.system(cmd)` — execute a shell command via `ProcessBuilder`; returns exit code

**New stdlib classes:**
- `ConsoleInput` — `readline()`, `prompt(string)`, `ready()`, `can_wait()`; shared `BufferedReader` on `System.in`
- `KBHit` — `kbhit()` / `hit()`, `getchar()`, `can_wait()`; background virtual thread queues keypresses from `System.in`
- `MidiFileIn` — `open(string)`, `read(MidiMsg)`, `more()`, `rewind()`, `close()`, `size()`, `numTracks()`, `resolution()`; uses `javax.sound.midi.MidiSystem`; merges and sorts all tracks by tick
- `HidOut` — stub; `open()` always returns 0 (native USB HID reports not available in standard Java SE)

All four classes registered in `ChuckFactory` and in `reflect-config.json` for GraalVM native image.

### Math Library Additions (2026-04-06)

All functions from section 5.2 of `missing.md` are now implemented in `MathInstrs.MathFunc`:

| Category | Functions |
|----------|-----------|
| Hyperbolic trig | `sinh(x)`, `cosh(x)`, `tanh(x)` |
| Geometry / numeric | `hypot(x,y)`, `fmod(x,y)`, `remainder(x,y)` |
| Scalar | `min(a,b)`, `max(a,b)`, `exp2(x)` |
| Integer | `nextpow2(n)`, `ensurePow2(n)` — returns `long` (next power of 2 ≥ n) |
| Random | `random2(lo,hi)`, `random2i(lo,hi)`, `random2f(lo,hi)`, `randomf()`, `gauss(mean,std)` |
| Mapping | `map(x,srcMin,srcMax,dstMin,dstMax)` = linear; `map2`/`remap(...,type)` = linear/cosine/smoothstep |
| Clamping | `clampi(x,lo,hi)` (int), `clampf(x,lo,hi)` (float) |
| Array | `cossim(a[],b[])` — cosine similarity |
| Fast scalar approx | `ssin`, `scos`, `stan`, `ssinh`, `scosh`, `stanh`, `sexp`, `sinsqrt` (delegate to std Math) |

The emitter's existing `default` fallthrough already pushes all args and calls `MathFunc`; no emitter changes were needed.

### GraalVM Native Image & IDE Bundle (2026-04-05)

**Native executable (`-Pnative` profile):**
- `NativeMain.java` — headless entry point (no JavaFX dependency)
- `ChuckCLI.java` — IDE launch now uses `Class.forName()` so native image compiles cleanly
- `src/main/resources/META-INF/native-image/org.chuck/chuck-java/` — auto-discovered config:
  - `native-image.properties` — `--enable-preview`, `--add-modules=jdk.incubator.vector`, ANTLR build-time init, `--no-fallback`
  - `reflect-config.json` — reflection registrations for `Std`, `RegEx`, `Reflect`, `SerialIO`, `ChuckUGen`, `ChuckShred` (needed for `CallMethod` dispatch), ANTLR classes
  - `resource-config.json` — includes `examples/*.ck` resources
- No GraalVM path in pom.xml — relies entirely on `JAVA_HOME` pointing at GraalVM JDK 25
- Output: `target/chuck` (Linux/Mac) or `target/chuck.exe` (Windows) — ~49 MB, no JRE needed

**Native test support (`-DskipNativeTests=false`):**
- 108 of 130 tests run natively; 22 excluded (`DslExamplesTest` + `PolyphonyDSLTest`) because they use `ChuckDSL.load()` → `javax.tools.JavaCompiler` which is unavailable in native image
- `ChuckShred.allPublicMethods` in reflect-config fixes `me.running()` / `me.numArgs()` in native image

**IDE bundle (`-Pide-bundle` profile):**
- Uses `jpackage` to produce a self-contained directory or installer
- `jpackage.type` property defaults to `app-image`; override per-platform: `dmg`, `deb`, `rpm`, `msi`, `exe`
- Fat JAR includes `ServicesResourceTransformer` (merges JavaFX `META-INF/services/` entries)
- Output: `target/chuck-ide-bundle/chuck-ide/` (Linux/Windows) or `.app`/`.dmg` (macOS)
- **AOT cache (JEP 483, JDK 24+):** build does a training run to generate `chuck.aot` alongside the JAR; the cache is bundled inside the app-image and loaded via `-XX:AOTCache=$APPDIR/lib/app/chuck.aot`. Pre-warms class loading and ANTLR ATN init, reducing IDE startup time. Skip with `-DskipAot=true` (CI and Docker use this automatically).

**Cross-platform CI (`/.github/workflows/build.yml`):**
- GitHub Actions matrix: `ubuntu-latest`, `macos-latest`, `windows-latest`
- Each runner: (1) runs JVM tests, (2) builds native CLI, (3) runs 108 native tests, (4) builds IDE bundle
- macOS builds `.dmg`; Windows/Linux build `app-image`
- Release job attaches all three native binaries to GitHub Releases automatically

**Docker (`Dockerfile`):**
- Builds Linux native binary from any platform with Docker installed
- Based on `ghcr.io/graalvm/native-image-community:25`
- `docker build --output dist/linux .` → extracts `chuck` binary
- `docker build --target full --output dist/linux .` → extracts `chuck` + IDE bundle

### Major Additions (2026-04-06)

#### Language Features
- **`typeof(expr)`** — returns type name as string; `TypeInstrs.TypeofInstr` pops value, inspects Java type
- **`instanceof(expr, TypeName)`** — returns 1/0; `TypeInstrs.InstanceofInstr` walks class hierarchy
- **`abstract class`** / **`interface`** — `ABSTRACT`/`INTERFACE` grammar tokens; `UserClassDescriptor.isAbstract/isInterface` flags; `ChuckFactory` throws on instantiation attempt
- **`loop` statement** — infinite loop (`loop { ... }`); only `break` exits; `LOOP` lexer token + grammar rule + emitter
- **`@construct`** — already parsed via `REFERENCE_TAG ID` in `functionName`; emitter handles at class compile time
- **`@destruct`** — called on shred cleanup for `UserObject`s registered via `ChuckShred.registerDestructible()`
- **`@operator` overload** — `fun @operator+(...)` already mapped to `__op__+` in `ChuckASTVisitor.visitFunctionDef`
- **`Machine.*` property reads** — `Machine.realtime`, `Machine.silent`, etc. without `()` now handled in `DotExp` emitter path (previously fell through to `GetFieldByName` on null)

#### AI / ML Library (`org.chuck.core.ai`)
All six classes from ChucK's `ulib_ai.cpp` implemented:
- `KNN` — k-nearest neighbor regression; `train(float[][], float[][])`, `predict(float[], float[])`, `k`
- `KNN2` — k-nearest neighbor classification; `train`, `predict`, `search`, `k`
- `SVM` — linear one-vs-rest SVM (subgradient descent); `train`, `predict`, `save(string)`, `load(string)`
- `MLP` — feedforward network with backprop SGD; `input/hidden/output(int)`, `train`, `predict`, `activation(string)`
- `HMM` — Gaussian emission HMM with Baum-Welch EM; `train`, `generate`, `logLikelihood`
- `PCA` — power iteration + deflation; `train`, `transform`, `explainedVariance`, `numComponents`
All registered in `ChuckFactory`; each extends `ChuckObject` with `super(ChuckType.OBJECT)`.

#### New UGens / UAnas
- **`DelayP`** — pitch-shifting delay; two crossfading read-heads; API: `delay(samples)`, `max(samples)`, `shift(semitones)`
- **`JetTabl`** — STK jet saturation curve `x*(0.2−x²*0.12)`, used internally by `Flute`
- **`FilterBasic`** — abstract filter base; `freq(double)`, `Q(double)`, `set(double,double)`
- **`FilterStk`** — STK filter base extending `FilterBasic`; adds `gain(double)`, `clear()`
- **`BiQuadStk`** — STK-style BiQuad with direct coefficient API: `setB0/B1/B2/A1/A2`, `setCoeffs`, `clear`
- **`LiSa6`**, **`LiSa10`** — registered via `LiSaN(6/10, sr)`
- UAnas (all in `org.chuck.audio`): `DCT`, `IDCT`, `AutoCorr`, `XCorr`, `Chroma`, `FeatureCollector`, `Flip`, `UnFlip`
- STK instruments: `VoicForm`, `ModalBar`, `BandedWG`, `BlowBotl`, `BlowHole`, `HnkyTonk`, `FrencHrn`, `KrstlChr`

#### Stdlib & Networking
- **`OscEvent`** — subclass of `OscIn`; registered in `ChuckFactory`; same API (`port`, `addAddress`, `recv`)
- **`ChuckTypeObj`** — runtime `Type` introspection object: `name()`, `parent()`, `isa(string)`, `eq(Type)`; registered as `"Type"` in `ChuckFactory`
- **`ConsoleInput`**, **`KBHit`**, **`MidiFileIn`**, **`HidOut`** — all registered in `ChuckFactory` and `reflect-config.json`

#### Bug Fixes
- Removed `EXE [N]` trace `System.out.println` from `ChuckShred.execute()`
- `mvn package` fix: AI/ML classes were missing `super(ChuckType.OBJECT)` constructors — incremental `mvn test` masked this; clean build exposed it
- `FilterStk.gain(double)` return type changed from `void` to `double` to match `ChuckUGen` override


### Key Design Patterns

**Time model:** `ChuckVM.advanceTime(n)` advances the logical clock sample-by-sample. Each sample, the shreduler wakes any `ChuckShred` whose `wakeTime <= now`, then ticks the entire UGen graph. Shreds yield by calling `shred.yield(samples)`, which suspends their Virtual Thread until the VM advances to their wake time.

**UGen graph:** Pull-based. The DAC channels are the roots; `tick(systemTime)` recurses through `sources` lists. Each UGen is ticked at most once per sample (guarded by `lastTickTime`). Adding a connection (`ChuckTo` instruction) calls `src.chuckTo(dest)` which appends to `dest.sources`.

**ChucK `=>` operator:** The `CHUCK` operator in ChuckAST is the central wiring mechanism — it handles both UGen connections and value assignments depending on operand types.
