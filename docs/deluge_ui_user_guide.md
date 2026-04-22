# ChucK-Java Deluge UI User Guide

This guide describes the operation of the JavaFX Deluge Emulator, mapping the flows described in the official Synthstrom Deluge PDF Guidebook to our software interface.

## 1. Song Creation Workflow

### 1.1 Loading an Existing Song
1.  Open the **Project Manager** sidebar on the left.
2.  Select the **LIBRARY** tab (this acts as the "SD CARD" explorer).
3.  Expand the **SONGS** folder.
4.  **Double-click** on a song file (e.g., `song1`, `song2`, or `song3`) to load it.
5.  The status bar will indicate when the song is loaded, and the audio engine will load the associated samples.

### 1.2 Navigating Views
-   Use the mode toggle buttons at the top: **CLIP**, **SONG**, **ARR** (Arranger).
-   **Song Mode** gives you the overview of all clips.
-   **Clip Mode** allows you to edit the specific steps of a clip.

## 2. Song Mode Operations

We have refactored Song Mode to follow the hardware model where **Rows represent Clips**.

### 2.1 Launching Clips (Right-Side Controls)
-   In Song View, each row corresponds to a clip.
-   On the **right side** of the grid, there are two dedicated control buttons for each row:
    -   **L** (Launch): Click this button to start playback of the clip. It will turn **green** to indicate it is playing, and it will trigger the audio sequence in the bridge.
    -   **C** (Color): Click this button to cycle through 4 preset colors (Cyan, Pink, Yellow, Green) for the pads in that row.
-   **Pads**: The pads in the grid are now solid colored (no text) when filled, matching the hardware pad style.

### 2.2 Selecting a Clip to Edit
-   **Double-click on the row header** (the label on the left showing the clip name) to open it in the Clip Editor.
-   The view will automatically switch to **CLIP** mode, and the Matrix grid will be populated with the sequence from that clip.

### 2.3 Creating a New Clip
-   **Current State**: Clicking an empty cell in a row labeled `EMPTY` currently cycles through mock states (`PAT_0`, etc.) and does not yet create a real clip in the `ProjectModel`. This feature is pending.

## 3. Project Explorer vs. Library

-   **PROJECT Tab**: Currently hardcoded to show 8 tracks for mock visualization. It does not yet reflect the true state of loaded projects.
-   **LIBRARY Tab**: This is the functional file browser where you can load Kits, Synths, and Songs from resources or external folders.

## 4. Recording & MIDI Support

### 4.1 Live Grid Recording
1.  Click the **● REC** button in the Transport panel to enable Record mode.
2.  Press **▶ PLAY** to start the playhead.
3.  Click cells on the grid. They will act as trigger pads, recording notes at the current playhead position (`currentStep`) rather than toggling the step where you clicked.
4.  Recorded notes will appear on the grid and play back in the next loop.

### 4.2 MIDI Input & Learn
1.  Open **Settings > Mappings...** to select your MIDI Input device.
2.  Right-click any slider in the **MASTER FX** panel and select **MIDI Learn**.
3.  Move a physical knob on your MIDI controller to bind it to that slider. The mapping is saved persistently.

## 5. Auto-Scrolling

-   The grid now supports **auto-scrolling** to follow the playhead across pages.
-   When the playhead moves past step 15, the view shifts to show steps 16-31, and so on.
