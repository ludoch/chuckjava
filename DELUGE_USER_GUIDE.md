# ChucK-Java Deluge: Complete Reference Manual

Version 1.9 — April 2026 (Compatible with Production Engine v1.7)

---

## 1. Introduction & Philosophy
The ChucK-Java Deluge Emulator is a high-fidelity recreation of the Synthstrom Audible Deluge 4.1 workflow. It combines the tactile immediacy of the hardware grid with the limitless sound-generation capabilities of the ChucK programming language.

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

## 3. The Synthesis & Audio Engine (v1.7)

### 3.1 FM Synth Architecture
Each Synth track uses a dual-operator FM architecture:
*   **Carrier**: Sine/Saw Morphing oscillator.
*   **Modulator**: Internal frequency modulator.
*   **FM Ratio/Amount**: **Per-track** parameters for unique harmonic complexity on every voice.

### 3.2 Dynamic Sidechaining
Hardwired to **Track 0 (KICK)**. Every kick trigger broadcasts a `sidechain_event` which dips the synth bus volume.

---

## 4. Asset Management & Presets

### 4.1 The Unified Asset Browser
Click **📂 LOAD XML** in the transport row to open the searchable OLED browser.
*   **Active Track Loading**: The browser automatically identifies your currently selected track. If it's a Synth (Rows 5-8), it shows Synth Presets; if it's a Kit (Rows 1-4), it shows Drum Kits.
*   **Search**: Type into the search bar to instantly filter the library (e.g., "808" or "Bass").
*   **Categories**: 
    *   **[F] Factory**: Hundreds of bundled official Deluge sounds loaded directly from the JAR.
    *   **[U] User**: Custom sounds saved by the user.

### 4.2 Saving Custom Sounds
Open the **⚙ (Gear Icon)** on any Synth track and click **💾 SAVE PRESET**. 
*   Saved files are stored in the local `/presets` folder.
*   They will automatically appear in the Asset Browser under the **[U]** prefix.

---

## 5. Sound Editor Reference (Nested Menus)

Click the **⚙ (Gear Icon)** on any track to access deep parameters.

### **ARPEGGIATOR**
*   **ON / OFF**: Toggle per-track melodic animation.
*   **RATE**: Set rhythmic speed (0.25x to 4.0x).
*   **OCTAVES**: Range of pattern (1 to 4 octaves).

### **FILTER**
*   **MODE**: LPF 12dB, LPF 24dB, HPF, BPF.
*   **CUTOFF**: Master frequency offset.
*   **RES (Q)**: Filter resonance intensity.

### **FM SYNTHESIS**
*   **RATIO**: Modulator frequency multiplier.
*   **AMOUNT**: Modulation intensity (FM Index).

---

## 6. Sequencing & Recording

### 6.1 Step Sequencing & Play Conditions
*   **Note Toggle**: Single left-click.
*   **Conditionals (Iteration Dependence)**: 
    *   Set notes to only play on specific iterations (e.g., "1 of 2").
    *   **Logic**: Includes "PREV" (plays if previous note did) and "FILL" (plays only when ribbon FILL is held).

### 6.2 Live Automation Recording
Enable **● REC**, press **▶ PLAY**, and perform **Vertical Drags**. The engine captures movement into the sequence steps in real-time.

---

## 7. MIDI & Hardware Integration

Incoming MIDI notes are routed to the **active track** in the UI. Binding hardware knobs is done via **Right-Click -> MIDI Learn** on any ribbon button.

---

## 8. Quick Reference (Popular Commands)

| Category | Interaction | Function |
| :--- | :--- | :--- |
| **Asset** | 📂 LOAD XML | Open Searchable Asset Browser |
| **Asset** | 💾 SAVE PRESET | Save current sound to /presets |
| **Grid** | Left-Click Pad | Toggle Step Note On/Off |
| **Grid** | Vertical Drag Pad | Lock Parameter Value to Step |
| **Track** | ⚙ Gear Icon | Open OLED Sound Editor |
| **Perform** | Hold STUTTER | Trigger High-speed Beat Repeat |
| **Song** | Click Section A-H | Launch Entire Column (Quantized) |

---

*ChucK-Java Deluge Manual — Version 1.9 — April 2026*
