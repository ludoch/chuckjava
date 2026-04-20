# ChucK-Java Deluge: Complete Reference Manual

Version 1.10 — April 2026 (Compatible with Production Engine v1.8)

---

## 1. Introduction & Philosophy
The ChucK-Java Deluge Emulator is a high-fidelity recreation of the Synthstrom Audible Deluge 4.1 workflow. It features a unique **Dual-Engine Architecture** that allows users to choose between classic ChucK scripting and high-performance native Java synthesis.

---

## 2. Interface Reference (Hardware Emulation)

### 2.1 The Master Control Row
*   **VIEW MODE (CLIP / SONG / ARR)**: Toggle between Sequencer, Launcher, and Timeline.
*   **▶ PLAY**: Starts the internal ChucK clock (Sample-Accurate).
*   **● REC**: Toggles Automation Recording.
*   **TEMPO / SWING**: Global rhythmic adjustment.

### 2.2 The Parameter Ribbon (Simulated Gold Knobs)
Select a mode, then **Vertical Drag** on the grid or track label to edit.

| Button | Mode | ChucK Hookup |
| :--- | :--- | :--- |
| **LEVEL / VELO** | Level/Velocity | `env.gain` / `SndBuf.gain` |
| **PAN** | Panning | `Pan2.pan` (Interpolated) |
| **PITCH** | Pitch | `Wavetable.freq` |
| **FILTER** | Cutoff | `SVFilter.freq` (20Hz-20kHz) |
| **RESONANCE** | Resonance (Q) | `SVFilter.Q` (1.0-10.0) |
| **MOD FX** | Mod Amount | `Chorus.modDepth` |
| **STUTTER** | Stutter Trigger | Rhythmic 1/32 repeats. |
| **GATE** | Note Length | `keyOff` timing. |

---

## 3. The Synthesis & Audio Engines

### 3.1 Dual-Engine Architecture
The emulator supports two distinct audio processing "brains." You can toggle between them in real-time during playback.

*   **Classic ChucK (🎸)**: Runs the engine via the original `engine.ck` script. Best for compatibility and rapid prototyping of new DSP logic.
*   **Native Java DSL (🚀)**: A high-performance implementation using the ChucK-Java DSL. It leverages **Java Virtual Threads (Loom)** for voice management and modern JIT optimizations.

### 3.2 Engine Controls
*   **Toggle Engine**: Press **Ctrl + G** to swap between ChucK and Java modes.
*   **Verification**: The console will log: `🔄 Toggling Engine Mode: JAVA DSL`.

### 3.3 Voice Architecture
Each engine provides:
*   **8-Voice Polyphony**: Independent FM operators and filters for 8 synth tracks.
*   **Dynamic Sidechaining**: Kick-driven ducking for the main synth bus.
*   **Master Bus**: Serial HPF -> Compressor -> Limiter chain.

---

## 4. Asset Management & Presets

### 4.1 The Unified Asset Browser
Click **📂 LOAD XML** in the transport row to open the searchable OLED browser.
*   **Active Track Loading**: The browser identifies your selected track and shows either Synth or Kit presets.
*   **Search**: Filter the library (e.g., "TR-808") via the integrated search bar.
*   **Categories**: **[F] Factory** (bundled) and **[U] User** (local `/presets` folder).

### 4.2 Saving Custom Sounds
Open the **⚙ (Gear Icon)** and click **💾 SAVE PRESET**. Sounds are stored as Deluge-compatible XMLs in the local `/presets` folder.

---

## 5. Sound Editor Reference (Nested Menus)

Click the **⚙ (Gear Icon)** on any track to access deep parameters.

*   **ARPEGGIATOR**: Per-track patterns with Rate (0.25x to 4.0x) and Octave range (1-4).
*   **FILTER**: Multi-mode SVF with Drive and Envelope modulation.
*   **FM SYNTHESIS**: Per-track FM Ratio and Modulation Amount (Index).

---

## 6. MIDI & Hardware Integration

*   **MIDI Follow**: Incoming notes are routed to the **active track**.
*   **MIDI Learning**: **Right-Click** any ribbon button -> **MIDI Learn** to bind external knobs.

---

## 7. Quick Reference (Popular Commands)

| Category | Interaction | Function |
| :--- | :--- | :--- |
| **Engine** | **Ctrl + G** | **Toggle between ChucK and Java DSL Engine** |
| **Asset** | 📂 LOAD XML | Open Searchable Asset Browser |
| **Asset** | 💾 SAVE PRESET | Save current sound to /presets |
| **Grid** | Left-Click Pad | Toggle Step Note On/Off |
| **Grid** | Vertical Drag Pad | Lock Parameter Value to Step |
| **Track** | ⚙ Gear Icon | Open OLED Sound Editor |
| **Perform** | Hold STUTTER | Trigger High-speed Beat Repeat |

---

*ChucK-Java Deluge Manual — Version 1.10 — April 2026*
