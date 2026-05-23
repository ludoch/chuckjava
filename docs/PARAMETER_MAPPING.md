# Deluge Parameter Mapping Reference Manual

This document acts as the definitive reference manual mapping the physical Yamaha/Deluge hardware parameter page system to our virtual Swing Workstation UI layout. It bridges physical front panel chassis markings with real-time model properties and high-fidelity DSP engine targets.

---

## 1. The Physical Deluge 8-Button × 2-Encoder System

On the physical Deluge, the column of **8 buttons** next to the key pad matrix acts as a **Page Selector** for the two gold encoder knobs (the **Left Gold Knob** and the **Right Gold Knob**). Toggling a button selects between its **Upper Mode** (green LED) and **Lower Mode** (orange/gold LED), routing the two encoders to control two distinct parameters at once:

```
[ AFFECT ENTIRE ] (Global override selector)
   ├── Button 1 ── [ VOLUME (Upper) ]  /  [ PAN (Lower) ]
   ├── Button 2 ── [ TRANSPOSE      ]  /  [ PORTAMENTO  ]
   ├── Button 3 ── [ LFO 1          ]  /  [ LFO 2       ]
   ├── Button 4 ── [ CUTOFF / FM    ]  /  [ HPF / LEVEL ]
   ├── Button 5 ── [ ENV 1          ]  /  [ ENV 2       ]
   ├── Button 6 ── [ MODRATE        ]  /  [ DEPTH       ]
   ├── Button 7 ── [ DELAY          ]  /  [ REVERB       ]
   └── Button 8 ── [ ARP RATE       ]  /  [ GATE        ]
```

---

## 2. Double-Knob Parameter Mapping Grid (16 Logical Pages)

Below is the complete mapping of what the **Left Gold Knob (Knob 1)** and **Right Gold Knob (Knob 2)** control under all 16 hardware select modes across Subtractive Synth, 6-Operator FM (DX7), and Drum Kit track types:

### 🎛️ Button 1: VOLUME & PAN Page
*   **Upper Mode (VOLUME - Green LED):**
    *   **Subtractive / FM Synth:** Left = **`Master Track Volume`** | Right = **`Stereo Pan Position`**
    *   **Drum Kit Mode:** Left = **`Selected Drum Lane Volume`** | Right = **`Selected Drum Lane Pan`**
*   **Lower Mode (PAN - Orange LED):**
    *   **Subtractive / FM Synth:** Left = **`Delay Send Amount`** | Right = **`Reverb Send Amount`**
    *   **Drum Kit Mode:** Left = **`Delay Send Amount`** | Right = **`Reverb Send Amount`**

### 🎛️ Button 2: TRANSPOSE & PORTAMENTO Page
*   **Upper Mode (TRANSPOSE - Green LED):**
    *   **Subtractive / FM Synth:** Left = **`Pitch Transpose (Semitones)`** | Right = **`Fine Tune (Cents)`**
    *   **Drum Kit Mode:** Left = **`Selected Drum Pitch (Semitones)`** | Right = **`Drum Sample Fine Tune`**
*   **Lower Mode (PORTAMENTO - Orange LED):**
    *   **Subtractive / FM Synth:** Left = **`Portamento Glide Time`** | Right = **`Legato Auto-Glide Toggle`**
    *   **Drum Kit Mode:** *Not Applicable*

### 🎛️ Button 3: LFO 1 & LFO 2 Page
*   **Upper Mode (LFO 1 - Green LED):**
    *   **Subtractive Synth:** Left = **`LFO 1 Frequency Speed (Hz)`** | Right = **`LFO 1 Modulation Depth`**
    *   **FM Synth Mode:** Left = **`DX7 Global LFO Speed (Hz)`** | Right = **`DX7 Global LFO Pitch Depth`**
    *   **Drum Kit Mode:** Left = **`Selected Lane LFO 1 Speed`** | Right = **`LFO 1 Depth`**
*   **Lower Mode (LFO 2 - Orange LED):**
    *   **Subtractive Synth:** Left = **`LFO 2 Frequency Speed (Hz)`** | Right = **`LFO 2 Modulation Depth`**
    *   **FM Synth Mode:** Left = **`DX7 Global LFO Speed (Hz)`** | Right = **`DX7 Global LFO Amp/Filter Depth`**
    *   **Drum Kit Mode:** Left = **`Selected Lane LFO 2 Speed`** | Right = **`LFO 2 Depth`**

### 🎛️ Button 4: FILTER & FM Page (Chassis: `CUTOFF / FM` & `RES / FM`)
*   **Upper Mode (CUTOFF / FM - Green LED):**
    *   **Subtractive Synth:** Left = **`Lowpass Filter Cutoff (LPF)`** | Right = **`Lowpass Filter Resonance (Q)`**
    *   **FM Synth Mode:** Left = **`Active Operator Coarse Freq (Ratio)`** | Right = **`Active Operator Fine Freq`**
    *   **Drum Kit Mode:** Left = **`Selected Lane LPF Cutoff`** | Right = **`Selected Lane LPF Resonance`**
*   **Lower Mode (RESONANCE / FM - Orange LED):**
    *   **Subtractive Synth:** Left = **`Highpass Filter Cutoff (HPF)`** | Right = **`Highpass Filter Resonance (Q)`**
    *   **FM Synth Mode:** Left = **`Active Operator Output Level (0-99)`** | Right = **`Active Operator Detune Offset`**
    *   **Drum Kit Mode:** Left = **`Selected Lane HPF Cutoff`** | Right = **`Selected Lane HPF Resonance`**

### 🎛️ Button 5: ENV 1 & ENV 2 Page (Envelopes)
*   **Upper Mode (ENV 1 - Green LED - Amp Envelope):**
    *   **Subtractive Synth:** Left = **`Attack Time`** *(Sustain via Shift)* | Right = **`Decay Time`** *(Release via Shift)*
    *   **FM Synth Mode:** Left = **`Operator EG Rate 1 / Rate 2`** | Right = **`Operator EG Rate 3 / Rate 4`**
    *   **Drum Kit Mode:** Left = **`Drum Amp Attack`** | Right = **`Drum Amp Decay/Release`**
*   **Lower Mode (ENV 2 - Orange LED - Filter/Mod Envelope):**
    *   **Subtractive Synth:** Left = **`Attack Time`** *(Sustain via Shift)* | Right = **`Decay Time`** *(Release via Shift)*
    *   **FM Synth Mode:** Left = **`Operator EG Level 1 / Level 2`** | Right = **`Operator EG Level 3 / Level 4`**
    *   **Drum Kit Mode:** Left = **`Drum Filter Attack`** | Right = **`Drum Filter Decay/Release`**

### 🎛️ Button 6: MOD FX & DEPTH Page (Modulation Effects)
*   **Upper Mode (MODRATE - Green LED):**
    *   **Subtractive / Drum Kit:** Left = **`Mod Chorus/Flanger LFO Rate`** | Right = **`Mod Chorus/Flanger Depth`**
    *   **FM Synth Mode:** Left = **`FM Algorithm Matrix Selection (0-31)`** | Right = **`FM Feedback Level (0-7)`**
*   **Lower Mode (DEPTH - Orange LED):**
    *   **Subtractive / Drum Kit:** Left = **`Mod FX Feedback amount`** | Right = **`Mod FX Type (Chorus/Flange/Phase)`**
    *   **FM Synth Mode:** Left = **`Carrier Phase Initialization`** | Right = **`Operator Enabled Switch`**

### 🎛️ Button 7: DELAY & REVERB Page
*   **Upper Mode (DELAY - Green LED):**
    *   **Subtractive / FM / Kit:** Left = **`Delay Sync Time (e.g. 1/16)`** | Right = **`Delay Feedback amount`**
*   **Lower Mode (REVERB - Orange LED):**
    *   **Subtractive / FM / Kit:** Left = **`Reverb Room Size / Decay Time`** | Right = **`Reverb Highpass Damping`**

### 🎛️ Button 8: ARP RATE & GATE Page
*   **Upper Mode (ARP RATE - Green LED):**
    *   **Subtractive / Drum Kit:** Left = **`Arpeggiator Rate (Step size)`** | Right = **`Arpeggiator Direction Mode`**
    *   **FM Synth Mode:** *Not Applicable*
*   **Lower Mode (GATE - Orange LED):**
    *   **Subtractive / Drum Kit:** Left = **`Arpeggiator Gate Duration`** | Right = **`Arpeggiator Octave Range (1-4)`**
    *   **FM Synth Mode:** *Not Applicable*

---

## 3. Virtual UI 16-Macro Column Mapping Matrix

To simplify computer operation with a standard mouse/keyboard interface, the Deluge Swing Workstation flattens these upper/lower hardware control pages into a **virtual 9th row (the Macro Sliders row)** consisting of **16 interactive button columns** (Index 0 to 15). 

Below is the complete, comprehensive mapping matrix for the 16 virtual macro columns in the Swing UI:

| Column Index | Software Macro Label | Upper/Lower Group | Model Property Target | High-Fidelity DSP/JNI Target | Synth Mode Behavior | FM Mode Behavior | Kit/Drum Mode Behavior |
| :---: | :--- | :---: | :--- | :--- | :--- | :--- | :--- |
| **0** | **LEVEL** | Upper 1 | `track.getVolume()` | `LOCAL_VOLUME` in Q31 | Adjusts track master gain (0.0 to 1.5 multiplier). | Adjusts master FM voice loop master gain. | Adjusts overall summing drum kit voice gain. |
| **1** | **PAN** | Lower 1 | `track.getPan()` | `LOCAL_PAN` in Q31 | Adjusts stereo panning placement (-1.0 left to +1.0 right). | Adjusts stereo panning placement. | Adjusts master stereo panning placement. |
| **2** | **PITCH** | Upper 2 | `st.getTranspose()` | `LOCAL_PITCH_ADJUST` | Shifts note pitches (-24 to +24 semitones). | Shifts fundamental Carrier frequency. | Custom slider: adjusts selected drum lane sample pitch. |
| **3** | **FILTER** | Upper 4 | `st.getLpfFreq()` | `LOCAL_LPF_FREQUENCY` | Adjusts Lowpass Filter Cutoff frequency (20Hz to 20kHz). | Adjusts the active FM Operator's Coarse/Ratio Frequency (0 to 31 ratio). | Adjusts overall drum lane filter cutoff. |
| **4** | **RESONANCE** | Lower 4 | `st.getLpfRes()` | `LOCAL_LPF_RESONANCE` | Adjusts analog lowpass filter resonance Q (0.0 to 1.0). | Adjusts the active FM Operator's Output Volume Level (0 to 99). | Adjusts overall drum lane filter resonance. |
| **5** | **OSC1** | Upper 5 | `st.getOscMix()` | `LOCAL_OSC_A_VOLUME` | Adjusts relative volume mix of Oscillator A (0.0 to 1.0). | Controls FM Carrier feedback depth (0 to 7 level). | Custom: adjusts relative drum lane sample playback start offset. |
| **6** | **OSC2** | Lower 5 | `st.getNoiseVol()` | `LOCAL_NOISE_VOLUME` | Adjusts background white noise generator level (0.0 to 1.0). | Controls individual FM Modulator frequency ratios. | Custom: adjusts relative drum lane sample release envelope. |
| **7** | **LFO** | Upper 3 | `lfo0.rateHz()` | `LFO_SPEED` / `lfo_unit` | Adjusts primary LFO 1 speed frequency (0.0Hz to 20.0Hz). | Adjusts DX7 FM global Pitch/Modulation LFO Speed (0 to 99). | Adjusts drum pitch modulation LFO speed. |
| **8** | **MOD FX** | Upper 6 | `st.getModFxDepth()` | `modFXDepth` in Q31 | Adjusts mod Chorus/Flanger effect depth (0.0 to 1.0). | Adjusts carrier phase offset parameter. | Adjusts mod Chorus/Flanger depth. |
| **9** | **DELAY** | Upper 7 | `st.getDelaySend()` | `delaySend` in Q31 | Adjusts master Delay spatial send amount (0.0 to 1.0). | Adjusts master Delay send amount. | Adjusts individual drum lane delay send amount. |
| **10** | **REVERB** | Lower 7 | `st.getReverbSend()` | `reverbSend` in Q31 | Adjusts master Reverb spatial send amount (0.0 to 1.0). | Adjusts master Reverb send amount. | Adjusts individual drum lane reverb send amount. |
| **11** | **STUTTER** | - | `st.getStutterRate()` | `stutterRate` / `Stutterer` | Adjusts real-time step repeater rate (0.0 to 1.0). | Adjusts real-time step repeater rate. | Adjusts overall drum loop stutter gate. |
| **12** | **PROBABILITY** | - | `step.probability()` | `getStepProbability()` | Adjusts step-event trigger probability (0% to 100%). | Adjusts step-event trigger probability. | Adjusts individual drum step trigger probability. |
| **13** | **GATE** | Lower 8 | `step.gate()` | note duration ticks | Adjusts step note length duration gate multiplier (0.0 to 2.0). | Adjusts FM note duration gate. | Adjusts individual drum lane gate gate. |
| **14** | **VELOCITY** | - | `step.velocity()` | note velocity value | Adjusts note key strike velocity input (0.0 to 1.0). | Adjusts FM note envelope strike velocity. | Adjusts individual drum lane trigger velocity. |
| **15** | **SAMPLE** | - | sample file paths | `AudioFileReader` sample | Enters sample browse/swap menu for custom wav selection. | Enters custom DX7 SysEx patch selection list dialog! | Enters drum kit sample swap/load menu. |
