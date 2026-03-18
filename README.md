# ChucK-Java (JDK 25 Migration)

## Progress Update (2026-03-18)

### Integration Test Coverage

| Suite | Tests | Passing | Notes |
|-------|-------|---------|-------|
| **01-Basic** | 242 | 230 | Core language, math, control flow, arrays, classes, operator overloading |
| **02-UGens** | 75 | 75 ✅ | All oscillators, filters, effects, physical models |
| **03-Modules** | 14 | 14 ✅ | FileIO (text + binary), OSC networking, seek |
| **04-Stress** | 15 | 8 | Deep recursion, large arrays, concurrency edge cases |
| **05-Global** | 52 | 51 | 77.ck fails: `me.dir(3)` requires 3-level path depth (test dir only 2 levels deep) |
| **06-Errors** | 109 | 42 | Error handling and type-checking edge cases |
| **07-Imports** | 9 | 9 ✅ | `#include` / machine imports |
| **Total** | **516** | **429 (83%)** | |

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

### New Features

#### 🎹 Advanced Audio Analysis (UAna)
- **`RMS`**: Root Mean Square power analyzer.
- **`Centroid`**: Spectral brightness analyzer (requires upstream `FFT`).
- **`IFFT`**: Inverse Fast Fourier Transform for spectral resynthesis.
- **Phase Support**: `UAnaBlob` and `Complex` now calculate and store phase data (`pvals`).
- **Chained Analysis**: `upchuck()` now recursively triggers analysis through the UGen graph.
- **Real-time Monitoring**: Use `--verbose:2` to see real-time RMS levels in the console.

#### 🎨 IDE Enhancements (JavaFX)
- **CLI Loading**: Pass `.ck` files as arguments to `run.sh` to open them directly in editor tabs.
- **Multi-Tab Editor**: Open and work on multiple `.ck` files simultaneously.
- **Spectrum Analyzer**: Real-time FFT magnitude visualization (Lime Green).
- **Oscilloscope**: Real-time time-domain waveform visualization (Cyan).
- **Master Controls**: Integrated Global Volume slider and live VM Logical Time display.
- **Console Clear**: Added a dedicated button to clear the output log.

---

> **Full language reference:** see [LANGUAGE.md](LANGUAGE.md) — operators, types, built-ins, all UGen parameters, CLI flags, and IDE shortcuts.

---

A modern migration of the ChucK Strongly-timed Audio Programming Language to JDK 25, utilizing the latest Java platform features for real-time audio synthesis and concurrent script execution.

## ⌨️ Command Line Interface

ChucK-Java now supports a full-featured CLI that mirrors the original ChucK implementation.

### Usage
```bash
# Launch the IDE with files loaded
./run.sh examples/basic/bar.ck examples/basic/chirp.ck

# Run in headless mode with real-time RMS monitoring
./run.sh --verbose:2 examples/basic/bar.ck
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
- `--antlr`: Use the ANTLR4-based parser instead of the handwritten one.
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

## 🚀 Key Modern Java Features Used

-   **Virtual Threads (Project Loom)**: Shreds (concurrent ChucK processes) are mapped 1:1 to Virtual Threads. They are cooperatively scheduled by a custom `Shreduler` to maintain ChucK’s deterministic, sample-accurate timing model.
-   **Vector API (SIMD)**: High-performance block processing in Unit Generators (UGens) like `Gain`. It uses `jdk.incubator.vector` to process audio samples in parallel. Automatically optimizes for **Intel AVX** or **ARM Neon**.
-   **Foreign Function & Memory (FFM) API (Project Panama)**: Manages off-heap memory (`Arena`, `MemorySegment`) for audio buffers to avoid GC pauses. Provides the infrastructure for binding to native MIDI drivers (RtMidi) and HID Joystick drivers.
-   **Sealed Interfaces & Records**: The Abstract Syntax Tree (AST) is built using type-safe sealed hierarchies and concise records.

## 🏗️ Architecture

### 1. Compiler Pipeline
-   **Lexer**: Hand-written scanner for ChucK syntax, including `=>`, `@=>`, `::`, `<<< >>>`, and `~`.
-   **Parser**: Recursive descent parser that builds a modern AST. Supports tabs, loops, and print statements.
-   **Emitter**: Translates the AST into VM Instructions with smart stack management.

### 2. Virtual Machine (VM)
-   **Deterministic Shreduler**: Orchestrates Virtual Threads based on ChucK's logical time (`now`).
-   **ChuckStack**: A memory-safe stack for primitives and objects. Supports dynamic type guessing for printing.
-   **Global Environment**: Thread-safe shared state for global variables and objects.

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
-   `Ctrl+O`: Open File (into new tab).
-   `Ctrl+S`: Save current tab.
-   `Ctrl+W`: Close current tab.
-   `Ctrl+Enter`: Spork current tab into VM.

## 🎹 Implemented Unit Generators (UGens)

### Oscillators & Physical Models
-   `SinOsc`, `SawOsc`, `TriOsc`, `PulseOsc`, `SqrOsc`, `Phasor`.
-   `Clarinet`, `Mandolin`, `Plucked`, `Rhodey`, `Bowed`, `StifKarp`, `Moog`, `Flute`, `Sitar`.

### Analysis (UAna)
-   `FFT`: Fast Fourier Transform.
-   `IFFT`: Inverse FFT (resynthesis).
-   `RMS`: Power analyzer.
-   `Centroid`: Spectral brightness.
-   `UAnaBlob`: Data container for magnitude and phase.

### Filters, Effects & Utilities
-   `Lpf`, `ResonZ`, `Chorus`, `Echo`, `JCRev`, `OnePole`, `OneZero`, `PitShift`.
-   `Pan2`, `ADSR`, `Envelope`, `SndBuf`, `Gain`, `Noise`, `Step`, `Impulse`, `Blackhole`.
