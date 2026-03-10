# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What This Is

A Java implementation of the [ChucK](https://chuck.stanford.edu/) strongly-timed music programming language. It compiles `.ck` source files and executes them with real-time audio via a virtual machine. Built with JDK 25 and uses Java Virtual Threads for concurrency.

## Build & Run Commands

```bash
# Build
mvn compile

# Run all tests
mvn test

# Run a single test class
mvn test -Dtest=ChuckCompilerTest

# Run the application (pass a .ck file)
mvn exec:java -Dexec.args="path/to/script.ck"

# Launch the JavaFX IDE
mvn javafx:run

# Package
mvn package
```

**Important:** Preview features (`--enable-preview`) and `jdk.incubator.vector` are required — the Maven plugins are already configured for this, but if running `java` directly you must pass these flags.

## Architecture

The pipeline: `.ck` source → `ChuckLexer` → tokens → `ChuckParser` → AST → `ChuckEmitter` → `ChuckCode` (bytecode) → `ChuckVM` executes via `ChuckShred`s.

### Packages

- **`org.chuck`** — Entry point (`Main.java`): reads a `.ck` file, wires together the compile pipeline, and runs it.
- **`org.chuck.compiler`** — Compiler pipeline:
  - `ChuckLexer` — tokenizes ChucK source
  - `ChuckParser` — recursive descent parser producing a `ChuckAST`
  - `ChuckEmitter` — walks the AST and emits `ChuckInstr` instances into a `ChuckCode` object
- **`org.chuck.core`** — VM runtime:
  - `ChuckVM` — manages the shreduler (priority queue by wake time), global variables (`dac`, `blackhole`, ints, objects), and drives the UGen audio graph sample-by-sample
  - `ChuckShred` — a concurrent execution unit backed by a Java Virtual Thread; uses `ReentrantLock`/`Condition` to yield/resume at sample-accurate times
  - `ChuckCode` / `ChuckInstr` — bytecode container and instruction interface
  - `ChuckStack` — register (`reg`) and memory (`mem`) stacks per shred
  - Individual instruction classes (`PushInt`, `PushFloat`, `AdvanceTime`, `ChuckTo`, `CallFunc`, etc.)
- **`org.chuck.audio`** — Unit Generator (UGen) graph:
  - `ChuckUGen` (abstract base) — pull-based tick mechanism; `tick(long systemTime)` is idempotent per sample
  - Concrete UGens: oscillators (`SinOsc`, `SawOsc`, `TriOsc`, `SqrOsc`, `PulseOsc`, `Phasor`), effects (`Echo`, `Delay`, `Chorus`, `JCRev`, `ResonZ`, `AllPass`), envelopes (`Adsr`, `Envelope`), instruments (`Clarinet`, `Mandolin`, `Plucked`, `Rhodey`), utilities (`Gain`, `Pan2`, `Noise`, `Impulse`, `Step`, `Blackhole`, `SndBuf`, `WvOut`)
  - `ChuckAudio` — Java `SourceDataLine` audio output; calls `vm.advanceTime(bufferSize)` per buffer
- **`org.chuck.ide`** — `ChuckIDE`: a JavaFX-based code editor with syntax highlighting (RichTextFX)
- **`org.chuck.midi`** — `MidiIn`/`ChuckMidi` for MIDI input support

### Recently Fixed Issues (2026-03-10)
- `SetMemberIntByName` was missing — now created, uses reflection + `setData` fallback
- `++`/`--`, `!`, `&&`, `||`, `%`, `=` (ASSIGN), string literals added to lexer+parser
- Emitter now handles: `UnaryExp`, `StringExp`, `ReturnStmt`, `ASSIGN`, `CallMethod`, `MathFunc`, `StdFtom`, all 22 UGen types in `InstantiateAndSetGlobal`
- IDE: replaced `TextArea` with RichTextFX `CodeArea` (syntax highlighting, line numbers, error line highlighting)
- Concurrency model was audited and confirmed correct — Virtual Thread yield works via `ReentrantLock`

### Key Design Patterns

**Time model:** `ChuckVM.advanceTime(n)` advances the logical clock sample-by-sample. Each sample, the shreduler wakes any `ChuckShred` whose `wakeTime <= now`, then ticks the entire UGen graph. Shreds yield by calling `shred.yield(samples)`, which suspends their Virtual Thread until the VM advances to their wake time.

**UGen graph:** Pull-based. The DAC channels are the roots; `tick(systemTime)` recurses through `sources` lists. Each UGen is ticked at most once per sample (guarded by `lastTickTime`). Adding a connection (`ChuckTo` instruction) calls `src.chuckTo(dest)` which appends to `dest.sources`.

**ChucK `=>` operator:** The `CHUCK` operator in ChuckAST is the central wiring mechanism — it handles both UGen connections and value assignments depending on operand types.
