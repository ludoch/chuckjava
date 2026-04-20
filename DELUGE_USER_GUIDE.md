# ChucK-Java Deluge: Complete Reference Manual

Version 1.8 — April 2026 (Compatible with Production Engine v1.5)

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

## 3. The Synthesis & Audio Engine (v1.5)

### 3.1 FM Synth Architecture
Each Synth track uses a dual-operator FM architecture:
*   **Carrier**: Sine/Saw Morphing oscillator.
*   **Modulator**: Internal frequency modulator.
*   **FM Ratio/Amount**: Global parameters for harmonic complexity.

### 3.2 Dynamic Sidechaining
Hardwired to **Track 0 (KICK)**. Every kick trigger broadcasts a `sidechain_event` which dips the `g_synth_bus` volume.

---

## 4. Sound Editor Reference (Nested Menus)

Click the **⚙ (Gear Icon)** on any track to access deep parameters.

### **OSC (Oscillators)**
*   **TYPE**: Sine, Saw, Triangle, Square.
*   **PW (Pulse Width)**: Active for Square waves.
*   **SYNC**: Hard-sync modulator to carrier.

### **FILT (Filters)**
*   **MODE**: LPF 12dB, LPF 24dB, HPF, BPF.
*   **ENV AMT**: Intensity of ENV2 modulation on cutoff.
*   **TRACKING**: Cutoff increases/decreases based on note pitch.

### **ENV (Envelopes)**
*   **ENV 1 (Amp)**: Main volume ADSR.
*   **ENV 2 (Mod)**: Dedicated filter/pitch ADSR.
*   **VELO -> ENV**: How much velocity scales the envelope peak.

### **LFO (Low Frequency Oscillators)**
*   **SHAPE**: Sine, Triangle, Square, Saw, Random.
*   **SYNC**: Sync to project BPM.
*   **RETRIG**: Toggle if LFO restarts on every Note-On.

---

## 5. Sequencing & Recording

### 5.1 Step Sequencing & Play Conditions
*   **Note Toggle**: Single left-click.
*   **Conditionals (Iteration Dependence)**: 
    *   Set notes to only play on specific iterations (e.g., "1 of 2").
    *   **Logic**: Includes "PREV" (plays if previous note did) and "FILL" (plays only when ribbon FILL is held).
*   **Vertical Drag**: Parameter Lock value to that specific step.

### 5.2 Live Automation Recording
Enable **● REC**, press **▶ PLAY**, and perform **Vertical Drags**. The engine captures movement into the sequence steps in real-time.

---

## 6. Arpeggiator & Performance

### 6.1 Arpeggiator (Nested in Sound Menu)
*   **MODE**: Up, Down, Up/Down, Random, As-Played.
*   **RATE**: Rhythmic speed (1/16, 1/8T, etc.).
*   **OCTAVE**: Range (1 to 4).

### 6.2 Momentary Effects
*   **STUTTER**: Repeats current 16th note at high speed.
*   **FILL**: Triggers all notes marked with the "FILL" play condition.

---

## 7. MIDI & Hardware Integration

### 7.1 MIDI Follow
Incoming MIDI notes are routed to the **active track** in the UI and trigger a low-latency live voice.

### 7.2 MIDI Learning
1.  **Right-Click** any ribbon button.
2.  Select **"MIDI Learn"**.
3.  Move a hardware knob to bind it.

### 7.3 MIDI Implementation Chart
| Message | Support | Mapping / Range |
| :--- | :--- | :--- |
| **Note On/Off** | Full | Mapped to Active Track; 0-127 Range |
| **Velocity** | Full | 1:1 scaling with Engine Gain |
| **CC (Control Change)** | Learning | User-assignable to any Ribbon Parameter |
| **Pitch Bend** | Planned | Targeted for Engine v1.6 |
| **Clock (Sync)** | Internal | Slave/Host sync under development |

---

## 8. Comparison with Hardware 4.1

| Feature | Deluge Hardware | ChucK-Java Emulator |
| :--- | :--- | :--- |
| **Grid** | 128 (8x16) RGB | 128 (8x16) OLED-Style |
| **Knobs** | 4 (Gold/Silver) | Vertical Drag (Simulated) |
| **Polyphony** | Variable | 8 Voices (Strict) |
| **Sampling** | Live Input | File-based `.wav` |

---

## 9. Quick Reference (Popular Commands)

| Category | Interaction | Function |
| :--- | :--- | :--- |
| **Transport** | ▶ PLAY | Toggle Global Playback |
| **Transport** | ■ STOP | Stop & Reset Playhead to Step 0 |
| **Grid** | Left-Click Pad | Toggle Step Note On/Off |
| **Grid** | Vertical Drag Pad | Lock Parameter Value to Step |
| **Grid** | Right-Click Pad | Open Step Editor (Numerical Entry) |
| **Grid** | Shift + Right-Click | Open Note Entry (Pitch Select) |
| **Track** | Click Track Label | Toggle Track Mute (Red = Muted) |
| **Track** | Drag Track Label | Perform Track-wide Parameter Sweeps |
| **Track** | ⚙ Gear Icon | Open Deep Sound Editor Menus |
| **Ribbon** | Left-Click Button | Change Active Parameter Mode |
| **Ribbon** | Right-Click Button | Enter MIDI Learn Mode |
| **Perform** | Hold STUTTER | Trigger High-speed Beat Repeat |
| **Song** | Click Section A-H | Launch Entire Column (Quantized) |

---

*ChucK-Java Deluge Manual — Version 1.8 — April 2026*
