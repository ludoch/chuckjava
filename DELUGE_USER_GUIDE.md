# ChucK-Java Deluge: Complete Reference Manual

Version 1.5 — April 2026 (Compatible with Production Engine v1.4)

---

## 1. Introduction & Philosophy
The ChucK-Java Deluge Emulator is a high-fidelity recreation of the Synthstrom Audible Deluge 4.1 workflow. It combines the tactile immediacy of the hardware grid with the limitless sound-generation capabilities of the ChucK programming language. This manual provides a comprehensive deep-dive into the engine architecture, UI interactions, and MIDI integration.

---

## 2. Interface Reference (Hardware Emulation)

### 2.1 The Master Control Row
Located at the very top, these controls manage the global "heartbeat" of the engine.

*   **VIEW MODE (CLIP / SONG / ARR)**:
    *   **CLIP**: The primary 16-step sequencer.
    *   **SONG**: The 8x8 clip launcher (64 total patterns).
    *   **ARR**: The linear arrangement timeline.
*   **▶ PLAY**: Starts the internal ChucK clock. The engine uses virtual time to ensure sample-accurate playback regardless of CPU load.
*   **● REC (Automation Recording)**: When active (Red Glow), any vertical drag performance on the UI is captured into the sequence at the current playhead position.
*   **■ STOP**: Halts playback and resets all `SndBuf` and `MorphingWavetable` phase positions.
*   **TEMPO / SWING**: Real-time rhythmic adjustment. Swing affects the 2nd and 4th 16th-notes of every beat (delayed by the swing percentage).
*   **MASTER VOL**: Controls the gain of the `master_shred` final limiter stage.

### 2.2 The Parameter Ribbon (The "Gold Knobs")
The ribbon buttons select which parameter is currently "mapped" to the grid. In the emulator, **Vertical Dragging** on a pad or label replaces the hardware's Gold Knobs.

| Button | Mode | Engineering Detail |
| :--- | :--- | :--- |
| **LEVEL / VELO** | Level/Velocity | Controls `env.gain` (Synth) or `SndBuf.gain` (Kit). Scales with Master Level. |
| **PAN** | Panning | Controls `Pan2.pan`. Smoothed via linear interpolation. |
| **PITCH** | Pitch | Synth: `MorphingWavetable.freq`. Kit: `SndBuf.rate` (resampling pitch). |
| **FILTER** | Cutoff | Offset for the `SVFilter.freq`. Range: 20Hz - 20,000Hz. |
| **RESONANCE** | Resonance (Q) | Offset for `SVFilter.Q`. Range: 1.0 (flat) - 10.0 (whistling). |
| **MOD FX** | Mod Amount | Controls the `Chorus.modDepth` of the global MOD bus. |
| **DELAY** | Delay Send | Post-fader send to the `Echo` bus in `fx_bus_shred`. |
| **REVERB** | Reverb Send | Post-fader send to the `JCRev` bus in `fx_bus_shred`. |
| **STUTTER** | Stutter Trigger | Momentary trigger. Halts `g_current_step` increment and triggers 1/32 repeats. |
| **GATE** | Note Length | Determines the `keyOff` timing. Visualized via dashed borders. |
| **PROB** | Probability | A dice roll (0.0-1.0) compared against the step value at every trigger. |
| **START/END** | Sample Range | Offsets the `SndBuf.pos` and monitors playback end in real-time. |

---

## 3. The Synthesis & Audio Engine (v1.4)

### 3.1 FM Synth Architecture
Each of the 4 Synth tracks (Rows 5-8) uses a dual-operator FM architecture:
*   **Carrier**: A `MorphingWavetable` (interpolating between Sine and Saw).
*   **Modulator**: A `MorphingWavetable` that modulates the Carrier's frequency.
*   **FM Ratio**: Controlled via the global `g_fm_ratio`.
*   **FM Amount**: Controlled via `g_fm_amount` (also known as FM Index).

### 3.2 Dynamic Sidechaining
The emulator features a "Ghost Sidechain" hardwired to **Track 0 (KICK)**.
*   Whenever a note is triggered on Track 0, a `sidechain_event` is broadcast.
*   The `sidechain_shred` catches this event and applies a rapid volume dip to the `g_synth_bus` (Synths 1-4).
*   **Recovery**: The recovery time is fixed at 100ms for a classic "pumping" electronic feel.

### 3.3 Master Signal Chain
The final audio output is processed through a high-fidelity serial chain:
`Synth/Kit Buses` → `HPF (20Hz)` → `Compressor (Dyno)` → `Limiter (Dyno)` → `Safety Gate` → `DAC`.

---

## 4. Sequencing Workflow

### 4.1 Step Sequencing (Clip View)
*   **Note Toggle**: Single left-click on any grid cell.
*   **Numerical Edit**: Right-click any cell to open the **Step Editor**. This allows precise 0-127 MIDI-style value entry for Velocity and Gate.
*   **Melodic Entry**: On Synth tracks, **Shift + Right-Click** opens the **Note Entry** popover.
    *   **Scale Folding**: If a Scale (e.g., Minor) is selected in the Transport Panel, out-of-key notes are automatically dimmed and disabled.
    *   **Octave Control**: Use the ◄/► buttons to shift the grid range.

### 4.2 Song Mode & Clip Library
Song Mode provides a high-level view of your project's **64 available clips**.
*   **Organization**: 8 tracks (rows) x 8 slots (columns A-H).
*   **Launch Quantization**: Clips always launch on the **next bar boundary** (Step 16).
*   **Section Launching**: Clicking the **Section Bar** (letters A-H) will queue all clips in that vertical column. This is equivalent to "launching a scene" in Ableton Live.
*   **Persistence**: The emulator maintains a live "Shadow Copy" of all clips. Swapping clips in Song Mode instantly updates the Sequencer Grid with the new data.

---

## 5. MIDI & Hardware Integration

The emulator replicates the Deluge 4.1 "MIDI Follow" behavior.

### 5.1 MIDI Input Routing
*   **Auto-Detection**: On startup, the app opens **all** native MIDI input ports.
*   **Follow Active Track**: Any incoming MIDI Note-On/Off is routed to the **currently selected track** in the UI.
*   **Live Performance**: Incoming notes trigger a high-quality "Live Voice" in the engine, which is mixed with the sequenced synth output.

### 5.2 MIDI Learning (CC Mapping)
You can bind external MIDI knobs to any global parameter:
1.  **Right-Click** a parameter button in the ribbon (e.g., **FILTER**).
2.  Select **"MIDI Learn"**.
3.  Move the knob on your hardware controller.
4.  **Verification**: The console will log: `MIDI LEARN: CC [num] -> g_filter`.

---

## 6. Project Management

### 6.1 Loading Official Presets
The app features an **official Deluge XML Parser**. 
*   Click **📂 LOAD XML** to import files from a Deluge SD card.
*   **Synths**: Maps `<osc1><type>`, `<filter><type>`, and `<env1>` parameters.
*   **Kits**: Maps `<sample><fileName>` and `<row><pitch>` data.

### 6.2 Saving & Persistence
*   **Auto-Save**: The app automatically saves your current Clip Library to the internal `preferences.json` on exit.
*   **Export**: (Under Development) Support for exporting 16-step patterns back to Deluge XML format.

---

## 7. Comparison with Hardware 4.1

| Feature | Deluge Hardware | ChucK-Java Emulator |
| :--- | :--- | :--- |
| **Grid** | 128 (8x16) RGB Pads | 128 (8x16) JavaFX Pads |
| **Knobs** | 2 Gold, 2 Silver | Vertical Dragging (Simulated) |
| **Polyphony** | Limited by CPU | 4 Voices (Strict Virtual Time) |
| **Audio Quality** | 24-bit / 44.1kHz | 32-bit Floating Point / 44.1kHz |
| **Synthesis** | Subtractive, FM, Multi | Subtractive (v1.1), FM (v1.3) |
| **Sampling** | Live Line-In / Mic | File-based `.wav` loading |
| **Song Mode** | Unlimited Scenes | 8 Sections (A-H) |

---

*ChucK-Java Deluge Manual — Version 1.5 — April 2026*
