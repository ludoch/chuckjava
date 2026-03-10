# ChucK-Java (JDK 25 Migration)

## Audit & Fix Progress (2026-03-10)

An independent audit of the Gemini-generated port was performed by Claude Code, followed by a full fix pass.
All 25 unit tests pass after the fixes.

### Bugs Fixed

| # | Bug | Status |
|---|-----|--------|
| 1 | **Concurrency model** — re-audited; `yield()` correctly uses `ReentrantLock` + `condition.await()` to suspend Virtual Threads. No fix needed. | ✅ Verified correct |
| 2 | **Missing `SetMemberIntByName` instruction** — class was referenced in the emitter but never created. | ✅ Fixed |
| 3 | **`++`/`--` operators not in lexer** — for-loop update expressions like `i + 1 => i` worked, but `i++` crashed. Added `PLUS_PLUS`, `MINUS_MINUS`, `BANG`, `AND_AND`, `OR_OR`, `PERCENT`, `ASSIGN` tokens. | ✅ Fixed |
| 4 | **`SawOsc.setFrequency` naming mismatch** — the formula was correct but the method was named `setFrequency` instead of `setFreq`, breaking the reflection-based setter. Added `setFreq` alias. | ✅ Fixed |
| 5 | **String literals not lexed** — quoted strings `"hello"` were not tokenized. Added `lexString()` with escape sequence support. | ✅ Fixed |
| 6 | **No plain `=` assignment** — only `=>` worked. Added `ASSIGN` token and emitter support for `x = value`. | ✅ Fixed |
| 7 | **No unary operators** — `-x`, `!b` had no AST/emitter support. Added `NegateAny`, `LogicalNot` instructions. | ✅ Fixed |
| 8 | **`return` statement not emitted** — `ReturnStmt` existed in AST but was ignored by emitter. | ✅ Fixed |
| 9 | **Limited UGen instantiation** — `InstantiateAndSetGlobal` only handled 8 UGen types. Added all 22 implemented UGens. | ✅ Fixed |
| 10 | **No generic method calls** — `adsr.keyOn()`, `adsr.set(...)` etc. had no emission path. Added `CallMethod` instruction using reflection. | ✅ Fixed |
| 11 | **Missing math functions** — `Math.sin`, `Math.cos`, `Math.sqrt`, etc. Added `MathFunc` instruction. Added `Std.ftom`. | ✅ Fixed |
| 12 | **Missing logical/comparison operators** — `&&`, `\|\|`, `%`, `!=` not emitted. Added `LogicalAnd`, `LogicalOr`, `ModuloAny`, `NotEqualsAny`. | ✅ Fixed |
| 13 | **Edit menu items had no handlers** — Undo/Redo/Cut/Copy/Paste items existed but did nothing. | ✅ Fixed |
| 14 | **No keyboard shortcuts** — Ctrl+Enter (Add Shred), Ctrl+Shift+Enter (Replace), Ctrl+. (Remove), Ctrl+/ (Stop All) missing. | ✅ Fixed |

### IDE Improvements

- **Syntax highlighting** — replaced plain `TextArea` with RichTextFX `CodeArea` (was already in pom.xml but unused). Keywords, types, builtins, strings, numbers, comments, and `=>` operators are colour-coded.
- **Line numbers** — displayed in gutter via `LineNumberFactory`.
- **Error line highlighting** — compiler errors now scroll the editor to the offending line and select it.
- **Keyboard shortcuts** — all standard shortcuts now work (Ctrl+Enter, Ctrl+S, Ctrl+Z, Ctrl+C/V/X, etc.).
- **Save As** added to file menu.

### Remaining Known Gaps

- **MIDI** — `ChuckMidi` is a stub; FFM binding to `librtmidi` needs native library setup.
- **FFT / UAna** — spectral analysis classes exist but contain no real implementation.
- **`class` definitions** — user-defined classes are not supported in the parser/emitter.
- **Audio input (ADC)** — `ChuckAudio` is output-only; no microphone input.
- **Anti-aliasing** — oscillators generate aliased harmonics above Nyquist.



A modern migration of the ChucK Strongly-timed Audio Programming Language to JDK 25, utilizing the latest Java platform features for real-time audio synthesis and concurrent script execution.

## 🚀 Key Modern Java Features Used

-   **Virtual Threads (Project Loom)**: Shreds (concurrent ChucK processes) are mapped 1:1 to Virtual Threads. They are cooperatively scheduled by a custom `Shreduler` to maintain ChucK’s deterministic, sample-accurate timing model.
-   **Vector API (SIMD)**: High-performance block processing in Unit Generators (UGens) like `Gain`. It uses `jdk.incubator.vector` to process audio samples in parallel, significantly reducing CPU overhead.
-   **Foreign Function & Memory (FFM) API (Project Panama)**: Manages off-heap memory (`Arena`, `MemorySegment`) for audio buffers to avoid GC pauses. Provides the infrastructure for binding to native MIDI drivers (RtMidi) and audio APIs.
-   **Sealed Interfaces & Records**: The Abstract Syntax Tree (AST) is built using type-safe sealed hierarchies and concise records, making the compiler frontend clean and maintainable.
-   **Pattern Matching**: Extensive use of pattern matching for `switch` and `instanceof` throughout the compiler and VM instruction set.

## 🏗️ Architecture

### 1. Compiler Pipeline
-   **Lexer**: Hand-written scanner for ChucK syntax, including `=>`, `@=>`, `::`, and `~`.
-   **Parser**: Recursive descent parser that builds a modern AST. Supports inline declarations, loops, and function definitions.
-   **Emitter**: Translates the AST into a sequence of executable VM Instructions with smart stack management.

### 2. Virtual Machine (VM)
-   **Deterministic Shreduler**: Orchestrates Virtual Threads based on ChucK's logical time (`now`).
-   **ChuckStack**: A memory-safe, high-performance stack for primitives and object references.
-   **Global Environment**: Thread-safe shared state for global variables and objects.

### 3. Audio Engine
-   **Pull-based UGen Graph**: Recursive sample pulling with memoization to ensure efficiency and accuracy.
-   **Real-time Output**: Bridged to system hardware via Java Sound.

## 🛠️ Getting Started

### Prerequisites
-   **JDK 25** (e.g., Zulu JDK 25)
-   **Maven**

### Building and Testing
```bash
mvn clean test
```

### Running a ChucK Script
```bash
mvn compile exec:java -Dexec.args="path/to/script.ck"
```

### 🎨 ChucK-Java IDE (Desktop)
The project includes a modern JavaFX-based IDE that replicates the core workflow of the Stanford WebChucK environment.

**Features:**
-   **Integrated Editor**: Write and edit ChucK code with monospaced font support.
-   **Shred Manager**: Add multiple concurrent shreds, monitor their status, and remove them individually.
-   **Live Console**: View compilation errors and runtime output in real-time.
-   **Audio Control**: Immediate real-time feedback through system speakers.

**Launch the IDE:**
```bash
mvn org.openjfx:javafx-maven-plugin:0.0.8:run
```

## 🎮 ChucK-Java IDE User Guide

The ChucK-Java Professional IDE provides a high-performance environment for real-time audio coding.

### 1. Tool Bar Actions
-   **➕ Add Shred**: Compiles the current editor code and spawns a new concurrent process (shred) in the VM.
-   **🔄 Replace Last**: Stops the most recently added shred and immediately replaces it with the current editor code. Ideal for rapid iteration.
-   **➖ Remove Last**: Stops and removes the most recently added shred.
-   **🛑 Stop All**: Immediately stops all running shreds and resets the Virtual Machine state.
-   **⏺ Record**: Toggles real-time session recording. Audio is saved as a high-quality `session.wav` file in the project directory.

### 2. Menu System
-   **File**:
    -   `New`: Clears the editor for a new script.
    -   `Open...`: Open an existing `.ck` file from your disk.
    -   `Save`: Save the current script.
    -   `Exit`: Safely shuts down the audio engine and exits the application.
-   **Edit**: standard `Undo`, `Redo`, `Cut`, `Copy`, and `Paste` operations.
-   **Examples**: A dynamic, multi-level menu containing the standard ChucK example library. Click any example to load it into the editor.
-   **Help**: Information about the ChucK-Java migration and system status.

### 3. Workspace Panels
-   **Project Explorer (Left)**: Navigate your local files. Double-click any `.ck` file to load it.
-   **Code Editor (Center)**: Full-featured monospaced editor with support for ChucK syntax and comments.
-   **Active Shreds (Right)**: Real-time list of all running processes. Select a shred and click **Stop Selected** to terminate a specific process.
-   **Console & Status (Bottom)**: 
    -   The **Console** shows compilation errors, VM messages, and `print` output.
    -   The **Status Bar** indicates the current VM load and engine state.

## 🎹 Implemented Unit Generators (UGens)

### Oscillators
-   `SinOsc`: Sine wave.
-   `SawOsc`: Sawtooth wave.
-   `TriOsc`: Triangle wave.
-   `PulseOsc`, `SqrOsc`: Pulse and Square waves.
-   `Phasor`: Linear ramp [0, 1].

### Filters & Effects
-   `LPF`, `Lpf`: Low-pass filters.
-   `OnePole`, `OneZero`: Foundational filters.
-   `ResonZ`: High-quality resonance filter.
-   `Chorus`: Multi-voice modulation effect.
-   `Echo`: Delay with feedback.
-   `JCRev`: Reverb (John Chowning model).
-   `AllPass`, `Comb`: Reverb building blocks.

### Physical Models (STK)
-   `Clarinet`: Digital waveguide woodwind.
-   `Mandolin`: Plucked string model.
-   `Plucked`: Karplus-Strong string synthesis.
-   `Rhodey`: FM-based Fender Rhodes piano.

### Analysis (UAna)
-   `FFT`: Fast Fourier Transform analyzer.
-   `UAnaBlob`: Spectral data container.

### Stereo & Control
-   `Pan2`: Stereo panning (Constant Power/Linear).
-   `ADSR`, `Envelope`: Amplitude control.
-   `Noise`: White noise generator.
-   `SndBuf`: Sample playback with linear interpolation.
-   `Gain`: SIMD-optimized volume control.
-   `Step`, `Impulse`, `Blackhole`: Control signal utilities.
