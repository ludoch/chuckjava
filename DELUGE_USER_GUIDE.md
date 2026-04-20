# ChucK-Java Deluge User Guide

Welcome to the digital edition of the Synthstrom Audible Deluge, powered by ChucK-Java. This application replicates the workflow and soul of the Deluge hardware within a high-performance JavaFX environment.

---

## Table of Contents
1. [Hardware Overview](#1-hardware-overview)
2. [Interface Reference](#2-interface-reference)
3. [Getting Started](#3-getting-started)
4. [Clip View](#4-clip-view)
5. [MIDI Support (Hardware Integration)](#5-midi-support-hardware-integration)
6. [Song Mode](#6-song-mode)
7. [Arranger Mode](#7-arranger-mode)
8. [Sound Design & Advanced DSP](#8-sound-design--advanced-dsp)
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

### The Matrix Grid
The Matrix consists of 8 tracks (rows) and 16 steps (columns).
*   **Audition Pad (Square)**: Located on the far left of each row. Click to trigger the track's sound manually.
*   **Track Label**: Identifies the sound (e.g., KICK, SYNTH 1). Drag vertically to adjust Track Level or Filter.
*   **Step Cells**: 16 interactive buttons for sequencing. Active steps are lit with the track's unique color.
*   **Playhead**: A white highlight that moves across the columns during playback.

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

### Sequencing & Parameter Editing
*   **Left Click**: Toggle a note on (Track Color) or off (Dark).
*   **Vertical Drag (on active step)**: Dynamically adjust the value of the currently selected parameter.
*   **Right Click**: Open the **Step Editor** popover for precise numerical entry.
*   **Shift + Right Click (Synth only)**: Open the **Note Entry** popover to select a specific pitch.

---

## 5. MIDI Support (Hardware Integration)

The ChucK-Java Deluge features robust integration with external MIDI hardware, emulating the Deluge 4.1 "MIDI Follow" and "Learning" workflows.

### Connection
The application automatically scans and opens **all available native MIDI input ports** on startup. No configuration is necessary.

### Live Playing (MIDI Keyboard)
You can play the internal synths using an external MIDI keyboard. 
*   **Follow Mode**: Incoming MIDI notes are routed to the **currently selected track** in the UI.
*   **Auditioning**: Playing a note will trigger a high-quality "Live" voice in the ChucK engine, allowing you to jam alongside your sequence.
*   **Recording**: (Coming Soon) Playing notes while the transport is running will record them into the active clip.

### MIDI Learning (CC Mapping)
Map your hardware knobs or sliders directly to Deluge parameters:
1.  **Right-Click** any button in the **Parameter Ribbon** (e.g., FILTER, DELAY, LEVEL).
2.  Select **"MIDI Learn"** from the context menu.
3.  **Move a knob** on your external MIDI controller.
4.  The parameter is now bound! Moving that knob will update the global engine value in real-time.

### Comparison: Emulator vs. Hardware 4.1
| Feature | Hardware 4.1 | ChucK-Java Emulator |
| :--- | :--- | :--- |
| **Learning** | Hold Learn + Turn Knob | Right-Click Menu + Turn Knob |
| **Note Input** | MIDI In (DIN/USB) | Auto-detect all System MIDI Ports |
| **Mapping** | Persistent in XML | Session-based (Saving coming soon) |
| **MPE** | Supported | Basic Note + Velocity only |
| **MIDI Follow** | Optional | Always On (Active track receives MIDI) |

---

## 6. Song Mode

Accessed via the **SONG** toggle. The matrix transforms into a **Clip Launcher** grid (8 tracks x 8 slots).

### Clip Library & Persistent Patterns
Unlike standard sequencers, the Deluge Emulator stores up to **64 unique clips** (8 per track). 
*   **Active Clip**: In Clip View, you are always editing the "active" clip for each track.
*   **Automatic Sync**: Any changes you make in Clip View (adding notes, dragging velocity) are **automatically saved** into the corresponding slot in the Clip Library.

### Launching Clips
*   **Queueing**: Click a colored block in the Song Mode grid to queue that pattern. The cell will turn **Yellow** (Queued).
*   **Quantized Launch**: The pattern will wait until the **next 1 bar boundary** (Step 16) to start.
*   **Playing**: Once launched, the cell turns **Green** (Playing) and the sequencer grid in Clip View is automatically updated with the new pattern data.
*   **Section Launch**: Click the letters (A, B, C...) in the top **Section Bar** to launch an entire column of clips simultaneously.

### Stopping Clips
*   Click an active (Green) clip to queue it to stop. It will return to its "Filled" state at the end of the bar.

---

## 7. Arranger Mode

Accessed via the **ARR** toggle. Provides a linear timeline of the song structure. Use this to record your clip-launching performance into a permanent arrangement.

---

## 8. Sound Design & Advanced DSP

The emulator features a professional-grade ChucK audio engine (v1.4) with advanced modulation capabilities.

### Parameter Locking
Select a parameter from the **Ribbon** (e.g., FILTER), then **Vertical Drag** on an active step to "lock" a specific value to that point in time. 

### Advanced Synth Modes
*   **FM Synthesis**: Every synth voice includes a modulator and carrier. Adjust **FM Ratio** and **Amount** for metallic, evolving textures.
*   **Sidechain Ducking**: The **Kick** drum (Track 0) is hardwired to a sidechain bus. Synths will automatically "duck" when the kick triggers, creating dynamic rhythmic movement.
*   **Interpolation**: Parameter locks (like Filter sweeps) are **automatically smoothed** using linear interpolation, preventing clicks and "steppiness."

### Global Master Chain
The final output passes through a high-quality serial chain:
1.  **HPF**: Removes infra-sonic mud below 20Hz.
2.  **Compressor**: User-adjustable threshold via the **LEVEL** learn mapping.
3.  **Limiter**: Prevents digital clipping.
4.  **Safety Gate**: Ensures zero noise when the transport is stopped.

---

## 9. Current Limitations

| Hardware Feature | Current Status | Workaround / Detail |
| :--- | :--- | :--- |
| **Polyphony** | 4 Voices | Total voices across synth tracks. |
| **Automation Rec** | Planned | Manual parameter locking only. |
| **Sampling** | Static | Internal wav samples only; no live line-in sampling. |
| **Resampling** | Not Possible | Use external system recording. |
| **Unison** | Not Possible | Single voice per synth track. |
| **Undo / Redo** | Model only | Logic exists in code but lacks UI buttons. |
| **Midi Out** | Experimental | Basic routing available in bridge. |

---

*Manual version 1.3 — April 2026*
