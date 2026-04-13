# ChucK-Java (JDK 25 Migration)

## Progress Update (2026-04-05, updated)

### Integration Test Coverage

| Suite | Tests | Passing | Notes |
|-------|-------|---------|-------|
| **01-Basic** | 243 | 235 | Core language, math, control flow, arrays, classes, complex/polar |
| **02-UGens** | 75 | 75 ✅ | All oscillators, filters, effects, physical models |
| **03-Modules** | 14 | 14 ✅ | FileIO (text + binary), OSC networking, seek |
| **04-Stress** | 15 | 8 | Deep recursion, large arrays, concurrency edge cases |
| **05-Global** | 52 | 51 | 77.ck fails: `me.dir(3)` requires 3-level path depth (test dir only 2 levels deep) |
| **06-Errors** | 109 | 42 | Error handling and type-checking edge cases |
| **07-Imports** | 9 | 9 ✅ | `#include` / machine imports |
| **ChuckAntlrNewFeaturesTest** | 23 | 23 ✅ | Ternary, switch/case, HPF/BPF/BRF, BlitSaw/BlitSquare |
| **ChuckMachineApiTest** | 16 | 16 ✅ | Full `me.*` and `Machine.*` shred API |
| **DslExamplesTest** | 20 | 20 ✅ | Java DSL shreds: oscillators, filters, effects, instruments |
| **NativeImageTests** | 108 | 108 ✅ | All non-DSL tests compiled to native via GraalVM |
| **Total** | **576** | **493 (86%)** | |

### Bugs Fixed & Language Improvements

| # | Improvement / Fix | Status |
|---|-----|--------|
| 1 | **Console Printing (`<<< ... >>>`)** — Added support for ChucK's standard debug printing syntax. | ✅ Fixed |
| 2 | **Swap Operator (`<=>`)** — Implemented the value/reference swap operator. | ✅ Fixed |
| 3 | **Unchuck Operator (`!=>`)** — Added support for disconnecting Unit Generators. | ✅ Fixed |
| 4 | **OSC Support (`OscIn`, `OscOut`, `OscMsg`)** — Full UDP networking: event-based `oin => now`, builder pattern `xmit.start().add().send()`, wildcard address matching (`/test/*`). | ✅ Fixed |
| 5 | **Inverse FFT (`IFFT`)** — Added support for spectral resynthesis. | ✅ Fixed |
| 6 | **HID Support (`Hid`, `HidMsg`)** — Integrated keyboard and mouse events from the IDE. | ✅ Fixed |
| 7 | **Advanced HID** — Added Windows Joystick/Gamepad support via FFM (Panama) API. | ✅ Fixed |
| 8 | **Unit Analyzer (UAna) Implementation** — Previously stubs; now feature real computation logic. | ✅ Fixed |
| 9 | **Missing `SetMemberIntByName` instruction** — class was referenced in the emitter but never created. | ✅ Fixed |
| 10 | **`++`/`--` operators not in lexer** — Added `PLUS_PLUS`, `MINUS_MINUS`, and emitter support. | ✅ Fixed |
| 11 | **SawOsc reflection mismatch** — Fixed `setFrequency` vs `setFreq` naming break. | ✅ Fixed |
| 12 | **String literals & Unary ops** — Added full support for `"strings"`, `-x`, and `!b`. | ✅ Fixed |
| 13 | **IDE Volume Slider** — The master gain slider now correctly controls the audio engine's output. | ✅ Fixed |
| 14 | **Compilation Stability** — Fixed several missing method and import errors in the IDE and Core. | ✅ Fixed |
| 15 | **Shred Management (`Machine`, `me`)** — Support for dynamic shred addition/removal and self-metadata. | ✅ Fixed |
| 16 | **Event `signal()`** — Support for waking a single shred from an event queue. | ✅ Fixed |
| 17 | **`LiSa` (Live Sampling Utility)** — Multi-voice buffer recording and playback. | ✅ Fixed |
| 18 | **`GenX` Family (`Gen5`, `Gen7`, `Gen10`)** — Table-based envelopes and oscillators. | ✅ Fixed |
| 19 | **IO Streams (`chout`, `cherr`)** — Support for standard output/error streams and the `<=` operator. | ✅ Fixed |
| 20 | **Advanced Physical Models** — Added `Moog`, `Flute`, `Sitar`, `Brass`, `Saxofony`, and `Shakers` STK models. | ✅ Fixed |
| 21 | **Operator Precedence** — Fixed math precedence (multiplicative > additive) in handwritten parser. | ✅ Fixed |
| 22 | **Chained Chuck Operators** — Support for long chains like `SinOsc s => Envelope e => dac;`. | ✅ Fixed |
| 23 | **Improved Mixed-Type Stack** — Corrected `popAsDouble` to handle both long and double bits seamlessly. | ✅ Fixed |
| 24 | **Array/UGen introspection** — Added support for `.size()` on arrays and `.last()` on UGens. | ✅ Fixed |
| 25 | **Binary FileIO (`IO.INT16`, `IO.INT32`, etc.)** — Full binary read/write with typed format constants. | ✅ Fixed |
| 26 | **Bitwise operators (`\|`, `&`)** — Added `BitwiseOrAny`/`BitwiseAndAny` instructions and emitter wiring. | ✅ Fixed |
| 27 | **`FileIO.good()` / `more()`** — Fixed EOF detection: `filePointer < fileLength`, empty-file special case. | ✅ Fixed |
| 28 | **`PitShift` UGen** — Added pitch-shifter UGen stub for compatibility. | ✅ Fixed |
| 29 | **`buffered` property** — Added `buffered` boolean to `ChuckObject` base class; readable/settable via member access on all UGens and objects. | ✅ Fixed |
| 30 | **`globalIsObject` registration** — Fixed `dac`, `blackhole`, `adc`, `chout`, `cherr`, `IO` not being registered in `globalIsObject` map; `GetGlobalObjectOrInt` was returning 0 instead of the object. | ✅ Fixed |
| 31 | **Compile-time global type conflict** — Emitter now detects when two `global` declarations in the same file use the same name with conflicting types and throws a descriptive compile error. | ✅ Fixed |
| 32 | **Operator overloading dispatch** — User-defined classes can now override `+`, `-`, `*`, `/`, `==`, `!=`, `<`, `>`, `!`, `++`, `--` via `op` functions. Tests 203–204, 207–209 all pass. | ✅ Fixed |
| 33 | **`ReturnFunc` zero-return bug** — Return value `0` was never re-pushed onto the reg stack after a function returned, causing stack underflow for callers expecting a result. | ✅ Fixed |
| 34 | **Class field literal initializers (`5 => int n;`)** — Class body statements of the form `<literal> => <type> <name>;` are now recognized as field declarations with initial values and correctly set on every new instance. | ✅ Fixed |
| 35 | **`SetMemberIntByName` push-back type** — After setting an `int` field on a `UserObject`, the instruction was pushing a `double` value back onto the stack, causing fields to print as floats (e.g. `1.000000` instead of `1`). | ✅ Fixed |
| 36 | **`emitChuckTarget` assignment bug** — `<value> => <type> <name>` was storing the default value (0) instead of the source value due to a `StackSwap+Pop` sequencing error. Fixed by replacing with a single `Pop` before `StoreLocal`. | ✅ Fixed |
| 37 | **Ternary `? :` operator** — `conditionalOp` was listed below `chuckOp` in the ANTLR grammar, giving it lower precedence than `=>`. Swapped ordering so `a ? b : c => x` assigns `(a ? b : c)` to `x`. | ✅ Fixed |
| 38 | **Band-limited oscillators (`BlitSaw`, `BlitSquare`)** — Added PolyBLEP anti-aliased sawtooth and square wave oscillators; configurable pulse width on `BlitSquare`. | ✅ Fixed |
| 39 | **Butterworth filters (`HPF`, `BPF`, `BRF`)** — High-pass, band-pass, and band-reject filters added alongside the existing `LPF`. | ✅ Fixed |
| 40 | **`switch`/`case` statement** — Full `switch`/`case`/`default` control flow with fall-through support and `break`. | ✅ Fixed |
| 41 | **Full Machine shred API** — Added `Machine.numShreds()`, `Machine.shreds()`, `Machine.shredExists(id)`, `Machine.crash()`, `Machine.removeAll()`. | ✅ Fixed |
| 42 | **Full `me.*` shred API** — Added `me.path()`, `me.source()`, `me.sourcePath()`, `me.running()`, `me.done()` methods. | ✅ Fixed |
| 43 | **`SwapLocal` type-tag corruption** — `ChuckSwap` called `setRef(idx, null)` on int-typed slots, marking them as objects; reads then returned `null`. Fixed by copying all four parallel arrays (primitive, isDouble, isObject, objects) while respecting the source slot's type. | ✅ Fixed |
| 44 | **Root-scope array globals** — Top-level array declarations (`int scale[3]`) were stored as shred-local variables, making `vm.getGlobalObject("scale")` return `null`. Fixed by routing root-scope array declarations to global storage. | ✅ Fixed |
| 45 | **`vec2` type** — Added `vec2` as a 2-element vector type alongside `vec3`/`vec4`. | ✅ Fixed |
| 46 | **Vector field accessors** — `.x/.y/.z/.w`, `.re/.im`, `.mag/.phase` now work as read and write expressions on `vec2`/`vec3`/`vec4`/`complex`/`polar` values. | ✅ Fixed |
| 47 | **`ZCR` (Zero Crossing Rate) UAna** — Frame-based zero crossing rate analyzer (0.0–1.0); configurable `frameSize`; `upchuck()` compatible. | ✅ Fixed |
| 48 | **`MFCC` UAna** — Full Mel-Frequency Cepstral Coefficients pipeline: DFT magnitude → 26-band mel triangular filterbank → log compression → DCT-II → 13 cepstral coefficients. `computeFromSpectrum()` accepts external FFT magnitudes. | ✅ Fixed |
| 49 | **FM instrument variants** — Implemented `Wurley` (Wurlitzer EP), `BeeThree` (Hammond organ), `HevyMetl` (heavy metal), `PercFlut` (percussive flute), `TubeBell` (tubular bells), `FMVoices` (singing voice). All expose `noteOn`, `noteOff`, `setFreq`. | ✅ Fixed |
| 50 | **Comparison operator overloading** — `<`, `<=`, `>`, `>=`, `==`, `!=` now dispatch to user-defined `fun int operator<(T other)` functions, completing the full operator overload suite. | ✅ Fixed |
| 51 | **Built-in `complex`/`polar` arithmetic** — `+`, `-`, `*`, `/` use proper complex number math (rectangular) and polar-native multiply/divide. `+`/`-` on polar converts via rectangular. | ✅ Fixed |
| 52 | **Built-in `vec2`/`vec3`/`vec4` arithmetic** — Element-wise `+`, `-`; scalar `*`; dot product (`vec * vec → float`). | ✅ Fixed |
| 53 | **`SFM` (Spectral Flatness Measure) UAna** — Geometric/arithmetic mean ratio of the magnitude spectrum; 0 = tonal, 1 = noisy. Chains after `FFT`. | ✅ Fixed |
| 54 | **`Kurtosis` UAna** — Normalized 4th central moment of the magnitude spectrum; high = impulsive, low = sustained. Chains after `FFT`. | ✅ Fixed |
| 55 | **`ChuckArray.sort()`** — Implemented sorting for all types (int, float, complex, polar, vec, string). | ✅ Fixed |
| 56 | **`Math.rtop()` / `ptor()`** — Added conversion between rectangular and polar forms. | ✅ Fixed |
| 57 | **Assignment Chaining (`5 => arr[0] => x`)** — Fixed array assignments to leave the object on the stack for chaining. | ✅ Fixed |
| 58 | **Math Shadowing Fix** — Ensured variables named `e` or `pi` take precedence over constants. | ✅ Fixed |
| 59 | **Duration to Float Conversion** — Implicitly convert `dur` to samples when passed to numeric method parameters. | ✅ Fixed |
| 60 | **Missing Object Instantiation** — Added `Hid` and `MidiIn` to the object factory to prevent declaration-time NPEs. | ✅ Fixed |
| 61 | **`ChuckShred.cleanup()` deadlock** — Java DSL shreds set `isDone=true` but never signalled `condition`, leaving the test thread's `resume()` blocked in `condition.await()` forever. Fixed by calling `condition.signalAll()` inside `cleanup()`. | ✅ Fixed |
| 62 | **`Delay.compute()` always returned 0** — Redundant tick-deduplication guard inside `compute()` was always true (parent `tick()` sets `lastTickTime` before calling `compute()`). Removed the guard; `Delay` now functions correctly for echo and feedback effects. | ✅ Fixed |
| 63 | **`Clarinet.compute()` silent** — `envelope.tick()` returned 0 because Envelope has no audio sources. Fixed by reading the ramp level via `envelope.getValue()` after advancing state with `tick()`. | ✅ Fixed |
| 64 | **`ChuckShred` reflection in native image** — `me.running()` / `me.numArgs()` returned `null` in GraalVM native image because `CallMethod` dispatches via `Class.getMethods()` and `ChuckShred` was not registered for reflection. Added `allPublicMethods` to `reflect-config.json`. | ✅ Fixed |
| 65 | **Math library gap-fill** — Implemented 20 missing `Math.*` functions: `sinh/cosh/tanh`, `hypot`, `fmod`, `remainder`, `min/max`, `exp2`, `nextpow2/ensurePow2`, `random2/random2f/randomf`, `gauss`, `map/map2/remap`, `clampi/clampf`, `cossim`, and 8 fast scalar approximations (`ssin` etc.). | ✅ Fixed |
| 66 | **Std library gap-fill** — Added `Std.rand()`, `Std.randf()`, `Std.system(cmd)`. | ✅ Fixed |
| 67 | **`ConsoleInput`** — New class: `readline()`, `prompt(string)`, `ready()`. Reads from `System.in`. | ✅ Fixed |
| 68 | **`KBHit`** — New class: `kbhit()`, `getchar()`. Background virtual thread queues keypresses non-blocking. | ✅ Fixed |
| 69 | **`MidiFileIn`** — New class: reads Standard MIDI files, merges/sorts all tracks, exposes `read(MidiMsg)`, `more()`, `rewind()`, `numTracks()`, `resolution()`. | ✅ Fixed |
| 70 | **`HidOut`** — New stub class: API-complete, `open()` returns 0 (native USB HID output unavailable in JVM without extra libraries). | ✅ Fixed |
| 71 | **Access Control Enforcement** — Implemented runtime enforcement of `public`, `private`, and `protected` modifiers for class fields and methods. | ✅ Fixed |
| 72 | **Doc Comment Extraction** — Added support for `/** ... */` comments; extracted and linked to classes, methods, and globals. | ✅ Fixed |
| 73 | **Real Hardware `SerialIO`** — Replaced the serial stub with a full implementation using `jSerialComm`; supports port listing, baud rates, and asynchronous data events. | ✅ Fixed |

| 74 | **Live Network Broadcasting** | ✅ Fixed — Added `Broadcaster` UGen to stream live audio over HTTP (raw WAV or compressed MP3 via ffmpeg). |
| 75 | **Multi-channel ADC** | ✅ Fixed — Upgraded `Adc` to support multi-channel hardware inputs. |
| 76 | **3D Audio & Spatialization** | ✅ Fixed — Added `Spatial3D` (binaural ITD/ILD panner) and `AmbisonicEncoder`/`AmbisonicDecoder` (B-format). |
| 77 | **Advanced LiSa** | ✅ Fixed — `LiSa` live sampling now supports 256 voices, cubic interpolation, and stereo panning. |
| 78 | **Vectorized Core Engine** | ✅ Fixed — `SinOsc`, `SawOsc`, `PulseOsc`, and all core IIR filters (`LPF`, `ResonZ`, etc.) now use SIMD vectorization and block caching. |
| 79 | **IDE: Control Surface** | ✅ Fixed — Auto-generates UI sliders for any `global` variables in your ChucK scripts. |
| 80 | **IDE: Advanced Visualizers** | ✅ Fixed — Added scrolling Waterfall Spectrogram and Stereo Phase (Vectorscope) to the UI. |
| 81 | **IDE: Preferences & Workflow** | ✅ Fixed — Dedicated Prefs tab, live audio engine restart, customizable syntax colors, drag-and-drop files, and auto-save on run. |

### New Features

#### 🌐 Network Audio & 3D Spatialization
- **`Broadcaster`**: Stream your ChucK session live over the network. Run `adc => Broadcaster b => dac; b.format("mp3"); b.start();` and tune in via VLC or browser at `http://localhost:8080/stream`.
- **`Spatial3D`**: Binaural panner for headphones, utilizing Head-Related Time/Level Differences (ITD/ILD) and distance attenuation.
- **Ambisonics**: First-order B-format spatial encoding (`AmbisonicEncoder`) and decoding (`AmbisonicDecoder`) to stereo or quadraphonic speaker layouts.

#### 🎹 Advanced Audio Analysis (UAna)
- **`RMS`**: Root Mean Square power analyzer.
- **`Centroid`**: Spectral brightness analyzer (requires upstream `FFT`).
- **`IFFT`**: Inverse Fast Fourier Transform for spectral resynthesis.
- **Phase Support**: `UAnaBlob` and `Complex` now calculate and store phase data (`pvals`).
- **Chained Analysis**: `upchuck()` now recursively triggers analysis through the UGen graph.
- **Real-time Monitoring**: Use `--verbose:2` to see real-time RMS levels in the console.

#### 🔍 Introspection & Documentation
- **Reflection Docs**: Use `Reflect.doc(obj)` to retrieve class or method documentation strings at runtime.
- **Global Documentation**: Support for `Reflect.docGlobal(name)` and `Reflect.docFunc(name)` to introspect global variables and functions.
- **Log Level Stack Traces**: Non-fatal errors and interpreter exceptions now gate full stack traces behind `--verbose:2` to keep logs clean by default.

#### 🎨 IDE Enhancements (JavaFX)
- **CLI Loading**: Pass `.ck` files as arguments to `run.sh` to open them directly in editor tabs.
- **Multi-Tab Editor**: Open and work on multiple `.ck` files simultaneously.
- **Spectrum Analyzer**: Real-time FFT magnitude visualization (Lime Green).
- **Oscilloscope**: Real-time time-domain waveform visualization (Cyan).
- **Master Controls**: Integrated Global Volume slider and live VM Logical Time display.
- **Console Clear**: Added a dedicated button to clear the output log.

#### 📦 Distribution Targets

Six pre-built distributions are produced automatically by CI and attached to every [GitHub Release](../../releases):

| File | Platform | Use case |
|------|----------|----------|
| `chuck` (Linux) | Linux x64 | Scripting, CI, headless |
| `chuck` (macOS) | macOS x64/ARM | Scripting, CI, headless |
| `chuck.exe` | Windows x64 | Scripting, CI, headless |
| `chuck-ide-linux.zip` | Linux x64 | Desktop IDE (unzip → `bin/chuck-ide`) |
| `chuck-ide-windows.zip` | Windows x64 | Desktop IDE (unzip → `chuck-ide.exe`) |
| `chuck-ide-1.0.0.dmg` | macOS | Desktop IDE (mount → drag to Applications) |

All downloads are self-contained — no Java installation required.

They can also be built locally on any platform:

| Target | Maven command | Output |
|--------|--------------|--------|
| **Native CLI** | `mvn -Pnative package -DskipTests` | `target/chuck` / `target/chuck.exe` |
| **IDE Bundle** | `mvn -Pide-bundle package -DskipTests` | `target/chuck-ide-bundle/chuck-ide/` |
| **Linux via Docker** | `docker build --output dist/linux .` | `dist/linux/chuck` |

**Native CLI** (`-Pnative`) is built with GraalVM native-image (requires `JAVA_HOME` pointing at GraalVM JDK 25). JavaFX is excluded. 108 of 130 unit tests pass natively (`mvn -Pnative -DskipNativeTests=false test`).

**IDE Bundle** (`-Pide-bundle`) uses `jpackage --type app-image` to embed a full JRE alongside the fat JAR. Includes a JEP 483 AOT cache (`chuck.aot`) for faster startup — pre-warms JavaFX and ANTLR initialization.

#### Publishing a Release

```bash
# Tag and publish (triggers CI → builds all 6 artifacts → attaches to release page)
gh release create v1.0.0 --title "ChucK-Java v1.0.0" --notes "Release notes here"
```

---

> **Full language reference:** see [LANGUAGE.md](LANGUAGE.md) — operators, types, built-ins, all UGen parameters, CLI flags, and IDE shortcuts.

---

A modern migration of the ChucK Strongly-timed Audio Programming Language to JDK 25, utilizing the latest Java platform features for real-time audio synthesis and concurrent script execution.

## ⌨️ Command Line Interface

ChucK-Java supports a full-featured CLI that mirrors the original ChucK implementation. It can be invoked via Maven, the fat JAR, or the native `chuck.exe` (built with `-Pnative`).

### Usage
```bash
# Via Maven exec plugin
mvn exec:java -Dexec.args="examples/basic/bar.ck"

# Via fat JAR
java --enable-preview --add-modules jdk.incubator.vector -jar target/chuck-java-1.0-SNAPSHOT.jar examples/basic/bar.ck

# Via native executable (no JRE needed)
chuck.exe examples/basic/bar.ck

# Launch JavaFX IDE (fat JAR or mvn javafx:run only — not available in chuck.exe)
mvn javafx:run
chuck-ide.exe          # self-contained IDE bundle built with -Pide-bundle
```

### Options
- `--halt` / `-h`: (Default Headless) Exit once all shreds finish.
- `--loop` / `-l`: Continue running headless even if no shreds are active (starts the Machine Server).
- `--silent` / `-s`: Headless mode with audio output disabled.
- `--verbose:<level>`: Set log level. Level 2 enables real-time RMS monitoring.
- `--syntax`: Check syntax of the provided files without running them.
- `--dump`: Dump virtual instructions (bytecode) to the console.
- `--srate:<N>`: Set sampling rate (default: 44100).
- `--bufsize:<N>`: Set audio buffer size (default: 512).
- `--chan:<N>`: Set number of channels (default: 2).
- `--version`: Display version information.
- `--help`: Print usage information.

### Headless Headless OTF Commands (Headless)
These commands interact with a running ChucK-Java instance (started with `--loop`).
- `+` / `--add`: Add a file as a new shred.
- `-` / `--remove`: Remove a shred by its ID.
- `=` / `--replace`: Replace an existing shred with a new file.
- `^` / `--status`: Print the current status of the VM.

### Examples
```bash
# Run a file in silent headless mode
mvn exec:java "-Dexec.args=--silent examples/basic/foo.ck"

# Start a background headless loop
mvn exec:java "-Dexec.args=--loop"

# (In another terminal) Add a file to the running headless loop
mvn exec:java "-Dexec.args=+ examples/basic/moe.ck"

# Open the IDE with multiple scripts
./run.sh examples/basic/bar.ck examples/basic/chirp2.ck
```

## 🔌 Embedding & Hosting

ChucK-Java is designed to be easily embedded as a library using the `ChuckHost` class. This provides a unified API for managing the VM, audio hardware, and global state.

### 1. Minimal Hosting (No Audio Hardware)
Ideal for pure logic or non-real-time processing.
```java
ChuckHost host = new ChuckHost(44100);
host.onPrint(System.out::println);

host.run("<<< \"Hello from Java Host!\" >>>; 1::second => now;");
host.advance(44100); // Manually drive the engine
```

### 2. Standard Real-time Hosting
Initializes the JavaSound engine automatically.
```java
ChuckHost host = new ChuckHost(44100)
    .withAudio(512, 2); // Buffer size, channels

host.add("myscript.ck");
host.setMasterGain(0.8f);
```

### 3. Custom Audio Callback
Drive ChucK from your own external audio loop (e.g. game engine).
```java
ChuckHost host = new ChuckHost(44100);
host.run("SinOsc s => dac; 440 => s.freq;");

// Inside your own audio callback:
for (int i = 0; i < buffer.length; i++) {
    host.advance(1); // Advance VM by 1 sample
    buffer[i] = host.getLastOut(0); // Pull from channel 0
}
```

### 4. Global Variable Interop
Pass data between Java and ChucK at runtime.
```java
// Java Side
host.setGlobalInt("playerHealth", 100);

// ChucK Side
// Machine.getGlobalInt("playerHealth") => int health;
```

For full examples, see the `org.chuck.examples.host` package.

## 🎸 Java Fluent DSL

ChucK-Java provides a pure-Java Fluent API that allows you to write synthesis logic with the same evocative "chucking" flow as the original language, leveraging **Scoped Values** (JEP 481) for shred-local logical time.

### Examples (`examples_dsl/`)

Ready-to-run Java DSL shreds covering a wide range of synthesis techniques:

| File | Demonstrates |
|------|-------------|
| `SineDSL.java` | Basic SinOsc — the simplest possible DSL shred |
| `FmDSL.java` | FM synthesis with ADSR envelope |
| `AdsrDSL.java` | ADSR envelope shaping random MIDI notes |
| `EnvelopeDSL.java` | Linear Envelope on white Noise |
| `LfoDSL.java` | LFO to `blackhole()`, printing values |
| `PhasorDSL.java` | Phasor modulating PulseOsc width |
| `CombDSL.java` | Feedback comb filter (pitched resonance from Noise) |
| `ChirpDSL.java` | Frequency sweep with a reusable `chirp()` method |
| `BlitDSL.java` | Band-limited impulse train + JCRev reverb |
| `HarmonicsDSL.java` | Harmonic series sweep |
| `WindDSL.java` | Noise → BiQuad resonance filter sweep |
| `OscillatorsDSL.java` | All 6 oscillator types (Sin/Saw/Tri/Pulse/Sqr + FM) |
| `Fm2DSL.java` | SinOsc FM via `sync(2)` mode |
| `Blit2DSL.java` | BLIT + ADSR articulation + JCRev |
| `LarryDSL.java` | Impulse → BiQuad frequency sweep |
| `LpfDSL.java` | Noise → LPF with sweeping cutoff |
| `BpfDSL.java` | Noise → BPF with sweeping center frequency |
| `ResonZDSL.java` | Noise → ResonZ with sweeping resonance |
| `ChorusDSL.java` | 4-voice chord (Dm7) through Chorus effect |
| `ClarDSL.java` | Clarinet physical model melody |
| `WurleyDSL.java` | Wurley FM electric piano |
| `PolyphonyDSL.java` | Concurrent shreds via `vm.spork()` |
| `DslDemo.java` | Hot-reload demo — random notes in infinite loop |

Run any example directly:
```bash
./run_dsl.sh examples_dsl/WurleyDSL.java
```

All non-infinite examples are covered by `DslExamplesTest` (20 JUnit tests).

### 1. Chaining UGens
All UGens support the `.chuck(target)` method, which connects the unit and returns the target for further chaining. Note that ChucK-style setters (like `.freq(440)`) return the value set, which breaks the chain. Configure your units before or after connecting them.

```java
SinOsc s = new SinOsc(440);
LPF f = new Lpf(1000);

// Correct: Connect then configure, or vice versa
s.chuck(f).chuck(dac());
s.freq(440);
```

### 2. Scoped Time Context
When running inside a shred (Virtual Thread), logical time is managed automatically via Scoped Values. You can use the `ChuckDSL` utility to advance time without passing around VM or Shred references.

```java
host.spork(() -> {
    SinOsc s = new SinOsc(440).chuck(host.getDac());
    
    while (true) {
        s.freq(Math.random() * 800 + 200);
        
        // Equivalent to ChucK's: 100::ms => now;
        ChuckDSL.advance(ChuckDSL.ms(100));
    }
});
```

### 3. The Java Machine (Hot-Reloading)
For a lightweight development experience, you can run the **Java Machine**, which watches your Java source files and hot-reloads them instantly without restarting the JVM.

**Start the machine:**
```bash
./run_dsl.sh --machine examples_dsl
```

**Write a hot-reloadable shred (`MyShred.java`):**
Java DSL files do not require a `package` declaration. Implementing the `Shred` interface ensures compatibility with the dynamic loader.

```java
import org.chuck.audio.*;
import org.chuck.core.Shred;
import static org.chuck.core.ChuckDSL.*;

public class MyShred implements Shred {
    @Override
    public void shred() {
        SinOsc s = new SinOsc(44100).chuck(dac());
        while (true) {
            s.freq(440 + Math.random() * 440);
            advance(second(1));
        }
    }
}
```
Every time you save the file, the `JavaMachine` (or the IDE via `Ctrl+Enter`) will automatically compile it, stop the old shred, and spork the new one.

### 4. Benefits
- **Zero Overhead**: Scoped Values are optimized for massive concurrency in Virtual Threads.
- **Fast Iteration**: Hot-reload Java logic just like `.ck` scripts.
- **Readability**: Synthesis graphs in Java now mirror the structure of `.ck` scripts.
- **Idiomatic**: Integrate ChucK logic into larger Java applications while keeping the code evocative and clean.

## 🚀 Key Modern Java Features Used

-   **Java Vector API (JDK 25)**: SIMD-accelerated Unit Generators (e.g., `SinOsc`, `Gain`) and vectorized DAC mixing for high-performance synthesis. Automatically optimizes for **Intel AVX** or **ARM Neon**.
-   **Foreign Function & Memory (FFM) API (Project Panama)**: 
    *   **Off-Heap Audio Buffers**: Zero-copy, off-heap DAC buffers via `MemorySegment` to eliminate GC jitter in the audio thread.
    *   **Native Drivers**: Low-latency bindings to native MIDI drivers (`rtmidi`) and HID Joystick drivers.
-   **Virtual Threads (Project Loom)**: Shreds (concurrent ChucK processes) are mapped 1:1 to Virtual Threads. They are cooperatively scheduled by a custom `Shreduler` to maintain ChucK’s deterministic, sample-accurate timing model.
-   **Sealed Interfaces & Records**: The Abstract Syntax Tree (AST) is built using type-safe sealed hierarchies and concise records.

## 🏗️ Architecture

### 1. Compiler Pipeline
-   **ANTLR4 Parsing**: Uses a hardened **ANTLR4** grammar for robust expression and control-flow parsing.
-   **Emitter**: Translates the AST into VM Instructions with smart stack management and support for multi-pass static resolution.

### 2. Virtual Machine (VM)
-   **Deterministic Shreduler**: Orchestrates Virtual Threads based on ChucK's logical time (`now`).
-   **Vectorized Audio Engine**: Optimized mixing and oscillation using SIMD instructions.
-   **Off-Heap Management**: Audio data is stored outside the Java heap to ensure stable, jitter-free playback.

### 3. Audio Engine
-   **Pull-based UGen Graph**: Recursive sample pulling with memoization.
-   **Real-time Output**: Bridged to system hardware via Java Sound with global master gain control.

## 🛠️ Getting Started

### Prerequisites
-   **JDK 25** (e.g., Zulu JDK 25)
-   **Maven**

### 📦 Native Dependencies (Optional - for MIDI support)
The engine will run without these, but MIDI features require the `rtmidi` library:

#### **Windows**
-   The easiest way is to use [vcpkg](https://vcpkg.io/): `vcpkg install rtmidi:x64-windows`
-   Ensure `rtmidi.dll` is in your PATH or the project root.

#### **macOS (Intel & Apple Silicon)**
-   Use Homebrew: `brew install rtmidi`

#### **Linux (Intel & ARM/Raspberry Pi)**
-   Use your package manager: `sudo apt-get install librtmidi-dev` (on Debian/Ubuntu)

### 📦 Building and Running

#### 1. Build the JAR
```bash
mvn clean package
```

#### 2. Run the IDE
You can use the provided scripts which automatically include the necessary JVM flags (`--enable-preview`, `--add-modules jdk.incubator.vector`, and `--enable-native-access`):

**Windows:**
```cmd
run.bat
```

**macOS/Linux:**
```bash
chmod +x run.sh
./run.sh
```

Alternatively, run manually with all flags:
```bash
java --enable-preview --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED -jar target/chuck-java-1.0-SNAPSHOT.jar
```

### 🎨 Launch the IDE (Maven)
```bash
mvn org.openjfx:javafx-maven-plugin:0.0.8:run
```

## 🎮 ChucK-Java IDE User Guide

### 1. Visualizers & Controls
-   **Spectrum**: High-speed frequency distribution view.
-   **Oscilloscope**: Real-time waveform monitor.
-   **Master Gain**: Controls final output volume (0.0 to 1.0).
-   **VM Time**: Shows the logical time elapsed in the engine.

### 2. Multi-Tab Editor
-   `Ctrl+N`: New Tab.
-   `Ctrl+O`: Open File (into new tab). Supports both **`.ck`** and **`.java`** files.
-   `Ctrl+S`: Save current tab.
-   `Ctrl+W`: Close current tab.
-   `Ctrl+Enter`: Spork current tab into VM.
    *   **ChucK files**: Compiled via the internal emitter.
    *   **Java files**: Dynamically compiled via the Java Compiler API and sporked as Java-based shreds.

### 3. Java DSL in the IDE
You can now write and run pure-Java ChucK logic directly in the IDE. 
1. Create or open a `.java` file.
2. Define a class with a `public void shred()` method.
3. Use `import static org.chuck.core.ChuckDSL.*;` for ChucK-like syntax.
4. Press `Ctrl+Enter` to hot-reload your Java logic into the running audio engine.

## 🎹 Implemented Unit Generators (UGens)

### Oscillators
`SinOsc`, `SawOsc`, `TriOsc`, `PulseOsc`, `SqrOsc`, `Phasor`, `Blit`, `BlitSaw` (PolyBLEP), `BlitSquare` (PolyBLEP)

### Physical Models (STK)
`Clarinet`, `Mandolin`, `Plucked`, `Rhodey`, `Wurley`, `BeeThree`, `HevyMetl`, `PercFlut`, `TubeBell`, `FMVoices`, `Bowed`, `StifKarp`, `Moog`, `Flute`, `Sitar`, `Brass`, `Saxofony`, `Shakers`

### Filters
`LPF`, `HPF`, `BPF`, `BRF` (Butterworth), `BiQuad`, `ResonZ`, `OnePole`, `OneZero`, `TwoPole`, `TwoZero`, `PoleZero`, `AllPass`

### Effects & Delays
`Chorus`, `Echo`, `Delay`, `DelayL`, `DelayA`, `JCRev`, `NRev`, `PRCRev`, `GVerb`, `PitShift`

### Envelopes & Control
`ADSR`, `Envelope`, `Gain`, `GainDB`, `Step`, `Impulse`, `Noise`, `CNoise`

### Utilities
`Pan2`, `SndBuf`, `SndBuf2`, `WvOut`, `Blackhole`, `LiSa`

### Analysis (UAna)
`FFT`, `IFFT`, `RMS`, `Centroid`, `ZCR`, `MFCC`, `SFM`, `Kurtosis`, `UAnaBlob`
