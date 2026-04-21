# ChucK-Java Deluge User Guide

Welcome to the digital edition of the Synthstrom Audible Deluge, powered by ChucK-Java. This application replicates the workflow and soul of the Deluge hardware within a high-performance, desktop-optimized JavaFX environment.

---

## 1. The Deluge Object Model

The system is organized into a nested hierarchy of musical objects, managed via a persistent **Sidebar Project Manager**.

| Object | Software Equivalent | XML Mapping | User Actions (Mouse/Key) |
| :--- | :--- | :--- | :--- |
| **Song** | The Project File | `.xml` (Song) | **Save:** `Ctrl+S`. **New:** `Ctrl+N`. **Render:** `File > Export Audio`. |
| **Clip** | Track / Instance | Nested in Song `.xml` | **Create:** Click empty row in Song View. **Duplicate:** `Alt + Drag`. **Mute:** Right-click track header. |
| **Synth/Kit** | Instrument Engine | `.xml` (Preset) | **Edit:** Double-click Clip to open Graph Editor. **Load Preset:** Drag `.xml` from Library to Clip. |

---

## 2. Interface Reference

### Sidebar Project Manager
A persistent explorer on the left that provides access to the project structure and library.
*   **Project Tree:** View and manage all Tracks and Clips in the current Song.
*   **SD Card Emulator (Library):** Mirrors the Deluge folder structure (`SAMPLES`, `SYNTHS`, `KITS`, `SONGS`).
    *   **Built-in Kits:** Double-click a kit in the `KITS` folder (e.g., TR-909) to instantly load it into the project. The sequencer rows will update with the correct sound names and samples.
    *   **Quick Listen:** Play button next to every file for instant audition.
    *   **Drag-and-Drop:** Drag Synth/Kit XMLs directly onto clips to load presets.

### The Matrix Grid (Main View)
The central interaction zone for sequencing.
*   **Dynamic Grid:** Toggle between 8x16 and 16x16 views. Cells glow with velocity-sensitive brightness.
*   **Mouse Actions:**
    *   **Left-click:** Toggle notes on/off.
    *   **Right-click:** Contextual Popup Menu (Quantize, Transpose, Legato, Delete).
    *   **Shift + Drag:** Disable "Snap to Grid" for fine-grained, humanized timing.
    *   **Marquee Selection:** Click and drag a box over notes or clips to select multiple objects.

### Visual Editors (Pop-ups)
Instead of hardware knobs, the emulator uses high-precision visual graphs.
*   **OSC & FM Matrix:** A node-based editor for connecting operators via drag-and-drop. Adjust ratios by clicking and typing or using the mouse wheel.
*   **Multisampling Editor:** A waveform view where users drag `.wav` files onto a virtual piano keyboard for automatic pitch mapping.
*   **Automation Graphs:** Draw Bezier curves over the grid for parameter modulation.
    *   **Action:** Hold `A` + Click-drag on the grid to draw a filter sweep or volume curve.

---

## 3. Keyboard & Mouse Shortcuts Summary

| Action | Mapping |
| :--- | :--- |
| **Play / Stop** | `Space` |
| **Zoom (Horizontal)** | `Ctrl + Mouse Wheel` |
| **Zoom (Vertical)** | `Shift + Mouse Wheel` |
| **Scroll (Hand Tool)** | `Middle-click + Drag` |
| **Duplicate Object** | `Alt + Drag` |
| **Copy / Paste** | `Ctrl+C` / `Ctrl+V` |
| **Delete** | `Backspace` or `Delete` |
| **Arpeggiator** | `P` key (opens visual pattern editor with Euclidean toggle) |
| **MIDI Learn** | `Right-click` any knob > **Learn** (waits for physical controller) |
| **Key Mapping** | `Ctrl + M` |

---

## 4. Advanced Synthesis & Effects

### Sidechain (Chain)
A visual routing menu where you select a **Source** (e.g., Kick) and **Target** (e.g., Synth) with a dedicated slider for ducking depth.

### Global Config Editor
Accessed via `Settings > Global`. Manages:
*   MIDI Input/Output mapping and MPE toggles.
*   Audio buffer sizes and latency settings.
*   Clock Sync (Master/Slave toggle in Top Transport Bar).

---
*Manual version 2.0 — April 2026*
