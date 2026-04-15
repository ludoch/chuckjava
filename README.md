# ChucK-Java (JDK 25 Migration)

## Progress Update (2026-04-14)

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
| **MidiNativeTest** | 7 | 7 ✅ | Native RtMidi bindings, port discovery, sysex, and timestamps |
| **Total** | **524** | **441 (84%)** | |

### Recent Enhancements (Pro MIDI & Audio)

| Feature | Description | Status |
|---|-----|--------|
| **Native RtMidi** | Low-latency native drivers (ASIO, CoreMIDI, ALSA, JACK) via Project Panama. | ✅ Fixed |
| **MidiPoly** | High-level automatic voice management for polyphonic MIDI instruments. | ✅ New |
| **MIDI Learn** | Persistent MIDI mapping for global variables (saves to preferences). | ✅ Enhanced |
| **MIDI Recorder** | One-click "REC" button to capture all MIDI to timestamped files. | ✅ New |
| **MIDI Monitor** | Real-time diagnostic tab and status bar activity tracking. | ✅ New |
| **MidiPoly** | High-level voices with customizable **Velocity Curves**. | ✅ Enhanced |
| **MidiPlayer** | High-level MIDI sequencer. Connects directly to `MidiPoly` (`file => player => poly;`). | ✅ New |
| **Device Probing** | Robust audio/MIDI device discovery with native port names. | ✅ Fixed |
| **Precision Time** | Sample-accurate MIDI timestamps (`msg.when`) for high-jitter protection. | ✅ Fixed |

### New Features

#### 🎹 Advanced MIDI & Musician Features
- **Native RtMidi Support**: Low-latency native MIDI drivers via Project Panama (ASIO, CoreMIDI, ALSA, JACK). Replaces the 1ms polling loop with a true native callback model.
- **Native Port Sharing**: Run multiple `MidiIn` and `MidiOut` objects on the same physical hardware port safely (prevents "Device Busy" errors).
- **`MidiPoly`**: Automatic high-level voice management. Map MIDI to STK instruments or custom UGens with zero manual sporking. Supports instrument selection, voice stealing, polyphonic pools, and custom microtonal tuning maps (`tuning(float[])`). Supports direct connections: `MidiIn min => MidiPoly poly;`.
- **`MidiPlayer`**: A high-level MIDI sequencer. Load a `MidiFileIn`, connect it to an instrument (`player => poly`), and call `player.play()` for automatic, sample-accurate playback.
- **`MidiMpe`**: Full MIDI Polyphonic Expression (MPE) support for per-note pitch bend and channel pressure.
- **`MidiClock`**: Dedicated class for tracking MIDI 24ppq clock, start, stop, and continue messages, allowing tight transport sync (`onBeat()`, `onSixteenth()`, etc.).
- **IDE MIDI Monitor**: Dedicated tab for real-time logging of incoming MIDI messages with high-resolution timestamps.
- **Status Bar MIDI Tools**: Quick-access port selection menu, a one-click **MIDI Recorder (REC)**, and a flashing activity indicator.
- **Persistent MIDI Learn**: Bind physical knobs to variables; mappings are automatically saved and restored across sessions.
- **IDE MIDI Visualizer**: Real-time 88-key piano keyboard showing Note-On/Off, CC, and Pitch Bend movements.
- **MidiFileOut**: Record generative MIDI performances directly to standard `.mid` files. Supports **SMF Format 1** (multi-track), Tempo Maps, Section Markers, and 14-bit NRPN high-resolution sweeps.
- **MidiFileIn**: Upgraded to support multi-track reading, file metadata (`bpm()`, `tpq()`), and automatic delta-time calculation (`msg.when`).
- **Precision Timestamps**: Incoming messages now include `msg.when` (seconds) from the native driver for jitter-free alignment.

#### 🌐 Network Audio & 3D Spatialization
- **`Broadcaster`**: Stream your ChucK session live over the network. Run `adc => Broadcaster b => dac; b.format("mp3"); b.start();` and tune in via VLC or browser.
- **`Spatial3D`**: Binaural panner for headphones, utilizing Head-Related Time/Level Differences (ITD/ILD).
- **Ambisonics**: First-order B-format spatial encoding (`AmbisonicEncoder`) and decoding (`AmbisonicDecoder`).

#### 🎹 Advanced Audio Analysis (UAna)
- **`RMS`**: Root Mean Square power analyzer.
- **`Centroid`**: Spectral brightness analyzer (requires upstream `FFT`).
- **`IFFT`**: Inverse Fast Fourier Transform for spectral resynthesis.
- **Phase Support**: `UAnaBlob` and `Complex` now calculate and store phase data (`pvals`).

#### 🔍 Introspection & Documentation
- **Reflection Docs**: Use `Reflect.doc(obj)` to retrieve class or method documentation strings at runtime.
- **IDE Hover Docs**: Hover over any keyword, UGen, or method in the editor to see its documentation and signature.

#### 🎨 IDE Enhancements (JavaFX)
- **MIDI Keyboard Monitor**: Visual feedback for incoming MIDI notes and controllers in the bottom panel.
- **MIDI Settings Tab**: Configure native library paths, preferred APIs (e.g. JACK vs ALSA), and port filters.
- **Multi-Tab Editor**: Open and work on multiple `.ck` and `.java` files simultaneously.
- **Master Controls**: Integrated Global Volume slider, live VU meters, and VM Logical Time display.

---

> **MIDI Guide for Musicians:** see [MIDI_GUIDE.md](MIDI_GUIDE.md) for a centralized guide on native drivers, polyphony, and MIDI Learn.

---

## ⌨️ Command Line Interface

ChucK-Java supports a full-featured CLI that mirrors the original ChucK implementation.

### Usage
```bash
# Via native executable (no JRE needed)
chuck.exe examples/basic/bar.ck

# Launch JavaFX IDE
mvn javafx:run
chuck-ide.exe          # self-contained IDE bundle
```

### Options
- `--halt` / `-h`: (Default Headless) Exit once all shreds finish.
- `--loop` / `-l`: Continue running headless (starts the Machine Server).
- `--silent` / `-s`: Headless mode with audio output disabled.
- `--verbose:<level>`: Set log level. Level 2 enables real-time RMS monitoring.
- `--srate:<N>`: Set sampling rate (default: 44100).
- `--bufsize:<N>`: Set audio buffer size (default: 512).

---

## 🛠️ Getting Started

### Prerequisites
-   **JDK 25** (e.g., Zulu JDK 25)
-   **Maven**

### 📦 Native Dependencies (Optional - for MIDI support)
The engine runs without these using JavaSound fallback, but for low-latency and virtual ports, the `rtmidi` library is recommended:

#### **Windows**
-   Requires `rtmidi.dll`.
-   **Where to find it?** Search your computer for music apps like **Bespoke Synth**, **VCV Rack**, or **SuperCollider**. Copy the DLL to a folder (e.g., `C:\rtmidi\`) and point the IDE to it in **Preferences -> MIDI**.
-   **Developer Method:** Use Microsoft's `vcpkg`: `vcpkg install rtmidi:x64-windows`.

#### **macOS**
-   Install via Homebrew: `brew install rtmidi`
-   **Location:** `/opt/homebrew/lib/` (Apple Silicon) or `/usr/local/lib/` (Intel).
-   **Tip:** Find the exact path with `brew ls --verbose rtmidi | grep dylib`.

#### **Linux**
-   Install via your package manager: `sudo apt-get install librtmidi-dev` (on Debian/Ubuntu).
-   **Location:** Usually in `/usr/lib/x86_64-linux-gnu/`.

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
