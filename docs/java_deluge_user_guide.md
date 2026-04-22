# ChucK-Java Deluge Workstation - User Guide

Welcome to the ChucK-Java Deluge Workstation, a software emulation of the Synthstrom Deluge workflow, built with JavaFX and the ChucK audio engine.

## 1. Core Concepts

### 1.1 View Modes
-   **Clip Mode**: This is where you edit the sequence for a single track. For a Kit track, rows represent different sounds (Kick, Snare, etc.). For a Synth track, rows represent pitches.
-   **Song Mode**: This view consolidates tracks and allows you to launch clips and manage the overall arrangement.

### 1.2 Instruments
-   **KIT**: A sample-based instrument. Loading a kit populates rows with different drum sounds.
-   **SYNTH**: A synthesis engine track for melodic lines.

## 2. Using Song Mode

### 2.1 The Grid
In Song Mode, the grid displays the clips.
-   **Rows**: Represent **Clips** (following the hardware model).
-   **Columns**: Represent clip slots.

### 2.2 Actions
-   **Add Clip**: Click an empty cell to create a new clip.
-   **Remove Clip**: Click a playing clip to cycle back to empty or stop it.
-   **Move Clip**: Click and drag a filled clip cell to another cell to move it.
-   **Mute Track**: Click the **[M]** button next to the track name to mute/unmute the track.

## 3. Hardware Parity

Our implementation is inspired by the Synthstrom Deluge hardware and aims to replicate its workflow.

### 3.1 Engine Capabilities
-   We have expanded the Java DSL engine to support up to **64 simultaneous tracks** (e.g., 8 kits of 8 sounds each), allowing you to play multiple kits at once.

## 4. Advanced Features

-   **Live Recording**: Record notes from grid or MIDI at playhead position.
-   **MIDI Learn**: Map physical CC controls to parameters persistently.
-   **Persistence**: Save/Load projects to Deluge-compatible XML on disk.

## 5. Quick Start

1.  **Load a Kit**: Select a kit from the sidebar. It will be added as a new track.
2.  **Create a Sequence**: Go to Clip Mode and click steps to create a rhythm, or use Record mode to play live!
3.  **Use Song Mode**: Switch to Song Mode to see your clips and launch them.
