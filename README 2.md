# ChucK-Java (JDK 25 Migration)

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
