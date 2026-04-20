# ChucK-Java Deluge User Guide

Welcome to the digital edition of the Synthstrom Audible Deluge, powered by ChucK-Java. This application replicates the workflow and soul of the Deluge hardware within a high-performance JavaFX environment.

---

## Table of Contents
1. [Hardware Overview](#1-hardware-overview)
2. [Interface Reference](#2-interface-reference)
3. [Getting Started](#3-getting-started)
4. [Clip View](#4-clip-view)
5. [Song Mode](#5-song-mode)
6. [Arranger Mode](#6-arranger-mode)
7. [Sound Design](#7-sound-design)
8. [Advanced Features](#8-advanced-features)
9. [Current Limitations](#9-current-limitations)

---

## 1. Hardware Overview

The Deluge UI is divided into three primary interactive zones: Global Controls, the Matrix Grid, and the Status Bar.

![UI Overview](docs/images/ui_overview.svg?v=2)

---

## 2. Interface Reference

### Global Controls (Top Panel)
*   **VIEW MODE (CLIP / SONG / ARR)**: Toggles the central Matrix between Step Sequencing, Clip Launching, and Timeline Arrangement.
*   **▶ PLAY**: Starts the global transport and internal ChucK clock.
*   **■ STOP**: Stops playback and resets the playhead to Step 0.
*   **TEMPO (BPM)**: Adjusts the playback speed (60 to 200 BPM).
*   **SWING (%)**: Controls the timing offset for even-numbered 16th notes. 50% is "straight" timing.
*   **MASTER VOL**: Sets the final output gain of the ChucK audio engine.
*   **📂 LOAD XML**: Opens a file browser to load official Deluge Synth or Kit XML presets.
*   **🐞 BUG (Debug)**: Toggles real-time console logging for DAC attribution (shows which UGens are outputting signal).

### The Matrix Grid
The Matrix consists of 8 tracks (rows) and 16 steps (columns).
*   **Audition Pad (Square)**: Located on the far left of each row. Click to trigger the track's sound manually.
*   **Track Label**: Identifies the sound (e.g., KICK, SYNTH 1).
*   **⚙ (Gear Icon)**: Opens the detailed configuration dialog for that specific track.
*   **Step Cells**: 16 interactive buttons for sequencing. Active steps are lit with the track's unique color.
*   **Beat Separators**: A vertical dark bar appears every 4 cells (after steps 4, 8, and 12) to help visualize the 4/4 time signature.
*   **Playhead**: A white highlight that moves across the columns during playback.

### Status Bar (Bottom)
Provides real-time feedback on the current step, active BPM, Swing percentage, and engine health status.

---

## 3. Getting Started

### Playback
Click **▶ PLAY** in the top transport panel. The playhead will begin moving across the grid. Use **■ STOP** to reset the playhead to the beginning.

### Adjusting Sound
Drag the **TEMPO** or **SWING** sliders to change the groove in real-time. The ChucK engine uses virtual time to ensure these changes are sample-accurate.

---

## 4. Clip View

Clip view is the default sequencing environment. 

### Track Layout
*   **Rows 1-4**: Drum Kit tracks (Kick, Snare, HiHat, Open Hat).
*   **Rows 5-8**: Polyphonic Synth tracks.

### Sequencing
*   **Left Click**: Toggle a note on (Track Color) or off (Dark).
*   **Right Click**: Open the **Step Editor** popover to adjust Velocity, Gate length, and Probability.
*   **Shift + Right Click (Synth only)**: Open the **Note Entry** popover to select a specific pitch for that step.

---

## 5. Song Mode

Accessed via the **SONG** toggle. The matrix transforms into a **Clip Launcher**.

![Grid Transformation](docs/images/grid_transformation.svg?v=2)

### Launching Clips
*   Click a colored block to queue a clip. It will flash until the next bar boundary, then stay lit while playing.
*   Clicking an active clip will queue it to stop at the end of the current bar.

---

## 6. Arranger Mode

Accessed via the **ARR** toggle. Provides a linear timeline of the song structure. Use this to record your clip-launching performance into a permanent arrangement.

---

## 7. Sound Design

Click the **⚙** icon on any track to open the engine configuration.

### Synth Engine
*   **Oscillators**: Select waveforms (Sine, Saw, etc.).
*   **Filter**: Control Cutoff and Resonance for the SVF filter.
*   **Envelopes**: Adjust the Attack curve of the exponential ADSR.

### Kit Engine
*   **Sample Selection**: Use the internal browser to load `.wav` files.
*   **Pitch**: Shift samples by +/- 24 semitones.

---

## 8. Advanced Features

### Loading Hardware Files
1.  Click **📂 LOAD XML**.
2.  Select a Synth or Kit XML file.
3.  The app maps XML parameters (like `<osc1><type>`) directly to ChucK UGen properties.

---

## 9. Current Limitations

| Hardware Feature | Current Status | Workaround / Detail |
| :--- | :--- | :--- |
| **Polyphony** | Limited | 4-voice polyphony for synths; 4-track monophonic kits. |
| **Oscillators** | Single | Each synth voice uses one `MorphingWavetable` (Sine/Saw transition). |
| **Resampling** | Not Possible | Use external system recording. |
| **FM Synthesis** | Not Possible | Native ChucK `SinOsc` FM patches are not yet wired to the UI. |
| **Probability** | Engine only | Logic exists in ChucK but lacks UI toggles per step. |
| **Automation** | Linear only | Use UI sliders; recording "gold knob" movements is not yet supported. |
| **Unison** | Not Possible | Single voice per synth track in the current `engine.ck`. |
| **Sidechain** | Not Possible | Global `Dyno` limiter on the master bus provides compression but no ducking input. |
| **Effects** | Global Only | Delay and Reverb are shared across all tracks. |
| **Multisampling** | Not Possible | Each kit row supports one sample only. |
| **Undo / Redo** | Model only | Logic exists in code but lacks UI buttons/shortcuts. |
| **Midi Out** | Experimental | Basic routing available in `org.chuck.deluge.midi`. |

---

*Manual version 1.2 — April 2026*
