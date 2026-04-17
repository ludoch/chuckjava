# ChucK-Java (JDK 25 Migration)

## Progress Update (2026-04-16)

### Integration Test Coverage (Latest)

| Suite | Total | Passed | Failed | Timed Out | Notes |
|-------|-------|--------|--------|-----------|-------|
| **Total** | **1126** | **1025 (91%)** | **62** | **39** | Fixed float, complex, polar formatting. |

*Note: Remaining failures (62) are mostly minor output mismatches or edge cases in static field handling (e.g., 116.ck).*

### Roadmap Progress
- **✅ Negative Testing:** 100% resolution of "ERROR NOT CAUGHT" cases.
- **✅ Smoke Testing:** Infinite loops are handled correctly.
- **✅ Virtual Filesystem:** Global search paths and `special:` aliases work.
- **✅ Output Normalization:** Fixed float precision and complex/polar formatting to match reference ChucK.
- **🔄 Static Field Hardening:** (Next) Resolving persistent edge cases in static array initialization and access.

### Recent Enhancements (Pro MIDI & Audio)

| Feature | Description | Status |
|---|-----|--------|
| **Native RtMidiJava** | Zero-setup, zero-GC native drivers via FFM API. | ✅ New |
| **MidiPoly** | High-level automatic voice management for polyphonic MIDI instruments. | ✅ New |
| **MIDI Learn** | Persistent MIDI mapping for global variables (saves to preferences). | ✅ Enhanced |
| **MIDI Recorder** | One-click "REC" button to capture all MIDI to timestamped files. | ✅ New |
| **MIDI Map Manager** | Central dashboard for all persistent variable bindings. | ✅ New |
| **MIDI Thru Patchbay** | Route any physical input to any output at the IDE level. | ✅ New |
| **MIDI Master Clock** | IDE acts as sync master (BPM + Start/Stop) for external gear. | ✅ New |
| **MIDI Monitor** | Real-time diagnostics, CC Grid, and status bar activity tracking. | ✅ Enhanced |
| **MidiPoly** | High-level voices with customizable **Velocity Curves**. | ✅ Enhanced |
| **MidiPlayer** | High-level MIDI sequencer. Connects directly to `MidiPoly` (`file => player => poly;`). | ✅ New |
| **Device Probing** | Robust audio/MIDI device discovery with native port names. | ✅ Fixed |
| **Precision Time** | Sample-accurate MIDI timestamps (`msg.when`) for high-jitter protection. | ✅ Fixed |

### New Features

#### 🎹 Advanced MIDI & Musician Features
- **Native RtMidiJava Support**: Ultra-low latency MIDI drivers via pure Java FFM (Panama). No native libraries to compile or install. Supports Windows (WinMM), macOS (CoreMIDI), and Linux (ALSA/JACK).
- **Zero-GC Performance**: Optimized MIDI hot-path ensures no Java heap allocations during real-time MIDI processing.
- **Native Port Sharing**: Run multiple `MidiIn` and `MidiOut` objects on the same physical hardware port safely (prevents "Device Busy" errors).
- **`MidiPoly`**: Automatic high-level voice management. Map MIDI to STK instruments or custom UGens with zero manual sporking. Supports instrument selection, voice stealing, polyphonic pools, and custom microtonal tuning maps (`tuning(float[])`). Supports direct connections: `MidiIn min => MidiPoly poly;`.
- **`MidiPlayer`**: A high-level MIDI sequencer. Load a `MidiFileIn`, connect it to an instrument (`player => poly`), and call `player.play()` for automatic, sample-accurate playback.
- **`MidiMpe`**: Full MIDI Polyphonic Expression (MPE) support for per-note pitch bend and channel pressure.
- **`MidiClock`**: Dedicated class for tracking MIDI 24ppq clock, start, stop, and continue messages, allowing tight transport sync (`onBeat()`, `onSixteenth()`, etc.).
- **IDE MIDI Monitor**: Diagnostics tab with real-time Message Log, 128-knob CC Grid, and a Map Manager dashboard.
- **Status Bar MIDI Tools**: Quick-access port selection, Thru Patchbay routing, Master Sync (BPM/Clock), a one-click **REC** button, and an "All Notes Off" Panic button (`!`).
- **Persistent MIDI Learn**: Bind physical knobs to variables; mappings are automatically saved and restored across sessions.
- **IDE MIDI Visualizer**: Real-time 88-key piano keyboard that highlights in Orange for physical input and Light Blue for generative code output.
- **MidiFileOut**: Record generative MIDI performances directly to standard `.mid` files. Supports **SMF Format 1** (multi-track), Tempo Maps, Section Markers, and 14-bit NRPN high-resolution sweeps.
- **MidiFileIn**: Upgraded to support multi-track reading, file metadata (`bpm()`, `tpq()`), and automatic delta-time calculation (`msg.when`).
- **Precision Timestamps**: Incoming messages now include `msg.when` (seconds) from the native driver for jitter-free alignment.
- **Visual Grid Sequencer**: TR-808 style 16-step drum sequencer tab in the IDE. Features 8-track (Kick, Snare, etc.) interactive grid with real-time bi-directional sync to the ChucK engine.

#### ⚡ Audio Fidelity & Performance
- **Idle CPU Optimization**: The audio engine automatically detects when the VM is idle (no active shreds). It applies a smooth global fade-out and puts the processing loop into a low-power sleep mode, reducing "doing nothing" CPU usage to near 0%.
- **Pop/Click Prevention**: `SndBuf` and `SndBuf2` now feature an analytical 1ms fade-out when reaching the end of a sample, preventing abrupt DC-offset jumps that cause speaker pops.
- **Project Loom Concurrency**: Uses Java Virtual Threads for shreds, allowing thousands of simultaneous oscillators and logic processes with minimal memory overhead.
- **SIMD Audio Kernel**: Optimized Vector API paths for common UGen operations, utilizing modern CPU instructions for high-density synthesis.

#### 🔍 Introspection & Documentation
- **Reflection Docs**: Use `Reflect.doc(obj)` to retrieve class or method documentation strings at runtime.
- **IDE Hover Docs**: Hover over any keyword, UGen, or method in the editor to see its documentation and signature.

#### 🎨 IDE Enhancements (JavaFX)
- **MIDI Keyboard Monitor**: Visual feedback for incoming MIDI notes and controllers in the bottom panel.
- **MIDI Settings Tab**: Configure preferred APIs (e.g. JACK vs ALSA) and port filters.
- **Multi-Tab Editor**: Open and work on multiple `.ck` and `.java` files simultaneously.
- **Master Controls**: Integrated Global Volume slider, live VU meters, and VM Logical Time display.

---

## ⌨️ Command Line Interface

ChucK-Java supports a full-featured CLI that mirrors the original ChucK implementation, including full support for **on-the-fly (OTF)** programming.

### Usage
```bash
# Via fat JAR
java --enable-native-access=ALL-UNNAMED -jar target/chuck-java.jar examples/basic/bar.ck

# Launch JavaFX IDE
mvn javafx:run
```

---

## 🛠️ Getting Started

### Prerequisites
-   **JDK 25** (e.g., Zulu JDK 25)
-   **Maven**

### 📦 MIDI Support
MIDI support is built-in via the **RtMidiJava** library. There are no native dependencies to install manually.
Ensure you run with `--enable-native-access=ALL-UNNAMED`.

---

## 🎹 Implemented Unit Generators (UGens)

### Oscillators
`SinOsc`, `SawOsc`, `TriOsc`, `PulseOsc`, `SqrOsc`, `Phasor`, `Blit`, `BlitSaw`, `BlitSquare`

### Physical Models (STK)
`Clarinet`, `Mandolin`, `Plucked`, `Rhodey`, `Wurley`, `BeeThree`, `HevyMetl`, `PercFlut`, `TubeBell`, `FMVoices`, `Bowed`, `StifKarp`, `Moog`, `Flute`, `Sitar`, `Brass`, `Saxofony`, `Shakers`, `ModalBar`, `VoicForm`

### Filters
`LPF`, `HPF`, `BPF`, `BRF`, `BiQuad`, `ResonZ`, `OnePole`, `OneZero`, `TwoPole`, `TwoZero`, `PoleZero`, `AllPass`

### MIDI & Control
`MidiPoly` (High-level Polyphony), `ADSR`, `Envelope`, `Gain`, `Step`, `Impulse`, `Noise`, `CNoise`

### Effects & Delays
`Chorus`, `Echo`, `Delay`, `DelayL`, `DelayA`, `JCRev`, `NRev`, `PRCRev`, `GVerb`, `PitShift`

### Utilities
`Pan2`, `SndBuf`, `SndBuf2`, `WvOut`, `Blackhole`, `LiSa`

### Analysis (UAna)
`FFT`, `IFFT`, `RMS`, `Centroid`, `ZCR`, `MFCC`, `SFM`, `Kurtosis`
