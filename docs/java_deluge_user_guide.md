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
In Song Mode, the grid displays the clips available for each track.
-   **Rows**: Represent **Tracks** (Instruments) in our current implementation.
-   **Columns**: Represent clip slots.

### 2.2 Actions
-   **Add Clip**: Click an empty cell to create a new clip.
-   **Remove Clip**: Click a playing clip to cycle back to empty or stop it.
-   **Move Clip**: Click and drag a filled clip cell to another cell to move it.
-   **Mute Track**: Click the **[M]** button next to the track name to mute/unmute the track.

## 3. Hardware Parity and Gaps

Our implementation is inspired by the Synthstrom Deluge hardware but has some differences to accommodate software paradigms and current engine limits.

### 3.1 Song Mode Model Gap
-   **Real Deluge**: Song View uses **Rows as Clips**. You can have many clips for the same instrument on different rows. The leftmost pads (Launch pads) trigger the clip in that row.
-   **JavaFX Implementation**: We use a more traditional DAW approach where **Rows are Tracks** and columns are clip slots (similar to Ableton Session View). This makes it easier to visualize full tracks on screen.

### 3.2 Engine Capabilities
-   We have expanded the Java DSL engine to support up to **64 simultaneous tracks** (e.g., 8 kits of 8 sounds each), allowing you to play multiple kits at once.

## 4. Quick Start

1.  **Load a Kit**: Select a kit from the sidebar. It will be added as a new track.
2.  **Create a Sequence**: Go to Clip Mode and click steps to create a rhythm.
3.  **Use Song Mode**: Switch to Song Mode to see your track, add clips, and use the Mute buttons to control the mix.
