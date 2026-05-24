# ChucK-Java Deluge Guidebook

This book is a formal reference to the ChucK-Java Deluge Workstation, built to mirror the operation of the official Synthstrom Deluge manual as closely as possible in a software environment.

---

## 1. CORE CONCEPTS

### 1.1 View Modes
-   **Song Mode**: This view consolidates tracks and allows you to launch clips and manage the overall arrangement. Rows represent Clips (following the hardware model), and columns represent clip slots.
-   **Clip Mode**: This is where you edit the sequence for a single track. For a Kit track, rows represent different sounds (Kick, Snare, etc.). For a Synth track, rows represent pitches.
-   **Arranger Mode**: Used for linear arrangement of clips over time.

### 1.2 Instruments
-   **KIT**: A sample-based instrument. Loading a kit populates rows with different drum sounds.
-   **SYNTH**: A synthesis engine track for melodic lines.

### 1.3 Hardware Parity & Limits
-   Our implementation is inspired by the Synthstrom Deluge hardware.
- We have expanded the Java DSL engine to support up to **128 simultaneous tracks** (e.g., handles the full standard 128 MIDI notes range from C1 to G9 melodic pitch grid lanes), allowing high-polyphony tracks and rich arrangements.
-   For a comprehensive per-feature and per-menu mapping against the official firmware, see [`FIRMWARE_FEATURES_MAPPING.md`](FIRMWARE_FEATURES_MAPPING.md).

### 1.4 Grid Mode (Viewport Configuration)

The grid viewport is configurable via **Settings → Preferences...** → **Grid Mode**. This controls how many rows and columns are visible at once, independent of the underlying clip data.

| Setting | Rows | Columns | Use Case |
|---------|------|---------|----------|
| GRID\_8x16  | 8  | 16 | Default. Matches real Deluge hardware (8×16 pads). |
| GRID\_16x16 | 16 | 16 | More voice rows for larger kits, same column width. |
| GRID\_24x16 | 24 | 16 | Maximum voice rows (24-step sequencer). |
| GRID\_16x24 | 16 | 24 | More visible steps per row (adds a horizontal scroll when clip length < 24). |

**How it works:**
- Changing the Grid Mode resizes the grid cells proportionally to fit the window — more cells = smaller pads, fewer cells = larger pads.
- **Scroll Zoom Cycling (`Alt + Mouse Wheel` or `Cmd + Mouse Wheel`):** Turning the scroll wheel while holding down Alt/Cmd will cycle sequentially through all four grid size modes: `GRID_8x16 ➔ GRID_16x16 ➔ GRID_24x16 ➔ GRID_16x24` on the fly!
- **Keyboard Zoom Cycling (`Alt + PageUp / PageDown`):** Instantly cycle through all available grid sizes sequential layout modes using PageUp (forward) and PageDown (backward) hotkeys while holding Alt/Cmd!
- The grid always draws `gridMode.rows` voice row slots in the viewport. If the model has more rows (e.g., a 16-sound TR-808 kit), **vertical scroll buttons (▲/▼)** appear in the CLIP view header.
- The combined MACROS (Macro Sliders) and KEYBOARD rows stay fixed at the bottom regardless of grid mode.
- **SONG** and **ARRANGEMENT** views also respect the grid mode setting — the viewport shows `gridMode.rows` track slots immediately, no need to load a clip first.

**Per-clip step count:**
- Each clip has its own length (default 16, range 1–192). Right-click or double-click the `[N]` badge on the grid row to change it.
- When clip length exceeds `gridMode.columns`, a **horizontal scroll (◀/▶)** appears to pan the visible step window.
- `gridMode.columns` only affects the viewport, not the underlying clip data.

### 1.5 Auditioning
-   Before placing notes in the sequence, you can audition (preview) the sound of a given row.
-   In **Clip Mode**, click the rightmost pad/button of a row to hear the pitch or sound assigned to it.

### 1.6 Velocity View
-   Active step pads are rendered with **velocity-blended colors**: a step with full velocity (1.0) shows the full track color; lower velocities blend the color toward dark gray (`#333333`).
-   The cell text in CLIP mode now shows real values: `Ve:<val>` for velocity and `Pr:<val>` for probability, read directly from the engine.
-   To **set step velocity**, right-click a pad cell to open the **Step Properties** dialog, which includes a Velocity slider (0–100).
-   The combined **MACROS** row provides a 16-parameter vertical mixing deck (LEVEL, PAN, PITCH, FILTER, etc.) for the active track. Click and drag vertically on a cell to adjust its value in real-time. The active value (e.g. 85%, 2.4kHz, or +12) displays directly as a text overlay while dragging, and reverts back to the parameter name upon release.
-   The MIDI track click-path and playhead re-sync also respect the velocity blend, so pad brightness always reflects the current velocity value during playback.

### 1.7 Row-Level Velocity & Probability
-   **Right-click a row label** (the track name on the left of each row) to open the track context menu.
-   **Set Row Probability...**: Opens a dialog (0–100%) that applies the same probability value to all steps in that row. This is useful for introducing controlled randomness across an entire drum sound or voice — each step has an independent `probability%` chance of firing when the playhead passes over it.
-   **Set Row Velocity...**: Opens a dialog (0–100%) that applies the same velocity to all steps in that row. Useful for adjusting the overall dynamics of a sound without editing each step individually.
-   These operations target the currently edited clip's sequence data and respect the per-step column count (clip length).

### 1.8 Horizontal Grid Scrolling & Loop Lengths
- **Always-Enabled High-Visibility Scroller:** A solid 12px horizontal Steps scrollbar with a 4px glowing center path line is permanently active at the bottom of the grid viewport! Drag the scrollbar thumb or use **`Shift + Scroll Wheel`** to scroll across steps pages smoothly!
- **Bottom Step Loop Length Controller Badge `[LENG]`:** Located on the left side of the horizontal scroller! Click or right-click this loop badge, enter a new step length (e.g. 16, 32, 64, or 128 steps) and press enter! This instantly:
  1. Resizes the step and automation arrays inside the Java Object Model clip.
  2. Updates the real-time physical audio bridge track loop step bounds.
  3. Automatically scales the horizontal scrollbar's boundary limits, enabling instant horizontal columns scrolling!
- When a clip is longer than the current viewport, the step range indicator shows which steps are currently visible (e.g. "1–16 / 64").
- The playhead and background step data sync correctly across all rows when the view is scrolled — steps outside the visible window don't show active playback sweeps.
- This works for both Kit and Synth tracks in CLIP mode.

### 1.9 Melodic Pitch Scales & Startup Centering
- Melodic Synth tracks support the full standard **128 MIDI notes range (C1/0 to G9/127)**!
- **Startup scroll focus centering:** Melodic synth tracks scroll focus is automatically calibrated at startup to center on the standard mid-register **`C5` note (MIDI 72 / scrollOffset 55)**. This keeps key mid-octave pitches visible right upon initial boot, avoiding empty pitch registers focus!

### 1.10 Click-path Modifier Key Gestures (Alt, Cmd, Tab Modifiers)
Tuning individual step properties is incredibly fast and direct using keyboard modifier click combinations in CLIP Mode:
- **`Alt + Mouse Click` (Alt-Click) on a step pad:** Directly launches the **Step Properties Dialog** (velocity/gate/iteration panel) instantly, bypassing the double-click/right-click dialog routines!
- **`Cmd + Mouse Click` (or `Ctrl + Click`) on a step pad:** Cycles the step **Probability** levels directly: **100% ➔ 75% ➔ 50% ➔ 25% ➔ 100%**! Toggles the step ON if it was off, and dims cell color intensity to match the probability level instantly.
- **`Tab + Mouse Click` (Tab-Click) on a step pad:** Cycles the step **Velocity** levels directly: **100% ➔ 75% ➔ 50% ➔ 25% ➔ 100%**! Toggles the step ON if it was off, and alters the cell background color velocity blend color instantly so you see volume dynamics directly on the pads deck!

### 1.11 Master Output Resampler Looper
Real-time digital resampling looper capture follows a standard physical workflow:
- **Start Resampling:** Click the **`[● RESAMPLE]`** button on the transport toolbar! The main playhead starts sweep cycles and the transport state turns into a glowing gold **`[● SAMPLING]`** button.
- **Perform:** Click some cell step pads or play notes live. Every audio signal generated by the synthesis engines is captured in perfect digital fidelity.
- **Stop & Auto-Render WAV Loop:** **Click the active `[● SAMPLING]` button again** (or the STOP transport button!). This immediately:
  1. Terminates the PCM capture thread.
  2. Compiles captured blocks into a high-fidelity 16-bit 44.1kHz stereo WAV file.
  3. Saves the WAV file strictly under the physical hardware standard path:
     📂 **`SAMPLES/RESAMPLE/Resample_[Timestamp].wav`** inside your Deluge library folder.
  4. Instantiates a new concrete kit drum track named `"Resample [N]"`, loads your recorded loop, and programs a default 4-on-the-floor beat sequence (Col 0, 4, 8, 12) so it plays back live in tempo sync instantly!

---

## 2. SONG CREATION & LOADING

▌ LOADING AN EXISTING SONG FROM LIBRARY
1. Open the **Project Manager** sidebar on the left.
2. Select the **LIBRARY** tab (this acts as the "SD CARD" explorer).
3. Expand the **SONGS** folder.
4. **Double-click** on a song file (e.g., `song1.xml`) to load it.
5. The status bar will indicate when the song is loaded, and the audio engine will load the associated samples.

▌ PROJECT EXPLORER VS. LIBRARY
-   **PROJECT Tab**: Shows the active tracks and clips in the current project.
-   **LIBRARY Tab**: This is the functional file browser where you can load Kits, Synths, and Songs from resources or external folders.

▌ CREATING A NEW CLIP FROM SONG VIEW
*Note: In our software emulation, rows represent tracks/instruments, and columns represent clip slots.*
1. Switch to **SONG** view using the mode toggle buttons at the top.
2. Click an empty cell in a row labeled `EMPTY`.
3. *Current State*: This feature currently cycles through mock states (`PAT_0`, etc.) and does not yet create a fully functional clip in the project model. This is a known gap.

---

## 3. SONG MODE OPERATIONS

▌ LAUNCHING CLIPS
1. Switch to **SONG** view.
2. Each row corresponds to a track, and columns are clip slots.
3. On the **Right edge** of the grid (Columns 17 and 18), locate the dedicated hardware-style operation pads:
   - **Col 17 (MUTE)**: Toggles channel mute state. Turns Red when Muted. 
     - *Modifier*: `Shift + Click` fully erases the notes programmed in the sequencer track.
   - **Col 18 (EDIT / SOLO)**:
     - *In Clip View*: Acts as a Solo track toggle (Turns Green).
     - *In Song View*: Triggers manual Audition Previews, or focus-switches to the sound EDITOR.

▌ SELECTING A CLIP TO EDIT
1. Switch to **SONG** view.
2. **Click on the row header** (the label on the left showing the clip name) to open it in the Clip Editor.
3. The view will automatically switch to **CLIP** mode, and the Matrix grid will be populated with the sequence from that clip.

▌ MULTI-TAB TRACK INSPECTOR (DESKTOP EXCLUSIVE)
1. **Right-Click** any active pad square inside the arrangement timeline grid.
2. Opens a modular operations inspector deck containing:
   - **PRESETS Tab**: Hot-swapping active instrument sounds references immediately.
   - **CLIPBOARD Tab**: Triggering rapid variations duplicate chains (`Clone Clip`).
   - **MIXER Tab**: Master headroom amplification decks routing attenuation sliders.



▌ OPENING THE SOUND EDITOR
1. In **SONG** view, locate the **E** button on the right side of the track row.
2. Click it to focus and populate the dynamic **EDITOR** tab on the left sidebar, letting you tweak its parameters.

---

▌ MUTING A TRACK
1. In Song View, locate the **[M]** button on the right side of the track row (next to Launch and Color).
2. Click it to mute the track. The button turns yellow.
3. Click it again to unmute.
▌ LOOP-BOUNDARY MUTE (QUEUED MUTES)
1. In Song View, hold the `Shift` key and click the **[M]** mute button on the right side of the track row.
2. The Mute pad will flash, indicating that the mute operation is ARMED and queued.
3. As the playback playhead crosses loop step 0 (the end of the current sequence cycle), the track will fully and instantaneously mute.

▌ INDIVIDUAL ROW MUTE IN KITS
1. When working with a **Kit** clip (where each row is a different sound, like Kick, Snare, etc.), you can mute individual sounds.
2. Click the **M** button on the right side of the row to mute just that sound. It will turn yellow.
3. Click it again to unmute.

▌ SIDECHAIN COMPRESSOR DUCKING
- Active sequencing on **Row 1 (The Kick Drum instrument)** drives a global compressor envelope ducking all collateral channels headroom temporarily (120 milliseconds recovery), establishing timeline pulse pumping aesthetics.
- **Gain Reduction (GR) Meter (Desktop Exclusive)**: A responsive orange bar renders on the visualizer pane viewport edges illustrating live ducking headroom compression.



▌ VERTICAL PAD SHIFTING (RE-ORDERING CLIPS)
1. In **SONG** view, reorganize the vertical stacking of clips by holding the `SHIFT` key and clicking any active Pad.
2. This accelerator shifts the clip block focus up/down displacement targets to group sections structurally.

---


## 4. RECORDING & MIDI SUPPORT

▌ LIVE GRID RECORDING
1. Click the **● REC** button in the Transport panel to enable Record mode.
2. Press **▶ PLAY** to start the playhead.
3. Click cells on the grid. They will act as trigger pads, recording notes at the current playhead position (`currentStep`) rather than toggling the step where you clicked.
4. Recorded notes will appear on the grid and play back in the next loop. Note duration is calculated precisely based on Note-Off events.

▌ MIDI OUTBOUND SEQUENCING
- Sequencer pad notes emit outbound triggers on the first operational hardware port connected via native `RtMidiOut` pipeline integration.
- **Dedicated MIDI Track (Track Type 2)**: Mutes internal software synthesis acoustics to map operations strictly delivering output sequence integers over continuous physical chords pipelines.
- **Outbound Activity LEDs**: Pads flash momentarily yellow providing execution stream indicators on MIDI deliveries.



▌ CLIP STUTTER ROLL ACCELERATOR
- Hold down any active sequence Pad on a drum kit track to repetitively re-trigger sound bursts at division rate intervals (1/8th note rolls).
- Release the pad stops the repeating triggers.

▌ ONE-SHOT (ONCE) TIMELINE CLIPS
- Toggles a track to automatically silence itself after playing exactly one sequence pass loop. 
- `SHIFT + Click` the left row header label text to engage the mode (appends `(1SH)` indicator tag).


▌ MIDI INPUT & LEARN
1. Open **Settings > Preferences...** to select your MIDI Input device from the dropdown.
2. To bind a physical controller to a parameter:
   - Right-click any slider in the **MASTER FX** panel (e.g., Reverb Mix or Size).
   - Select **MIDI Learn** from the context menu.
   - Move a physical knob or fader on your MIDI controller.
3. The CC number is captured and bound to that parameter. Mappings are saved persistently across sessions.

▌ CLEARING A MAPPING (UNLEARN)
1. Right-click the slider you want to unbind in the **MASTER FX** panel.
2. Select **Clear MIDI Mapping** from the context menu.
3. The parameter will stop responding to the MIDI controller, and the mapping is removed from preferences.

▌ DISPLAYING ACTIVE MAPPINGS
1. Go to **Settings > Preferences...** in the menu.
2. A list at the bottom of the dialog will show all active mappings in the format `parameter -> CC number`.

▌ MIDI GRID CONTROLLER MODE
1. Go to **Settings > Preferences...** and check **"MIDI Grid Mode"**.
2. In this mode, incoming MIDI notes are mapped to grid coordinates (Row = note / 16, Column = note % 16).
3. Pressing a pad on your controller will toggle the step on the grid instead of playing a note!

▌ EXAMPLE: SETTING UP A KORG NANOKONTROL2
1. Connect your Korg nanoKONTROL2 via USB.
2. Open **Settings > Preferences...** in the menu.
3. In the **MIDI Input** dropdown, look for **"Slider/Knob"** (or "nanoKONTROL2" if explicitly named). On some systems, generic names like "Slider/Knob" are used for class-compliant controllers.
4. Select it, click OK, and **restart the application**.
5. Right-click the **Reverb Mix** slider in the **MASTER FX** panel and select **MIDI Learn**.
6. Move a fader on the nanoKONTROL2. The on-screen slider will now follow your physical movements!

---

## 5. AUTO-SCROLLING

▌ FOLLOWING THE PLAYHEAD
- The grid automatically supports **auto-scrolling** to follow the playhead across pages.
- When the playhead moves past step 15, the view shifts to show steps 16-31, and so on.
- This ensures you always see the active step during long sequences or live recording.

---

## 6. VISUAL WALKTHROUGH (E2E SCENARIO)

This section provides a step-by-step visual guide to a common user scenario: loading a song, editing steps, and controlling playback.

### Step 0: Initial State
When the application starts, the grid is empty, and the transport is stopped.
![Initial State](step0_start.png)

### Step 1: Song View
After double-clicking `song1.xml` in the Library explorer, the Song View opens, showing active clips as colored pads.
![Song View](step1_loaded_songview.png)

### Step 2: Clip View
Double-clicking a clip in Song View opens it in the Clip Editor, showing the note sequence on the grid.
![Clip View](step1_loaded_clipview.png)

### Step 3: Editing Cells
Clicking on cells or receiving MIDI Grid notes toggles the steps. Here, steps 0, 4, 8, and 12 are enabled on the first track.
![Cells Edited](step2_edited.png)

### Step 4: Playing
Pressing the **▶ PLAY** button starts the playhead moving across the grid.
![Playing](step3_playing.png)

### Step 5: Recording
Pressing the **● REC** button enables live recording mode. Incoming notes will be captured at the current step.
![Recording](step4_recording.png)

### Preferences Dialog
The application preferences can be accessed via **Settings > Preferences...**.
![Preferences Dialog](preferences_dialog_annotated.png)

#### Hardware Character Preferences

The Deluge hardware (v1.3.1+) has a distinct analog character that the clean 32-bit float emulation doesn't naturally reproduce. These preferences enable select hardware behaviors for more authentic audio output:

| Preference | Description | Default |
|-----------|-------------|---------|
| **Master Saturation** | Enables tanh-style soft-clipping on the master summing bus, mimicking the hardware's analog saturation when the bus approaches 80%+ of full scale. Uses a Distortion UGen with atan-based overdrive curve. | Off |
| **Filter Drive (v1.3.1+)** | Boosts SVF filter drive to 1.8 (from the default 1.0), activating the post-filter tanh non-linearity present in firmware v1.3.1+. Applied per-voice for both synth and kit tracks. The `drive` parameter value used is the maximum of the track's programmed filter drive and 1.8. | Off |
| **14-bit DAC Crunch** | Truncates the 32-bit float output to 14-bit resolution (16384 levels) with TPDF dither, simulating the hardware's 14-bit DAC precision. Subtle at high signal levels, most audible on low-level tails and reverb decay. | Off |
| **Reverb Model** | Selects the reverb algorithm. **RingsReverb** (index 4) is a physical-modeling reverb based on the Rings resonator — notably different from the standard reverb types. Options: JCRev, FreeVerb, MVerb, ProceduralReverb, RingsReverb. | JCRev |

These preferences push float globals (`g_masterSat`, `g_charFilterDrive`, `g_bitCrunch`) to the ChucK engine each time a song loads. The engine reads them per-tick and applies the corresponding processing. Toggling a preference takes effect on the next song load.

### Preset Sound Editor
Clicking the **E** button in Song View or the **🎹 EDIT PRESET** button at the bottom of the Clip View opens the Sound Editor.
![Preset Sound Editor](preset_editor_annotated.png)


---

## 7. QUICK REFERENCE (SOFTWARE EQUIVALENTS)

Here is a map of common hardware actions to their software equivalents in our emulation:

| Hardware Action | Software UI Equivalent |
| :--- | :--- |
| **Audition Pad** (Far Right) | Click the Audition button `>` on the left of the row. |
| **Toggle Step** | Click any cell in the 16x8 grid (Clip Mode). |
| **Launch Clip** | Click Pad 18 on the right edge of the grid (Song Mode). |
| **Mute Track** | Click Pad 17 on the right edge of the grid. (`Shift + Click` clears notes). |
| **Change Color** | Right-Click the Track Row label text (Far Left). |
| **QWERTY Piano Play** | Press keyboard rows `Z-M` (White keys) and `S-J` (Black keys). |

---

## 8. SOUND EDITORS & CONTROLS WORKFLOW (MANUAL)

This section highlights all Sound and parameters editors workspace validation checks dynamically and each of their individual fields. 

### 8.1 Sidebar Sound Preset Editor 
Exposes synthesis preset sound parameters categorized inside visually premium dynamic folding Accordion categorisation groupings workspace: 
#### Category: OSCILLATORS
Contains inner sub accordions categorization grouping setups: 
- **OSCILLATOR 1 & 2**:
  - **Type**: Wave generator live inputs choices (SINE, SAW, SQUARE, TRIANGLE, SAMPLE, DX7 FM SYNTH). 
 
  - **Volume**: Individual oscillator amplitude level values. 
  - **Transpose**: Individual transposition values.
  - **Sync Checkbox**: Syncs with live frequency retrigger phase assignments bounds rules checks. 
  - **Pulse Width**: live dynamic pulse duty width slider. 
  - **Retrig phase**: note trigger phase degrees (0 to 360). 
- **MODULATOR 1 & 2 (FM Synthesis)**: 
  - **Transpose**: FM transposition values. 
  - **Amount**: visual modular amounts depth level. 
  - **Feedback**: Amplitude modulator live feedback assignments depth levels. 
  - **Carrier FB**: Carrier self-feedback (carrier1Feedback, carrier2Feedback), 0-100%.
  - **Modulator1 FB**: Modulator 1 feedback depth.
  - **Modulator2 Amt/ FB**: Modulator 2 amount and feedback depth.
  - **Destination (on MOD 2 only)**: destination combos logic dropdown (CARS, MOD1). 

#### Category: FILTERS 
- **LPF Frequency**: Low-pass cutoff frequency dynamic values. 
- **LPF Resonance**: filter frequency visual resonance cutoff depth. 

#### Category: MASTER FX 
- **Delay Amount**: visual Delay depth amount depth. 
- **Reverb Amount**: visual live Reverb depth values. 

> [!NOTE]
> Both Oscillator 1 volume and Low pass filter Cutoff parameters are modulated with dynamic live interactive patching logic visual matrix popups depth values assignments bounds. Click the Little dynamic **`M`** button next to parameter inputs values sliders to open live depth modulation matrices depth assignments popups. Modulations sources: LFO1, LFO2, ENV1, ENV2, RANDOM probabilities odds paths, dynamic velocities, step sidechains compressor levels assignments. 

---

### 8.2 Step effects Parameter visual visualizer lane Workspace 
Sitting dynamic line-by-line alignment underneath the sequence Matrix Grid workspace. Tapping visual path over step effects visualizer bars canvas workspace enables live visual dynamic automation drawing paths. Parameters steps and cutoff values supported: 
- VELOCITY, GATE note duration timing percentage 
- PITCH transposition, PROBABILITY odds
- FILTER sweeps cutoff paths automation, RESONANCE 
- PAN sweeps, master delay and master reverb amount depth paths live logic validation bounds checks 

---

### 8.3 Global Visual parameters Workspace lane
Sitting underneath the sequence visual dynamic Step effects visualizer lane visual workspace. Exposes track global visual sliders: 
- **TRACK LEVEL**: Controls active global track level volume. 
- **TRANSPOSE**: track global scale note transpose level values. 

---

### 8.4 live visual Euclidean visual visualizer arpeggiation popup
Press active Grid Workspace **`P`** validation onboard key. dynamic Visual step Euclidean rhythm live step sequencer distributions dynamically: 
- **Hits ($K$)**: Active sequencing notes active. 
- **Steps ($N$)**: Sequence duration validation checks duration. 
- **Offset ($O$)**: Step sequence horizontal timeline shift offset. 

---

### 8.5 Synth Config Dialog — Modulation Tab

Accessed via **Synth → Configure...** in the application menu. The dialog has 4 tabs:

**OSCILLATOR & FILTER (Tab 1)**: Oscillator type/volume/sync/PW/retrig phase per oscillator, LPF/HPF freq+resonance, filter mode (12dB/24dB/SVF), unison count/detune, portamento, master pan/volume, synth mode (subtractive/FM/ringmod), polyphony (poly/mono/legato).

**FM SYNTHESIS (Tab 2)**: FM modulator transpose/amount, carrier feedback, modulator1 feedback, modulator2 amount+feedback, destination routing.

**ENVELOPES (Tab 3)**: ADSR sliders for Envelope 1-4.

**MODULATION (Tab 4)**: Patch cable routing table and mod knob grid.

#### Patch Cables
- Source options: velocity, envelope1, envelope2, lfo1, lfo2, aftertouch, note, random, sidechain
- Destination options: volume, pan, lpfFrequency, lpfResonance, oscAVolume, oscBVolume, pitch, noiseVolume, modFxRate, modFxDepth
- Amount slider per cable (0-100%)
- Add/Remove buttons for cable rows

#### Mod Knobs
- 4×4 grid of 16 knob param selectors
- Options: NONE, volume, pan, reverb, delay, lpfFrequency, lpfResonance, hpfFrequency, pitch, oscAVolume, oscBVolume, noiseVolume, modFxRate, modFxDepth, modFxFeedback

### 8.6 Kit Assembly From Synth Presets

**File → Assemble Kit From Synths...** selects multiple `.XML` synth preset files and generates a single `.KIT` XML where each synth becomes a lane sound.

Flow:
1. Multi-file chooser for synth preset XMLs (filtered to `.XML`)
2. Per-lane configuration dialog: mute group, pitch offset (semitones), lane name
3. Output save dialog for the `.KIT` XML file
4. The generated kit references each synth preset via `<sample fileName>` — compatible with Deluge hardware rendering

---

## 9. PLANNED FEATURES / FUTURE VISION

*Note: The following features are described in the original design documents but are **not yet implemented** in the current software version. They represent the roadmap for future development.*

### 8.1 Advanced Object Model
-   **Clips**: Duplicate via `Alt + Drag`.
-   **Synth/Kit**: Edit via a full Node-based Graph Editor.

### 8.2 Advanced Interface Concepts
-   **Marquee Selection**: Click and drag a box over notes or clips to select multiple objects.
-   **Shift + Drag**: Disable "Snap to Grid" for fine-grained, humanized timing.
-   **Drag-and-Drop**: Drag Synth/Kit XMLs directly from the library onto clips to load presets.

### 8.3 Visual Editors
-   **OSC & FM Matrix**: A node-based editor for connecting operators via drag-and-drop.
-   **Multisampling Editor**: A waveform view where users drag `.wav` files onto a virtual piano keyboard for automatic pitch mapping.
-   **Automation Graphs**: Draw Bezier curves over the grid for parameter modulation.

### 8.4 Planned Shortcuts
-   `Ctrl + Mouse Wheel`: Zoom (Horizontal).
-   `Shift + Mouse Wheel`: Zoom (Vertical).
-   `Middle-click + Drag`: Scroll (Hand Tool).
-   `P` key: Open visual arpeggiator pattern editor with Euclidean toggle.
---

## 10. SWING UI EDITION REFERENCE

A lightweight, pure Java desktop UI alternative built entirely on Swing (accessible by launching with the `--swing` argument). Ideal for environments with native layout render bottlenecks.

▌ INTERFACE TOPOGRAPHY
- **Grid Layout partitions (GridBagLayout)**:
    - **Top strip**: Houses full modular views switches buttons (CLIP, SONG, ARR), global sequence controls (Play, Stop, Rec, Load XML), and speed modulators (BPM, Swing, Master volume sliders).
    - **Center Viewports canvas**: Confined behind centered scrollbars containment viewports locking cell pads proportions securely.
    - **Southern strip arrays**: Spans stacked parameter modifier ribbons layer toggling graphical draws step plots.

▌ VISUAL WALKTHROUGH (SWING)
- **Step 0: Pure Swing Workspace Launch**
  ![Swing Initial State](../docs/swing_step0_start.png)

- **Step 1: Song View (Loaded project)**
  ![Swing Loaded State](../docs/swing_step1_loaded_songview.png)

- **Step 1b: Clip View (Sequencer Editor pads)**
  ![Swing Clip View](../docs/swing_step1_loaded_clipview.png)

### 10.4 Shift Key Parameters Overlay & Real-Time Editing (Hardware Parity)

Our Swing desktop edition incorporates a complete emulation of the physical Deluge's **Shift Key Parameters Grid Shortcut** layout, translating physical hardware key-combos into a modern desktop workflow. You can toggle your preferred behavior via **Settings > Preferences...** → **Shift Shortcut Style**:
*   **Desktop Slider (Default):** Clicking a parameter pad cell pops up a standard neon JSlider next to your cursor for local mouse adjustments. Clicking away instantly commits changes and dismisses the slider.
*   **Hardware Rotary:** Replicates the physical gold encoders workflow. Clicking a pad cell highlights it with a thick golden focus border and locks that parameter to the top-bar's virtual **SELECT Gold Dial Encoder**. You can rotate the select dial (or use your computer keyboard's **Up/Down Arrow keys** / **Mouse Scroll Wheel**) to slide values in real-time, displaying parameters on our retro amber character LED display! Releasing the Shift key instantly commits the change and releases pad focus.

*   **Global Keyboard Shift Detection:** Pressing and holding the physical **`Shift`** key on your computer keyboard instantly hooks into the global key dispatcher, prompting the sequencer matrix grid to shift visual modes.
*   **Backlit Color Partitions Overlay:** The step cells grid temporarily replaces active sequence gates with dynamic, glowing, color-coded functional columns matching the official physical guidelines card:
    *   **Columns 0–1 (Peach):** `SMPL1` & `SMPL2` sample-playback parameters.
    *   **Columns 2–3 (Coral Red):** `OSC1` & `OSC2` subtractive oscillator parameters.
    *   **Columns 4–5 (Yellow):** `FM1` & `FM2` operators and distortion parameters.
    *   **Column 6 (Slate Blue):** `MASTER` track and main panning levels.
    *   **Column 7 (Deep Blue):** `VOICE` parameters (Unison count, detune, polyphony, glide).
    *   **Column 8 (Beige):** `ENV 1` ADSR (Volume envelope).
    *   **Column 9 (Orange):** `ENV 2` ADSR (Filter envelope).
    *   **Column 10 (Pink):** `SDCHAIN` sidechain ducking settings.
    *   **Column 11 (Bright Yellow/Green):** `ARP` arpeggiator modes and rates.
    *   **Columns 12–13 (Light Green/Soft Blue):** `LFO 1` & `LFO 2` modulators.
    *   **Column 14 (Soft Red):** `DELAY` sends.
    *   **Column 15 (Ochre/Gold):** `MOD AMT` modulation depth matrix.
*   **Two-Line Split Label Centering:** Each pad button displays the exact physical parameter label name (e.g., `"WAVE FORM"`, `"RESONANCE"`, `"BASS GAIN"`, `"UNISON DETUNE"`) using an advanced word-splitting vertical text layout that centers long labels inside the small glowing cells perfectly.
*   **Quick Parameter Edit Popups (JSliders):** Clicking any active parameter pad cell while holding Shift summons a highly responsive **JPopupMenu** styled in our premium dark-neon format. This popup displays:
    *   A bold cyan title showing the active parameter name.
    *   A horizontal **JSlider** mapping the parameter values (e.g. logarithmic frequency mappings for LPF/HPF cutoffs, precise decimals for attack/decay timings, or percentages for sustain levels).
    *   A status text display showing real-time formatted values (e.g., `"1420 Hz"`, `"0.24 s"`, `"75%"`).
    *   *Real-time Audition & Confirmation:* Sliding the fader immediately re-registers active values in our track model and pushes changes to the JNI playing audio engine, providing instant sonic feedback. Simply **release the mouse and click anywhere else** to confirm values and auto-dismiss the popup menu!
*   **Dynamic Parameter Applicability Guard:** The grid automatically queries the active track model to verify if the parameter shortcut is supported:
    *   *Synth Tracks:* All parameters are fully active.
    *   *Kit & Audio Tracks:* All synth-specific parameters are automatically **greyed out and dimmed** (painted in a dark desaturated charcoal gray with faint gray labels). Only master track sends and parameters (Track Level Volume, Pan, Reverb Size, Delay Rate, Sidechain sends) remain active and interactive.
    *   *Non-Disruptive Warnings:* Clicking any disabled greyed-out cell pops up a red-glowing **"PARAMETER NOT APPLICABLE"** transient label right next to your cursor, which automatically vanishes in 1.5 seconds without intercepting focus or locking user interactions.

---

## 11. FORMAL UI DESIGN SPECIFICATION

### 11.1 Architectural Layout (Framework-Independent)
The UI follows a classic Digital Audio Workstation (DAW) topography, divided into five primary regions using a border-layout or grid-bag approach:
1. **Top Panel**: Transport & Global Controls
2. **Left Panel**: Navigation & Preset Editing (Sidebar)
3. **Center Panel**: The Matrix Grid (Primary Workspace)
4. **Right Panel**: Real-time Audio Visualizers
5. **Bottom Panel**: Step Modifiers & Global Track Controls

### 11.2 Component Breakdown

#### Top Panel (Header Strip)
*   **Mode Toggles**: A distinct button group to switch the Center Workspace context (`CLIP`, `SONG`, `ARR`).
*   **Transport Controls**:
    *   `PLAY` (Green accent, playback toggle)
    *   `STOP` (Red accent, playback stop/reset)
    *   `REC` (Dark with red indicator, arm recording)
*   **Global Parameters**:
    *   `TEMPO` / `BPM` (Slider with numeric readout, e.g., 120.0)
    *   `SWING %` (Slider, 0-100%)
    *   `MASTER VOL` (Slider)
*   **Action Buttons**: `LOAD XML` for loading legacy or external project files.

#### Left Panel (Sidebar)
*   **Tabbed Interface**:
    *   **LIBRARY Tab**: A tree/hierarchical view displaying the file system (SD CARD) with folders for `KITS`, `SYNTHS`, `SONGS`, and `EXTERNAL SAMPLES`. Double-clicking a file loads it into the global model.
    *   **EDITOR Tab**: A dynamic property inspector. When a track is selected, it displays nested accordion menus:
        - **OSCILLATORS**: Type select (Sine, Saw, Square, Triangle, Noise), Volume, Transposition, and Pulse Width.
        - **MODULATORS**: Modulator matrix depth (FM synthesis amounts).
        - **FILTERS**: Low-pass and High-pass Cutoff sweeps, filter types select.
        - **ENVELOPES**: Complete Attack, Decay, Release, and Sustain ADSR curve limits.
        - **DISTORTIONS**: Bit-depth decimation limits and drive limits.
        - **MASTER FX**: Global visual reverb room sizes and delays send loops.

    *   **MIDI Tab**: (Primarily in Swing) For mapping and routing overviews.

#### Center Panel (The Matrix)
*   **The Grid**: A responsive 2D matrix (default 8 rows x 16 columns) representing step pads.
    *   **Row Headers (Left side of grid)**: Labels for the row (e.g., "EMPTY", "PAD 1", or Note Names). Includes a quick Audition `>` button to preview the row's sound.
    *   **Track Controls (Right side of grid)**: Dedicated pads at Column 17 (MUTE - toggles track activity) and Column 18 (EDIT / SOLO).


*   **Bottom Anchor (Clip Mode)**:
    *   **Piano Roll**: A clickable piano keyboard graphic spanning the width of the grid, enabling melodic input. Includes a horizontal scrollbar above it to page through time (auto-scrolling tracks the playhead).
*   **Parameter Ribbon**: A horizontal strip of toggle buttons immediately below the grid used to select the active Step Effects lane (e.g., `LEVEL`, `PAN`, `PITCH`, `FILTER`, `RESONANCE`, `MOD FX`, `DELAY`, `REVERB`, `PROBABILITY`, `GATE`, `VELOCITY`, `START/END`).

#### Right Panel (Visualizer Stack)
A vertically stacked column of four real-time graphical scopes mapping to the master audio bus:
1.  **Spectrum** (FFT Analysis)
2.  **Oscilloscope** (Waveform Time-Domain)
3.  **Waterfall** (3D Spectrogram)
4.  **Stereo Phase** (Goniometer / Lissajous curve)

#### Bottom Panel (Step Effects & Status)
*   **Step Effects Visualizer Lane**: A 16-step horizontal bar chart corresponding to the grid's columns. When a parameter (like Velocity or Pitch) is selected in the Ribbon above, this lane allows the user to draw dynamic per-step automation.
*   **Track Global Controls**:
    *   `TRACK LEVEL` slider.
    *   `TRANSPOSE` slider (ranged -24 to +24).
    *   `GLOBAL TEMPO` slider (mirrors top panel or acts as override).
    *   `SCALE` dropdown (e.g., "Major", "Minor").
*   **Status Bar (Footer)**:
    *   System messaging ("Waiting for Engine...", "MIDI: Ready").
    *   Application Branding module ("DELUGE").
    *   Performance metrics ("SHREDS: 0", indicating active concurrent Chuck threads).

### 11.3 Core Interaction Paradigms
*   **Contextual Drill-Down**: Double-clicking a track/clip row in Song Mode immediately shifts the view to Clip Mode, populating the matrix with that clip's specific step data.
*   **Grid Toggling & Recording**: Single clicks on grid cells toggle step activation. When Record (`REC`) is active, clicking cells acts as live trigger pads. In "MIDI Grid mode", incoming physical MIDI notes map directly to grid coordinates.
*   **Continuous Automation Drawing**: Clicking and dragging across the Step Effects Visualizer Lane (Bottom Panel) draws a curve for the selected parameter.
*   **MIDI Learn Context Menus**: Right-clicking virtually any slider (e.g., Reverb Mix) triggers a context menu to map or clear a physical MIDI CC assignment.

### 11.5 Logical Model Connections (The Bridge Contract)

The User Interface interacts with the sample-accurate synthesis engine entirely via shared static memory arrays managed by `org.chuck.deluge.BridgeContract`. There is no network overhead; UI changes map instantly to ChucK virtual machine shreds.

#### Global Scalars (Live bindings)
- `g_bpm` (Float): Master tempo. Mapped to slider drag events on the top transport ribbon.
- `g_swing` (Float): Shuffle ratio (0.5 = straight, 0.75 = heavy swing).
- `g_play` (Integer): Playback trigger bit. (`0` = stop, `1` = play).
- `g_current_step` (Integer): Read-only sequence step cursor. Drives playhead highlights framing rendering cycles.
- `g_master_vol` (Float): Output master audio gain attenuation.

#### Distributed Shared Arrays (Midi/Automation state mapping)
- `g_pattern` [Size: 128] (Integer): 1/0 bitmask storing note activity. Index computed via `trackIdx * 16 + stepIdx`.
- `g_velocity` [Size: 128] (Float): Per-step velocity automation values.
- `g_filter` [Size: 16] (Float pairs): Consecutive track frequency cutoff and resonance values tuples. 

### 11.6 Application State & Preferences Registry

Persistent preferences are handled through `org.chuck.deluge.project.PreferencesManager` and configure hardware boundaries on application boot cycles:
- `midi.input` (String): Descriptors name of incoming physical interface hardware port.
- `reverb.model` (String): Algorithmic architecture selection switch (`JCRev`, `FreeVerb`, `MVerb`, `ProceduralReverb`, `RingsReverb`).
- `show.visualizers` (Boolean): GPU acceleration optimization toggles gating FFT analyzer pipeline loops.
- `preset.linking.policy` (String): Governs composition save files structure (`EMBED` vs `LINK_LIVE` references).

> [!WARNING]
> **Hardware Parity Compatibility Constraint**:
> - `EMBED` is the default factory configuration. It guarantees full backward compatibility with official Synthstrom Deluge physical hardware.
> - Switching to `LINK_LIVE` means Songs will store file references instead of duplicate definitions. 
> - **Side Effects**: Exporting `.xml` Songs built with `LINK_LIVE` policy onto an SD Card to be loaded on real Deluge hardware will fail to recognize sound generators, as the native unit expects embedded parameters explicitly.



---


## 13. PERSISTENCE & STATE STORAGE SPECIFICATION

### 13.1 Directory structure resolution
The application reads and writes configurations matching standard local hierarchy mappings:
```text
[App Root Path or preferences directory mapping]
├── SONGS/          # Holds Master Project configuration state triggers .xml 
├── KITS/           # Holds instrument percussion elements allocations .xml
├── SYNTHS/         # Holds instrument synthesis node parameters .xml
└── SAMPLES/        # Binary PCM .wav / .aif acoustic streams assets
```

### 13.2 Saving streams execution
- **Songs backup triggers**: Bound across Master navigation ribbon setups providing manual serializations loops.
- **Active presets exports**: Triggered atop editing dials drawer pipelines backing updates to disk pointers.

---

## 14. ADVANCED ENGINEERING SCHEMATICS

### 14.1 Concurrent Thread Boundaries
To maintain zero-latency graphics response alongside sample-accurate synthesis timings, the application spans three isolated thread realms governed through lock-free boundaries:

```mermaid
graph TD
    subgraph "Java UI Thread Realm (Swing / JavaFX)"
        UI[Interaction Handlers] -->|Update| Arrays[Shared Memory BridgeContract]
    end
    subgraph "Virtual Machine audio Thread Realm (ChucK)"
        Arrays -->|Poll| Spork[Distributed Shreds]
        Spork -->|Compute| Audio[Sample buffers]
    end
    subgraph "Hardware Controller Realm (Midi In)"
        Midi[Hardware Port] -->|Interrupt| UI
    end
```

### 14.2 Deployment Pad layouts geometry
- **Pad Aspect bounding boxes ratios**: Preserved at `1:1` squares mapping matrix grids spans symmetrically. 

---

## 15. CHUCK LIVE NODE INSTRUMENTS (THE [C] CLIP)

Bypassing fixed subtractive synthesis XML models, the workstation enables hosting raw algorithmic synthesis scripts directly sequenced inside arrangement grids timelines.

### 15.1 Serialization schema
Song projects store file paths pointers resolving execution dependencies at load times:
```xml
<instrument type="CHUCK">
  <scriptPath>/CHUCKS/custom_fm.ck</scriptPath>
</instrument>
```

### 15.2 Debug Execution Log Consoles
Workstation sidebar editors expose script execution stdout readouts catching compilation syntax errors diagnostic feedback securely.

## 16. SWING DESKTOP DELUXE UX POWER-UP SUITE

To elevate our pure Swing desktop workstation far past the physical interactive boundaries of the actual hardware, the desktop version includes the premium **Deluxe UX Power-Up Suite** tools:

### 16.1 Shift Hover Previews & Cursor Tooltips
- **Dynamic Parameter Scanning**: Hold down the `Shift` key and hover the cursor across any backlit cell rows on the grid sequencer matrix. The center status segment display instantly lists the target param short code (e.g. `CUT` for filter cutoff, `ATK` for envelope attack) and its active float/decimal value.
- **Gold SELECT Encoder Mode**: Toggled in preferences setting cards, this locks focus to the top bar's retro digital SELECT dial. While focused, parameter properties can be tweaked continuously using the mouse scroll wheel, physical system keyboard `Up/Down Arrow` keys, or SELECT dial drag movements.
- **Popup JSlider Mode**: Summons a dark-neon quick adjustment slider popup right next to the cursor for rapid parameter offsets, auto-committing and dismissing when clicking away.

### 16.2 Row VU Audio Level Indicators
- **Dynamic Track Level Activity**: Inactive track rows sit silently. When a note triggers or playback steps are active, a dedicated high-fidelity green-orange-red horizontal level meter sits inside the track row label card, bouncing down with smooth exponential sound decay calculations at 30fps.
- **Decay Physics Calibration**: Zones represent real sound fields: standard active normal range (0-65% green), headroom buffer zone (65-85% orange), and hardware transient clipping zone (85-100% bright red).

### 16.3 Step Probability Heatmaps
- **Sequence Density Glow**: Note step backlights reflect dynamic random chance values. Active 100% chance notes shine with bright, intense solid track colors. Dimmer, semi-translucent steps represent lower probability thresholds (e.g., a faint mist glow for 10-25% chance of firing), letting your eyes scan sequence density instantly.

### 16.4 Alt-Drag Step Cloning & Drag-and-Drop Hot-Swapping
- **Alt-Drag Step Copy**: Hold down the `Alt/Option` key while dragging any sequencer cell pad horizontally or vertically. A glowing cyan preview outline trails your cursor. Releasing the mouse button over any destination cell instantly duplicates the complete step payload state, pitch offsets, velocity bounds, probability, and custom modulation parameters!
- **System Sample Drag-and-Drop**: Drag raw audio sound files (`.wav` or `.aif` PCM files) from your system explorer or Finder, and drop them directly onto the row label card of any Audio track lane or Drum Kit slot row. The sound sample is hot-swapped in real time JNI-free, printing a brief `[ SMPL SWAP ]` LED welcome status display!

### 16.5 Waveform Lane Backdrops
- **Timeline Transient Alignment**: A symmetrical, translucent green vector audio mirror envelope waveform path is rendered inside the background canvas of each active row lane. This lets you visually inspect sample transients, align steps exactly with beats peaks, and build precise sequences.
