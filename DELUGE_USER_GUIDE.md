# ChucK-Java Deluge User Guide

Welcome to the digital edition of the Synthstrom Audible Deluge, powered by ChucK-Java. This application replicates the workflow and soul of the Deluge hardware within a high-performance JavaFX environment.

---

## Table of Contents
1. [Hardware Overview](#1-hardware-overview)
2. [Getting Started](#2-getting-started)
3. [Clip View](#3-clip-view)
4. [Song Mode](#4-song-mode)
5. [Arranger Mode](#5-arranger-mode)
6. [Sound Design](#6-sound-design)
7. [Advanced Features](#7-advanced-features)
8. [Current Limitations](#8-current-limitations)

---

## 1. Hardware Overview

The Deluge UI is divided into four primary interactive zones:

<svg width="600" height="400" viewBox="0 0 600 400" xmlns="http://www.w3.org/2000/svg">
  <!-- Main Frame -->
  <rect x="10" y="10" width="580" height="380" rx="10" fill="#1a1a1a" stroke="#444" stroke-width="2"/>
  
  <!-- Top: Transport & Ribbon -->
  <rect x="30" y="30" width="540" height="80" rx="5" fill="#2b2b2b" stroke="#3d3d3d"/>
  <text x="300" y="55" fill="#e0e0e0" font-family="Arial" font-size="14" text-anchor="middle">TRANSPORT & PARAMETERS</text>
  <rect x="40" y="65" width="60" height="30" rx="3" fill="#2e7d32"/> <!-- Play -->
  <rect x="110" y="65" width="60" height="30" rx="3" fill="#c62828"/> <!-- Stop -->
  
  <!-- Center: Matrix -->
  <rect x="30" y="120" width="540" height="220" rx="5" fill="#111" stroke="#3d3d3d"/>
  <text x="300" y="145" fill="#888" font-family="Arial" font-size="12" text-anchor="middle">THE GRID (16 x 8)</text>
  
  <!-- Bottom: Status -->
  <rect x="30" y="350" width="540" height="30" rx="5" fill="#222" stroke="#333"/>
  <text x="50" y="370" fill="#aaa" font-family="Arial" font-size="10">OLED STATUS BAR</text>
</svg>

### Transport & Modes
*   **▶ PLAY / ■ STOP**: Controls the global transport.
*   **VIEW MODES**: Toggle between **CLIP** (Sequencing), **SONG** (Launching), and **ARR** (Arranging).
*   **TEMPO / SWING**: Adjustable sliders for the global clock.

### The Matrix
The heart of the Deluge. An 8-row by 16-column grid used for entering notes, launching clips, or visualizing the timeline.

<svg width="600" height="200" viewBox="0 0 600 200" xmlns="http://www.w3.org/2000/svg">
  <!-- CLIP MODE -->
  <rect x="20" y="20" width="260" height="160" rx="5" fill="#111" stroke="#333"/>
  <text x="150" y="45" fill="#e0e0e0" font-family="Arial" font-size="12" text-anchor="middle">CLIP VIEW (SEQUENCER)</text>
  <circle cx="50" cy="80" r="8" fill="#d4af37"/> <!-- Active Note -->
  <circle cx="80" cy="80" r="8" fill="#333"/>
  <circle cx="110" cy="80" r="8" fill="#d4af37"/>
  <rect x="40" y="100" width="220" height="4" fill="#555" rx="2"/> <!-- Track Line -->
  
  <!-- Arrow -->
  <path d="M 300 100 L 340 100 M 330 90 L 340 100 L 330 110" stroke="#888" stroke-width="2" fill="none"/>

  <!-- SONG MODE -->
  <rect x="360" y="20" width="220" height="160" rx="5" fill="#111" stroke="#333"/>
  <text x="470" y="45" fill="#e0e0e0" font-family="Arial" font-size="12" text-anchor="middle">SONG VIEW (LAUNCHER)</text>
  <rect x="380" y="70" width="40" height="25" rx="3" fill="#2e7d32"/> <!-- Active Clip -->
  <rect x="430" y="70" width="40" height="25" rx="3" fill="#444"/> <!-- Inactive Clip -->
  <rect x="380" y="105" width="40" height="25" rx="3" fill="#1565c0"/> <!-- Clip B -->
</svg>

---

## 2. Getting Started

### Playback
Click **▶ PLAY** in the top transport panel. The playhead (highlighted column) will begin moving across the grid. Use **■ STOP** to reset the playhead to the beginning.

### Global Parameters
*   **Tempo**: Drag the **TEMPO** slider to adjust BPM (60 - 200).
*   **Swing**: Drag the **SWING** slider to add "groove" to your patterns. 50% is straight, >50% pushes even 16th notes.
*   **Master Vol**: Controls the final output level of the ChucK engine.

---

## 3. Clip View

Clip view is where you create your patterns. The grid is split into **Kit** tracks and **Synth** tracks.

### Track Layout
*   **Rows 1-4**: Drum Kit tracks (Kick, Snare, HiHat, Open Hat).
*   **Rows 5-8**: Polyphonic Synth tracks.

### Sequencing
*   **Left Click**: Toggle a note on (Track Color) or off (Dark).
*   **Right Click**: Open the **Step Editor** popover to adjust Velocity, Gate length, and Probability.
*   **Shift + Right Click (Synth only)**: Open the **Note Entry** popover to select a specific pitch for that step.
*   **Audition**: Click the square **Audition Pad** on the far left of any row to hear the sound instantly.
*   **Configuration**: Click the **⚙ (Gear)** icon to open the Track Settings.

---

## 4. Song Mode

Accessed by clicking the **SONG** toggle. In this mode, the matrix transforms from a step sequencer into a **Clip Launcher**.

### Launching Clips
*   Each row represents a track.
*   The colored blocks represent saved clips/patterns.
*   Click a block to "queue" it for the next bar.
*   The Deluge uses **Launch Quantization** to ensure all clips stay in sync.

---

## 5. Arranger Mode

Accessed by clicking the **ARR** toggle. Arranger mode provides a linear "DAW-style" timeline of your entire track.

### Timeline Navigation
*   Horizontal axis represents time (Bars/Beats).
*   Vertical axis represents tracks.
*   You can see exactly when clips are triggered and how long they play.

---

## 6. Sound Design

Click the **⚙** icon on any track to dive into the engine.

### Synth Engine
*   **Oscillators**: Choose between Sine, Saw, Square, Triangle, and Analog-modeled waveforms.
*   **Filter**: State Variable Filter (SVF) with Lowpass, Highpass, and Bandpass modes. Adjustable **Cutoff** and **Resonance**.
*   **Envelopes**: Exponential Attack/Decay/Sustain/Release. *Currently supporting Attack adjustment via UI.*

### Kit Engine
*   **Sample Selection**: Open the **Browser** to load custom `.wav` files into any drum slot.
*   **Pitch**: Shift the sample pitch by +/- 24 semitones.

---

## 7. Advanced Features

### Loading Hardware Files
The Java edition can parse official Deluge `.XML` files!
1.  Click **📂 LOAD XML** in the transport panel.
2.  Select a Synth or Kit XML file from your Deluge SD card.
3.  The app will attempt to map parameters to the ChucK engine.

### Debugging
Click the **🐞 DEBUG** button to enable real-time audio tracing in the console. This shows which UGens are active and their current output levels.

---

## 8. Current Limitations

While the Java edition is powerful, some hardware features are currently in development. Understanding these gaps will help you plan your patches:

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

*Manual version 1.1 — April 2026*
